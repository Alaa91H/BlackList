package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import java.math.BigInteger

/**
 * Read-only analysis for a candidate persistent blacklist rule.
 *
 * The analyzer deliberately reports only overlaps that can be proven from two
 * rules alone. It never reads storage, queries Telecom, or changes a policy.
 * This lets the editor describe a draft rule before the user chooses to save it.
 */
class BlacklistRuleConflictAnalyzer(
    private val normalizer: PhoneNumberNormalizer
) {
    fun analyze(
        draft: BlacklistRuleEntity,
        existingRules: List<BlacklistRuleEntity>,
        maxExistingRules: Int = Int.MAX_VALUE
    ): RuleConflictPreview {
        if (!isPersistent(draft)) return RuleConflictPreview()

        val activeRuleCount = existingRules.count(::isPersistent)
        val inspectedRules = existingRules.asSequence()
            .filter(::isPersistent)
            .take(maxExistingRules.coerceAtLeast(0))
            .toList()
        val conflicts = inspectedRules.asSequence()
            .filter { existing ->
                sameScope(draft, existing) || overlaps(draft, existing)
            }
            .map { existing ->
                RuleConflict(
                    existingRule = existing,
                    kind = if (sameScope(draft, existing)) {
                        RuleConflictKind.DUPLICATE
                    } else {
                        RuleConflictKind.OVERLAP
                    },
                    winner = winner(draft, existing)
                )
            }
            .sortedWith(
                compareBy<RuleConflict> { it.existingRule.priority }
                    .thenByDescending { it.existingRule.createdAt }
                    .thenByDescending { it.existingRule.id }
            )
            .toList()

        return RuleConflictPreview(
            conflicts = conflicts,
            inspectedRuleCount = inspectedRules.size,
            activeRuleCount = activeRuleCount
        )
    }

    /** True when two persistent rules match exactly the same scope. */
    fun sameScope(first: BlacklistRuleEntity, second: BlacklistRuleEntity): Boolean {
        if (!isPersistent(first) || !isPersistent(second) || first.ruleType != second.ruleType) return false
        return when (first.ruleType) {
            BlacklistRuleEntity.TYPE_EXACT -> {
                val firstNumber = normalizedExact(first)
                val secondNumber = normalizedExact(second)
                firstNumber != null && secondNumber != null && normalizer.matches(firstNumber, secondNumber)
            }
            BlacklistRuleEntity.TYPE_PREFIX,
            BlacklistRuleEntity.TYPE_SUFFIX,
            BlacklistRuleEntity.TYPE_CONTAINS -> digits(first.pattern) == digits(second.pattern)
            BlacklistRuleEntity.TYPE_RANGE ->
                digits(first.startNumber) == digits(second.startNumber) &&
                    digits(first.endNumber) == digits(second.endNumber)
            BlacklistRuleEntity.TYPE_COUNTRY -> first.countryIso.equals(second.countryIso, ignoreCase = true)
            else -> false
        }
    }

    private fun overlaps(first: BlacklistRuleEntity, second: BlacklistRuleEntity): Boolean {
        val firstExact = normalizedExact(first)
        val secondExact = normalizedExact(second)

        if (firstExact != null && secondExact != null) return normalizer.matches(firstExact, secondExact)
        if (firstExact != null) return exactOverlaps(firstExact.digitsOnly, second)
        if (secondExact != null) return exactOverlaps(secondExact.digitsOnly, first)

        if (first.ruleType == BlacklistRuleEntity.TYPE_PREFIX && second.ruleType == BlacklistRuleEntity.TYPE_PREFIX) {
            val a = digits(first.pattern)
            val b = digits(second.pattern)
            return a.isNotEmpty() && b.isNotEmpty() && (a.startsWith(b) || b.startsWith(a))
        }
        if (first.ruleType == BlacklistRuleEntity.TYPE_SUFFIX && second.ruleType == BlacklistRuleEntity.TYPE_SUFFIX) {
            val a = digits(first.pattern)
            val b = digits(second.pattern)
            return a.isNotEmpty() && b.isNotEmpty() && (a.endsWith(b) || b.endsWith(a))
        }
        if (first.ruleType == BlacklistRuleEntity.TYPE_CONTAINS && second.ruleType == BlacklistRuleEntity.TYPE_CONTAINS) {
            val a = digits(first.pattern)
            val b = digits(second.pattern)
            return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
        }
        if (first.ruleType == BlacklistRuleEntity.TYPE_RANGE && second.ruleType == BlacklistRuleEntity.TYPE_RANGE) {
            val firstRange = range(first) ?: return false
            val secondRange = range(second) ?: return false
            return firstRange.first <= secondRange.second && secondRange.first <= firstRange.second
        }
        return first.ruleType == BlacklistRuleEntity.TYPE_COUNTRY &&
            second.ruleType == BlacklistRuleEntity.TYPE_COUNTRY &&
            first.countryIso.equals(second.countryIso, ignoreCase = true)
    }

    private fun exactOverlaps(exactDigits: String, rule: BlacklistRuleEntity): Boolean = when (rule.ruleType) {
        BlacklistRuleEntity.TYPE_PREFIX -> {
            val prefix = digits(rule.pattern)
            prefix.isNotEmpty() && exactDigits.startsWith(prefix)
        }
        BlacklistRuleEntity.TYPE_SUFFIX -> {
            val suffix = digits(rule.pattern)
            suffix.isNotEmpty() && exactDigits.endsWith(suffix)
        }
        BlacklistRuleEntity.TYPE_CONTAINS -> {
            val needle = digits(rule.pattern)
            needle.isNotEmpty() && exactDigits.contains(needle)
        }
        BlacklistRuleEntity.TYPE_RANGE -> range(rule)?.let { bounds ->
            val exact = exactDigits.toBigIntegerOrNull()
            exact != null && exact in bounds.first..bounds.second
        } ?: false
        BlacklistRuleEntity.TYPE_COUNTRY -> {
            val country = rule.countryIso ?: return false
            normalizer.normalize(exactDigits).countryIso.equals(country, ignoreCase = true)
        }
        else -> false
    }

    private fun winner(draft: BlacklistRuleEntity, existing: BlacklistRuleEntity): RuleConflictWinner {
        if (draft.priority != existing.priority) {
            return if (draft.priority < existing.priority) RuleConflictWinner.DRAFT else RuleConflictWinner.EXISTING
        }
        if (draft.createdAt != existing.createdAt) {
            return if (draft.createdAt > existing.createdAt) RuleConflictWinner.DRAFT else RuleConflictWinner.EXISTING
        }
        // A newly inserted Room row receives a positive id after all current rows.
        return RuleConflictWinner.DRAFT
    }

    private fun normalizedExact(rule: BlacklistRuleEntity) =
        if (rule.ruleType == BlacklistRuleEntity.TYPE_EXACT) normalizer.normalize(rule.pattern) else null

    private fun range(rule: BlacklistRuleEntity): Pair<BigInteger, BigInteger>? {
        val start = digits(rule.startNumber).toBigIntegerOrNull() ?: return null
        val end = digits(rule.endNumber).toBigIntegerOrNull() ?: return null
        return if (start <= end) start to end else null
    }

    private fun digits(value: String?): String = value.orEmpty().filter(Char::isDigit)

    private fun String.toBigIntegerOrNull(): BigInteger? = try {
        BigInteger(this)
    } catch (_: NumberFormatException) {
        null
    }

    private fun isPersistent(rule: BlacklistRuleEntity): Boolean =
        rule.isEnabled && !TemporaryFirewall.isTempType(rule.ruleType)
}

data class RuleConflictPreview(
    val conflicts: List<RuleConflict> = emptyList(),
    val inspectedRuleCount: Int = 0,
    val activeRuleCount: Int = 0
) {
    val hasDuplicate: Boolean get() = conflicts.any { it.kind == RuleConflictKind.DUPLICATE }
    val isTruncated: Boolean get() = inspectedRuleCount < activeRuleCount
}

data class RuleConflict(
    val existingRule: BlacklistRuleEntity,
    val kind: RuleConflictKind,
    val winner: RuleConflictWinner
)

enum class RuleConflictKind { DUPLICATE, OVERLAP }

enum class RuleConflictWinner { DRAFT, EXISTING }
