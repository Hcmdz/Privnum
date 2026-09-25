package com.hcmdz.privnum.data

import java.util.Base64
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.parameter.TelephoneType
import ezvcard.property.Address
import ezvcard.property.Birthday
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import java.time.LocalDate

private const val NICKNAME_PROP = "vnd.android.cursor.item/nickname"

object VcfMapper {

    fun toVcf(contact: Contact): String {
        val vcard = VCard()

        val structuredName = StructuredName()
        structuredName.family = ""
        structuredName.given = contact.name
        if (contact.prefix.isNotBlank()) structuredName.prefixes.add(contact.prefix)
        if (contact.suffix.isNotBlank()) structuredName.suffixes.add(contact.suffix)
        vcard.structuredName = structuredName
        vcard.setFormattedName(contact.displayName())

        if (contact.nickname.isNotBlank()) {
            vcard.addExtendedProperty("X-ANDROID-CUSTOM", "$NICKNAME_PROP;${contact.nickname}")
        }

        val numbers = contact.numbers.normalizePrimary()
        numbers.forEachIndexed { index, number ->
            val tel = Telephone("+${number.full}")
            tel.types.add(TelephoneType.CELL)
            if (index == 0) tel.pref = 1
            vcard.addTelephoneNumber(tel)
        }

        if (contact.email.isNotBlank()) {
            val email = vcard.addEmail(contact.email)
            email.pref = 1
        }
        if (contact.location.isNotBlank()) {
            val address = Address()
            address.locality = contact.location
            address.pref = 1
            vcard.addAddress(address)
        }
        if (contact.appointment.isNotBlank()) vcard.addTitle(contact.appointment)
        if (contact.website.isNotBlank()) vcard.addUrl(contact.website)
        if (contact.notes.isNotBlank()) vcard.addNote(contact.notes)
        if (contact.birthday.isNotBlank()) {
            birthdayOf(contact.birthday)?.let { vcard.setBirthday(it) }
        }
        photoBytes(contact.photo)?.let { (bytes, mime) ->
            vcard.addPhoto(Photo(bytes, imageTypeOf(mime)))
        }

        return Ezvcard.write(vcard).version(VCardVersion.V2_1).go()
    }

    fun contactsToVcf(contacts: List<Contact>): String =
        contacts.joinToString("\r\n\r\n") { toVcf(it) }

