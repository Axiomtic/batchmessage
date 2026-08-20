package com.local.bulksms.importdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberCheckerTest {

    @Test
    fun validChineseMobilesAreAvailable() {
        assertEquals(PhoneAvailability.AVAILABLE, PhoneNumberChecker.availability("13800138000"))
        assertEquals(PhoneAvailability.AVAILABLE, PhoneNumberChecker.availability("19912345678"))
        assertEquals(PhoneAvailability.AVAILABLE, PhoneNumberChecker.availability("19212345678"))
    }

    @Test
    fun formattedNumbersAreNormalizedBeforeValidation() {
        assertEquals(PhoneAvailability.AVAILABLE, PhoneNumberChecker.availability(" 138-0013-8000 "))
        assertEquals(PhoneAvailability.AVAILABLE, PhoneNumberChecker.availability("+8613800138000"))
        assertEquals(PhoneAvailability.AVAILABLE, PhoneNumberChecker.availability("86 138 0013 8000"))
    }

    @Test
    fun invalidAndEmptyValuesAreClassified() {
        assertEquals(PhoneAvailability.INVALID, PhoneNumberChecker.availability("1234567"))
        assertEquals(PhoneAvailability.INVALID, PhoneNumberChecker.availability("23800138000"))
        assertEquals(PhoneAvailability.INVALID, PhoneNumberChecker.availability("张三"))
        assertEquals(PhoneAvailability.INVALID, PhoneNumberChecker.availability("+14155552671"))
        assertEquals(PhoneAvailability.EMPTY, PhoneNumberChecker.availability(""))
        assertEquals(PhoneAvailability.EMPTY, PhoneNumberChecker.availability("   "))
    }

    @Test
    fun carriersAreDetectedFromMobileSegments() {
        assertEquals(Carrier.CHINA_MOBILE, PhoneNumberChecker.carrierOf("13800138000"))
        assertEquals(Carrier.CHINA_UNICOM, PhoneNumberChecker.carrierOf("15500138000"))
        assertEquals(Carrier.CHINA_TELECOM, PhoneNumberChecker.carrierOf("18900138000"))
        assertEquals(Carrier.CHINA_BROADNET, PhoneNumberChecker.carrierOf("19200138000"))
        assertEquals(Carrier.UNKNOWN, PhoneNumberChecker.carrierOf("not-a-number"))
        assertEquals(Carrier.UNKNOWN, PhoneNumberChecker.carrierOf(""))
    }

    @Test
    fun isBlankRecognizesEmptyNormalizedValues() {
        assertEquals(true, PhoneNumberChecker.isBlank(""))
        assertEquals(true, PhoneNumberChecker.isBlank("  -  "))
        assertEquals(false, PhoneNumberChecker.isBlank("13800138000"))
    }

    @Test
    fun extractsMultipleNumbersFromFreeFormText() {
        assertEquals(
            listOf("18912128125", "18912128115"),
            PhoneNumberChecker.extractMobileNumbers("18912128125（旧） 18912128115（新）"),
        )
    }

    @Test
    fun extractionDeduplicatesAndSkipsNonMobileDigits() {
        assertEquals(
            listOf("13800138000"),
            PhoneNumberChecker.extractMobileNumbers("13800138000 / 13800138000 / 张三 / 12345"),
        )
        // A 12-digit run must not yield an 11-digit prefix.
        assertEquals(
            emptyList<String>(),
            PhoneNumberChecker.extractMobileNumbers("189121281251"),
        )
    }

    @Test
    fun availabilityTreatsEmbeddedNumbersAsAvailable() {
        assertEquals(
            PhoneAvailability.AVAILABLE,
            PhoneNumberChecker.availability("18912128125（旧） 18912128115（新）"),
        )
    }

    @Test
    fun extractsPhoneLikeNumbersIncludingUnavailableOnes() {
        assertEquals(
            listOf("18912128125", "18912128115"),
            PhoneNumberChecker.extractPhoneNumbers("18912128125（旧） 18912128115（新）"),
        )
        // Landlines, malformed entries and foreign numbers are surfaced too so the
        // unavailable filter can act on them.
        assertEquals(
            listOf("01012345678"),
            PhoneNumberChecker.extractPhoneNumbers("010-12345678"),
        )
        assertEquals(
            listOf("12345678901"),
            PhoneNumberChecker.extractPhoneNumbers("12345678901"),
        )
        // A 12-digit run is one unavailable number, not a valid-mobile prefix.
        assertEquals(
            listOf("189121281251"),
            PhoneNumberChecker.extractPhoneNumbers("189121281251"),
        )
        // Short non-phone digit runs are ignored.
        assertEquals(emptyList<String>(), PhoneNumberChecker.extractPhoneNumbers("张三 12"))
    }

    @Test
    fun visibilityRuleMatchesAvailabilityCategories() {
        assertTrue(PhoneNumberChecker.isVisible("13800138000", true, false))
        assertFalse(PhoneNumberChecker.isVisible("13800138000", false, true))
        assertTrue(PhoneNumberChecker.isVisible("01012345678", false, true))
        assertFalse(PhoneNumberChecker.isVisible("01012345678", true, false))
        assertTrue(PhoneNumberChecker.isVisible("13800138000", true, true))
        assertTrue(PhoneNumberChecker.isVisible("01012345678", true, true))
    }

    @Test
    fun visibleNumbersFollowsTheToggles() {
        val text = "13800138000 010-12345678"
        assertEquals(
            listOf("13800138000"),
            PhoneNumberChecker.visibleNumbers(text, true, false),
        )
        assertEquals(
            listOf("01012345678"),
            PhoneNumberChecker.visibleNumbers(text, false, true),
        )
        assertEquals(
            listOf("13800138000", "01012345678"),
            PhoneNumberChecker.visibleNumbers(text, true, true),
        )
        assertEquals(emptyList<String>(), PhoneNumberChecker.visibleNumbers(text, false, false))
    }
}
