package com.hcmdz.privnum.data

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

data class ParsedNumber(
    val countryIso: String,
    val nationalNumber: String,
    val fullNumber: String
)

object PhoneNumberUtils {
    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    fun parse(phoneStr: String, defaultRegion: String): ParsedNumber? {
        return try {
            val proto = phoneUtil.parse(phoneStr, defaultRegion)
            val national = proto.nationalNumber.toString()
            val full = "${proto.countryCode}$national"
            val iso = phoneUtil.getRegionCodeForNumber(proto) ?: defaultRegion
            ParsedNumber(
                countryIso = iso,
                nationalNumber = national,
                fullNumber = full
            )
        } catch (e: NumberParseException) {
            null
        }
    }

    fun isValid(phoneStr: String, defaultRegion: String): Boolean {
        return try {
            phoneUtil.isValidNumber(phoneUtil.parse(phoneStr, defaultRegion))
        } catch (e: NumberParseException) {
            false
        }
    }

    fun formatNational(fullNumber: String, region: String): String {
        return try {
            val proto = phoneUtil.parse("+$fullNumber", region)
            phoneUtil.formatInOriginalFormat(proto, region)
        } catch (e: NumberParseException) {
            fullNumber
        }
    }

    /**
     * Parse raw editor input: a leading "+" keeps its country (region ignored),
     * otherwise digits are parsed as a national number in [region].
     */
    fun parseForSave(rawInput: String, region: String): ParsedNumber? {
        val raw = rawInput.trim()
        return if (raw.startsWith("+")) {
            parse("+" + raw.drop(1).filter { it.isDigit() }, "US")
        } else {
            parse(raw.filter { it.isDigit() }, region)
        }
    }

    /** International display preview of raw editor input, or null when unparseable. */
    fun previewNumber(rawInput: String, region: String): String? =
        parseForSave(rawInput, region)?.let { "+${it.fullNumber}" }
}