    fun parseVcf(vcfContent: String, defaultRegion: String): List<Contact> {
        return try {
            val normalized = normalizePartialBirthdays(vcfContent)
            val rawBirthdays = extractRawBirthdays(vcfContent)
            Ezvcard.parse(normalized).all()
                .mapIndexedNotNull { index, vcard ->
                    toContact(vcard, defaultRegion)?.let { contact ->
                        val raw = rawBirthdays.getOrNull(index).orEmpty()
                        if (isValidBirthday(raw)) contact.copy(birthday = raw) else contact
                    }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val bdayLine = Regex("(?m)^BDAY(?:;[^:]*)?:(.+)$")
    private val fullDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val partialDate = Regex("^--\\d{2}-\\d{2}$")

    private fun isValidBirthday(value: String): Boolean =
        fullDate.matches(value) || partialDate.matches(value)

    private fun extractRawBirthdays(vcfContent: String): List<String> =
        vcfContent.split("BEGIN:VCARD").drop(1).map { block ->
            bdayLine.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }

    private val partialBday = Regex("--(\\d{2})-(\\d{2})")

    private fun normalizePartialBirthdays(vcfContent: String): String =
        vcfContent.lines().joinToString("\n") { line ->
            if (line.substringBefore(":").substringBefore(";").equals("BDAY", ignoreCase = true)) {
                partialBday.replace(line) { "--${it.groupValues[1]}${it.groupValues[2]}" }
            } else line
        }

    private fun toContact(vcard: VCard, defaultRegion: String): Contact? {
        val structuredName = vcard.structuredName
        val name = listOfNotNull(
            structuredName?.given,
            structuredName?.additionalNames?.joinToString(" ")?.ifBlank { null },
            structuredName?.family?.ifBlank { null }
        ).joinToString(" ").ifBlank {
            vcard.formattedName?.value?.substringBefore(",")?.trim().orEmpty()
        }
        val prefix = structuredName?.prefixes?.firstOrNull().orEmpty()
        val suffix = structuredName?.suffixes?.firstOrNull().orEmpty()

        val parsedNumbers = vcard.telephoneNumbers.mapNotNull { tel ->
            PhoneNumberUtils.parseForSave(tel.text, defaultRegion)?.let { parsed ->
                PhoneNumberRef(
                    full = parsed.fullNumber,
                    national = parsed.nationalNumber,
                    country = parsed.countryIso,
                    primary = (tel.pref ?: 0) > 0
                )
            }
        }
        if (parsedNumbers.isEmpty()) return null
        if (name.isBlank()) return null
        val numbers = parsedNumbers.normalizePrimary()

        val nickname = vcard.extendedProperties
            .firstOrNull {
                it.propertyName.equals("X-ANDROID-CUSTOM", ignoreCase = true) &&
                    it.value.startsWith("$NICKNAME_PROP;")
            }?.value?.split(";")?.getOrNull(1).orEmpty()

        val email = vcard.emails.firstOrNull { (it.pref ?: 0) > 0 }
            ?: vcard.emails.firstOrNull()

        val photo = vcard.photos.firstOrNull()?.let { p ->
            val bytes = p.data ?: return@let null
            val mime = when (p.contentType) {
                ImageType.PNG -> "image/png"
                ImageType.GIF -> "image/gif"
                else -> "image/jpeg"
            }
            "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
        }.orEmpty()

        return Contact(
            name = name,
            numbers = numbers,
            appointment = vcard.titles.firstOrNull()?.value.orEmpty(),
            location = vcard.addresses.firstOrNull {
                it.locality != null
            }?.locality.orEmpty(),
            suffix = suffix,
            prefix = prefix,
            email = email?.value.orEmpty(),
            notes = vcard.notes.firstOrNull()?.value.orEmpty(),
            website = vcard.urls.firstOrNull()?.value.orEmpty(),
            birthday = vcard.birthday?.let { birthdayText(it) }.orEmpty(),
            nickname = nickname,
            photo = photo
        )
    }

    private fun birthdayText(birthday: Birthday): String {
        val date = birthday.date
        if (date is LocalDate) {
            return "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
        }
        if (date != null && date.javaClass.name.endsWith("PartialDate")) {
            @Suppress("UNCHECKED_CAST")
            val partial = date as ezvcard.util.PartialDate
            val month = partial.month
            val day = partial.date
            if (month != null && day != null) {
                return "--%02d-%02d".format(month, day)
            }
        }
        return birthday.text.orEmpty()
    }

    private fun birthdayOf(value: String): Birthday? {
        return runCatching {
            if (value.startsWith("--")) {
                val parts = value.removePrefix("--").split("-")
                Birthday(
                    ezvcard.util.PartialDate.Builder()
                        .month(parts[0].toInt())
                        .date(parts[1].toInt())
                        .build()
                )
            } else {
                Birthday(LocalDate.parse(value))
            }
        }.getOrNull()
    }

    private fun imageTypeOf(mime: String): ImageType = when (mime.lowercase()) {
        "image/png" -> ImageType.PNG
        "image/gif" -> ImageType.GIF
        else -> ImageType.JPEG
    }

    private fun photoBytes(photo: String): Pair<ByteArray, String>? {
        if (photo.isBlank()) return null
        return try {
            val data = if (photo.startsWith("data:image/")) {
                photo.substringAfter(",")
            } else photo
            val mime = if (photo.startsWith("data:image/")) {
                photo.substringAfter("data:").substringBefore(";")
            } else "image/jpeg"
            Pair(Base64.getMimeDecoder().decode(data), mime)
        } catch (e: Exception) {
            null
        }
    }
}
