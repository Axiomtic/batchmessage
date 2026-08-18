package com.local.bulksms.importdata

import org.junit.Assert.assertEquals
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
}
