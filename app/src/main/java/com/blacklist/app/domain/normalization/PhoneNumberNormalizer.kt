package com.blacklist.app.domain.normalization

import com.blacklist.app.domain.model.PhoneNumber
import com.blacklist.app.domain.model.Presentation
import com.blacklist.app.util.PhoneNumberUtils
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.ShortNumberInfo

/**
 * Single source of truth for number normalization.
 * Uses libphonenumber when possible, falls back to PhoneNumberUtils for offline/local formats.
 * No duplicate normalization logic anywhere else.
 */
class PhoneNumberNormalizer(
    private val defaultRegion: String = "DE"
) {
    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val shortNumberInfo: ShortNumberInfo by lazy { ShortNumberInfo.getInstance() }

    fun normalize(raw: String?, presentation: Int? = null): PhoneNumber {
        if (raw.isNullOrBlank() || PhoneNumberUtils.isPrivateOrHidden(raw)) {
            val pres = when {
                raw.isNullOrBlank() -> Presentation.UNKNOWN
                else -> Presentation.RESTRICTED
            }
            return PhoneNumber(
                raw = raw ?: "",
                normalized = raw?.trim() ?: "",
                e164 = null,
                countryIso = null,
                nationalNumber = null,
                presentation = pres
            )
        }

        // Try libphonenumber
        var e164: String? = null
        var country: String? = null
        var national: String? = null
        var normalized: String? = null

        try {
            // Handle 00 prefix -> +
            var toParse = raw.trim()
            if (toParse.startsWith("00")) toParse = "+" + toParse.substring(2)
            val proto = phoneUtil.parse(toParse, defaultRegion)
            if (phoneUtil.isPossibleNumber(proto)) {
                e164 = phoneUtil.format(proto, PhoneNumberUtil.PhoneNumberFormat.E164)
                country = phoneUtil.getRegionCodeForNumber(proto)
                national = proto.nationalNumber.toString()
                normalized = e164
            }
        } catch (_: NumberParseException) {
            // fallback
        }

        if (normalized == null) {
            normalized = PhoneNumberUtils.normalize(raw) ?: raw.trim()
        }

        return PhoneNumber(
            raw = raw,
            normalized = normalized,
            e164 = e164,
            countryIso = country,
            nationalNumber = national,
            presentation = Presentation.ALLOWED
        )
    }

    /**
     * Keeps emergency short codes outside all user-defined blocking rules.
     * The number's parsed region is preferred; the device locale region is used
     * as a safe fallback for short codes that cannot be formatted as E.164.
     */
    fun isEmergencyNumber(number: PhoneNumber): Boolean {
        val raw = number.raw.filter(Char::isDigit)
        if (raw.isBlank()) return false
        val regions = linkedSetOf<String>().apply {
            number.countryIso?.uppercase()?.takeIf { it.length == 2 }?.let(::add)
            defaultRegion.uppercase().takeIf { it.length == 2 }?.let(::add)
        }
        return regions.any { region -> shortNumberInfo.isEmergencyNumber(raw, region) }
    }

    fun matches(a: PhoneNumber, b: PhoneNumber): Boolean {
        if (a.presentation != Presentation.ALLOWED || b.presentation != Presentation.ALLOWED) return false
        if (a.normalized == b.normalized) return true
        if (a.e164 != null && b.e164 != null && a.e164 == b.e164) return true
        // fallback to suffix 9-digit (existing behavior)
        return PhoneNumberUtils.matches(a.normalized, b.normalized)
    }

    fun isSameNumber(a: String?, b: String?): Boolean {
        val pa = normalize(a)
        val pb = normalize(b)
        if (pa.presentation != Presentation.ALLOWED || pb.presentation != Presentation.ALLOWED) return false
        return matches(pa, pb)
    }
}
