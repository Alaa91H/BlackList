package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.model.PhoneNumber
import com.blacklist.app.domain.model.Presentation
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Unified blacklist engine: exact / prefix / range / country / international / hidden / unknown.
 * All matching goes through PhoneNumberNormalizer (single source).
 */
class BlacklistEngine(
    private val normalizer: PhoneNumberNormalizer
) {
    fun matches(eventNumber: PhoneNumber, rule: BlacklistRuleEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!rule.isEnabled || !com.blacklist.app.util.ScheduleEvaluator.isRuleActive(rule, nowMillis)) return false
        return when (rule.ruleType) {
            BlacklistRuleEntity.TYPE_EXACT -> {
                if (eventNumber.presentation != Presentation.ALLOWED) return false
                val ruleNum = normalizer.normalize(rule.pattern)
                if (ruleNum.presentation != Presentation.ALLOWED) return false
                normalizer.matches(eventNumber, ruleNum)
            }
            BlacklistRuleEntity.TYPE_PREFIX -> {
                if (eventNumber.presentation != Presentation.ALLOWED) return false
                val prefix = rule.pattern ?: return false
                // Compare normalized digits prefix
                val eventDigits = eventNumber.digitsOnly
                val prefixDigits = prefix.filter { it.isDigit() }
                if (prefixDigits.isEmpty()) return false
                eventDigits.startsWith(prefixDigits) || (eventNumber.e164 != null && eventNumber.e164.startsWith(prefix))
            }
            BlacklistRuleEntity.TYPE_SUFFIX -> {
                if (eventNumber.presentation != Presentation.ALLOWED) return false
                val suffix = rule.pattern ?: return false
                if (suffix.isBlank()) return false
                eventNumber.digitsOnly.endsWith(suffix.filter { it.isDigit() })
            }
            BlacklistRuleEntity.TYPE_CONTAINS -> {
                if (eventNumber.presentation != Presentation.ALLOWED) return false
                val needle = rule.pattern ?: return false
                if (needle.isBlank()) return false
                eventNumber.digitsOnly.contains(needle.filter { it.isDigit() })
            }
            BlacklistRuleEntity.TYPE_RANGE -> {
                if (eventNumber.presentation != Presentation.ALLOWED) return false
                val start = rule.startNumber?.filter { it.isDigit() } ?: return false
                val end = rule.endNumber?.filter { it.isDigit() } ?: return false
                val digits = eventNumber.digitsOnly
                try {
                    // Compare as BigInteger via string length + lexicographic (avoid overflow)
                    if (digits.length != start.length && digits.length != end.length) {
                        // Normalize length: pad or compare numerically
                        val d = digits.toBigIntegerOrNull() ?: return false
                        val s = start.toBigIntegerOrNull() ?: return false
                        val e = end.toBigIntegerOrNull() ?: return false
                        d in s..e
                    } else {
                        digits >= start && digits <= end
                    }
                } catch (_: Exception) { false }
            }
            BlacklistRuleEntity.TYPE_INTERNATIONAL -> normalizer.isInternational(eventNumber)
            BlacklistRuleEntity.TYPE_COUNTRY -> {
                if (eventNumber.presentation != Presentation.ALLOWED) return false
                val iso = rule.countryIso ?: return false
                // Use libphonenumber to check country, fallback to prefix
                try {
                    val util = PhoneNumberUtil.getInstance()
                    val region = eventNumber.countryIso ?: run {
                        val proto = util.parse(eventNumber.normalized, "ZZ")
                        util.getRegionCodeForNumber(proto)
                    }
                    region.equals(iso, ignoreCase = true)
                } catch (_: Exception) {
                    // fallback: compare prefix of country
                    try {
                        val util = PhoneNumberUtil.getInstance()
                        val cc = util.getCountryCodeForRegion(iso)
                        eventNumber.digitsOnly.startsWith(cc.toString())
                    } catch (_: Exception) { false }
                }
            }
            BlacklistRuleEntity.TYPE_HIDDEN -> eventNumber.presentation == Presentation.RESTRICTED
            BlacklistRuleEntity.TYPE_UNKNOWN -> eventNumber.presentation == Presentation.UNKNOWN
            else -> false
        }
    }

    fun findMatching(eventNumber: PhoneNumber, rules: List<BlacklistRuleEntity>, nowMillis: Long = System.currentTimeMillis()): List<BlacklistRuleEntity> {
        return rules.filter { matches(eventNumber, it, nowMillis) }.sortedWith(
            compareBy<BlacklistRuleEntity> { it.priority }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id }
        )
    }

    fun isBlocked(eventNumber: PhoneNumber, rules: List<BlacklistRuleEntity>): Boolean {
        return findMatching(eventNumber, rules).isNotEmpty()
    }

    private fun String.toBigIntegerOrNull() = try { java.math.BigInteger(this) } catch (_: Exception) { null }
}
