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

/**
 * Validates phone numbers using the mobile number segments used by the Chinese
 * carriers (the "SIM providers"). A number is considered available when it matches
 * a live 11-digit mobile segment; the carrier is reported for statistics.
 */
object PhoneNumberChecker {
    private val MOBILE_PATTERN = Regex("^1[3-9]\\d{9}$")

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
        val normalized = normalize(value)
        return when {
            normalized.isEmpty() -> PhoneAvailability.EMPTY
            normalized.matches(MOBILE_PATTERN) -> PhoneAvailability.AVAILABLE
            else -> PhoneAvailability.INVALID
        }
    }

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
