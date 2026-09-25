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

    const val MAX_PHONE_DIGITS = 15
    const val MIN_PHONE_DIGITS = 2

    /** Keep an optional leading "+", digits only, capped to E.164 max. */
    fun trimPhoneInput(rawInput: String): String {
        val raw = rawInput.trim()
        val digits = raw.filter { it.isDigit() }.take(MAX_PHONE_DIGITS)
        return if (raw.startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
    }

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
        val raw = trimPhoneInput(rawInput)
        val parsed = if (raw.startsWith("+")) {
            parse(raw, "US")
        } else {
            parse(raw, region)
        }
        return parsed?.takeIf { it.countryIso != "IL" }
    }

    /** International display preview of raw editor input, or null when unparseable. */
    fun previewNumber(rawInput: String, region: String): String? =
        parseForSave(rawInput, region)?.let { "+${it.fullNumber}" }
}
