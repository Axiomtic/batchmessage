package com.local.bulksms.importdata

/**
 * Availability of a phone number for bulk sending.
 *
 * - [EMPTY] the cell holds no number at all
 * - [AVAILABLE] a valid mainland-China mobile number (11 digits starting with 1)
 * - [INVALID] something present that cannot be used for sending
 */
enum class PhoneAvailability { AVAILABLE, INVALID, EMPTY }

/** Carrier for a mainland-China mobile number, used for the availability summary. */
enum class Carrier { CHINA_MOBILE, CHINA_UNICOM, CHINA_TELECOM, CHINA_BROADNET, UNKNOWN }

/** A found mobile number and its location inside the original text. */
data class NumberSpan(val start: Int, val end: Int, val number: String)

/**
 * Validates phone numbers using the mobile number segments used by the Chinese
 * carriers (the "SIM providers"). A number is considered available when it matches
 * a live 11-digit mobile segment; the carrier is reported for statistics.
 */
object PhoneNumberChecker {
    private val MOBILE_PATTERN = Regex("^1[3-9]\\d{9}$")

    /** Matches an 11-digit mainland mobile number that is not part of a longer digit run. */
    private val MOBILE_EXTRACT_PATTERN = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")

    /**
     * Matches any phone-like fragment: a digit group (with optional dashes or
     * parentheses between digit blocks, but not whitespace) whose digits alone are
     * 5..20 long. Whitespace separates numbers, so "13800138000 010-12345678" yields
     * two fragments. Used to surface "unavailable" numbers (landlines, malformed
     * entries, foreign numbers) so the visibility filters can act on them too, not
     * only on valid mobiles.
     */
    private val PHONE_LIKE_PATTERN = Regex("(?<![0-9])[0-9][0-9\\-()]{3,19}[0-9](?![0-9])")

    private val CHINA_MOBILE_PREFIXES = setOf(
        "134", "135", "136", "137", "138", "139", "147", "148", "150", "151", "152",
        "157", "158", "159", "165", "172", "173", "174", "178", "182", "183", "184",
        "187", "188", "195", "197", "198",
    )
    private val CHINA_UNICOM_PREFIXES = setOf(
        "130", "131", "132", "145", "146", "155", "156", "166", "167", "171", "175",
        "176", "185", "186", "196",
    )
    private val CHINA_TELECOM_PREFIXES = setOf(
        "133", "149", "153", "162", "177", "180", "181", "189", "190", "191", "193", "199",
    )
    private val CHINA_BROADNET_PREFIXES = setOf("192")

    fun availability(value: String): PhoneAvailability {
        if (extractMobileNumbers(value).isNotEmpty()) return PhoneAvailability.AVAILABLE
        val normalized = normalize(value)
        return when {
            normalized.isEmpty() -> PhoneAvailability.EMPTY
            normalized.matches(MOBILE_PATTERN) -> PhoneAvailability.AVAILABLE
            else -> PhoneAvailability.INVALID
        }
    }

    /**
     * Extracts every valid mainland mobile number from free-form text such as
     * "18912128125（旧） 18912128115（新）". Numbers are de-duplicated and keep their
     * first-seen order.
     */
    fun extractMobileNumbers(value: String): List<String> {
        val result = linkedSetOf<String>()
        for (match in MOBILE_EXTRACT_PATTERN.findAll(value)) {
            val digits = match.value
            if (carrierOf(digits) != Carrier.UNKNOWN) result += digits
        }
        return result.toList()
    }

    /** Returns the extracted numbers together with their span in the original text. */
    fun mobileNumberSpans(value: String): List<NumberSpan> {
        val result = mutableListOf<NumberSpan>()
        val seen = mutableSetOf<String>()
        for (match in MOBILE_EXTRACT_PATTERN.findAll(value)) {
            val digits = match.value
            if (carrierOf(digits) == Carrier.UNKNOWN || !seen.add(digits)) continue
            result += NumberSpan(start = match.range.first, end = match.range.last + 1, number = digits)
        }
        return result
    }

    /**
     * Extracts every phone-like number from free-form text, valid or not: valid
     * mobiles plus landlines / malformed / foreign numbers. This is the extraction
     * used by the preview, the send queue and the table so that the visibility
     * filters govern all three identically.
     */
    fun extractPhoneNumbers(value: String): List<String> {
        val result = linkedSetOf<String>()
        for (match in PHONE_LIKE_PATTERN.findAll(value)) {
            val digits = match.value.filter(Char::isDigit)
            if (digits.length < 5 || digits.length > 20) continue
            result += digits
        }
        return result.toList()
    }

    /**
     * The visibility rule shared by the table, the preview and the send queue:
     * a number is shown/sent when its category (available / unavailable) is on.
     */
    fun isVisible(number: String, showAvailable: Boolean, showUnavailable: Boolean): Boolean {
        val isAvailable = availability(number) == PhoneAvailability.AVAILABLE
        return (isAvailable && showAvailable) || (!isAvailable && showUnavailable)
    }

    /** All numbers in [value] whose category is currently visible. */
    fun visibleNumbers(value: String, showAvailable: Boolean, showUnavailable: Boolean): List<String> =
        extractPhoneNumbers(value).filter { isVisible(it, showAvailable, showUnavailable) }

    fun carrierOf(value: String): Carrier {
        val normalized = normalize(value)
        if (!normalized.matches(MOBILE_PATTERN)) return Carrier.UNKNOWN
        val prefix = normalized.take(3)
        return when {
            prefix in CHINA_MOBILE_PREFIXES -> Carrier.CHINA_MOBILE
            prefix in CHINA_UNICOM_PREFIXES -> Carrier.CHINA_UNICOM
            prefix in CHINA_TELECOM_PREFIXES -> Carrier.CHINA_TELECOM
            prefix in CHINA_BROADNET_PREFIXES -> Carrier.CHINA_BROADNET
            else -> Carrier.UNKNOWN
        }
    }

    /** Strips spacing, separators and a leading +86 / 86 country code. */
    fun normalize(value: String): String {
        val compact = value.trim()
            .filterNot { character ->
                character.isWhitespace() || character == '-' || character == '(' || character == ')'
            }
        return when {
            compact.startsWith("+86") -> compact.removePrefix("+86")
            compact.startsWith("86") && compact.length > 11 -> compact.removePrefix("86")
            else -> compact
        }
    }

    fun isBlank(value: String): Boolean = normalize(value).isEmpty()
}
