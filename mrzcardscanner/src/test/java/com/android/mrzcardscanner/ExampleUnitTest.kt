package com.android.mrzcardscanner

import com.android.mrzcardreader.cardconnectors.CardConnector
import org.junit.Test
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    private val singaporeRowTwo = "K0521119E1SGP7207129F2403122S7225825H<<<<<<26"

    @Test
    fun parsesTwoLinePassportMrz() {
        val card = CardConnector.parsePassport(
            listOf(
                "PASGPHAMIDAH<BTE<ARIFFIN<<<<<<<<<<<<<<<<<<<<",
                singaporeRowTwo
            )
        )

        assertNotNull(card)
        assertEquals("ARIFFIN", card?.firstName)
        assertEquals("BTE", card?.middleName)
        assertEquals("HAMIDAH", card?.lastName)
        assertEquals("K0521119E", card?.documentNo)
        assertEquals("12/07/1972", card?.dateOfBirth)
        assertEquals("12/03/2024", card?.dateOfExpiry)
        assertEquals("S7225825H", card?.idNo)
        assertEquals("SGP", card?.nationality)
    }

    @Test
    fun removesIntermittentKCharactersFromTheFillerArea() {
        val correct = "P<SGPHAMIDAH<<BTE<ARIFFIN".padEnd(44, '<')
        val singleK = correct.replace("ARIFFIN<<<", "ARIFFIN<K<")
        val doubleK = correct.replace("ARIFFIN<<<", "ARIFFIN<KK")

        val card = CardConnector.parsePassport(
            listOf(correct, singleK, correct, doubleK, correct, singaporeRowTwo)
        )

        assertNotNull(card)
        assertEquals("ARIFFIN", card?.firstName)
        assertEquals("BTE", card?.middleName)
        assertEquals("HAMIDAH", card?.lastName)
    }

    @Test
    fun removesPersistentTrailingKWhenPrintedNameConfirmsTheCorrection() {
        val incorrect = "P<SGPHAMIDAH<<BTE<ARIFFINK".padEnd(44, '<')
        val card = CardConnector.parsePassport(
            listOf(incorrect, incorrect, incorrect, "ARIFFIN", singaporeRowTwo)
        )

        assertNotNull(card)
        assertEquals("ARIFFIN", card?.firstName)
        assertEquals("BTE", card?.middleName)
        assertEquals("HAMIDAH", card?.lastName)
    }

    @Test
    fun preservesLegitimateNameEndingInK() {
        val rowOne = "P<SGPSMITH<<ASHOK".padEnd(44, '<')
        val card = CardConnector.parsePassport(listOf(rowOne, "ASHOK", singaporeRowTwo))

        assertNotNull(card)
        assertEquals("ASHOK", card?.firstName)
    }

    @Test
    fun parsesChinesePassportWithFullyPopulatedOptionalData() {
        val card = CardConnector.parsePassport(
            listOf(
                "POCHNZARAM<<STEVEN<<LEE<<<<<<<<<<<<<<<<<<",
                "E746438885CHN9012017M2605030LMNGNAKBMANHA914",
            )
        )

        assertNotNull(card)
        assertEquals("STEVEN", card?.firstName)
        assertEquals("LEE", card?.middleName)
        assertEquals("ZARAM", card?.lastName)
        assertEquals("E74643888", card?.documentNo)
        assertEquals("01/12/1990", card?.dateOfBirth)
        assertEquals("03/05/2026", card?.dateOfExpiry)
        assertEquals("CHN", card?.nationality)
    }

    @Test
    fun readsPlaceOfIssueFromThePassportVisualZone() {
        val card = CardConnector.parsePassport(
            listOf(
                "P<INDSANKARAN<<NITHYA".padEnd(44, '<'),
                "Place of Issue",
                "CHENNAI",
                singaporeRowTwo,
            )
        )

        assertNotNull(card)
        assertEquals("CHENNAI", card?.placeOfIssue)
    }

    @Test
    fun parsesGermanPassportWithFillerPaddedCountryCode() {
        val card = CardConnector.parsePassport(
            listOf(
                "P<D<<KOLDEWEY<<CONSTANTIN".padEnd(44, '<'),
                "C3LXV7G234D<<0310136M28062842101<<<<<<<<<<46",
            )
        )

        assertNotNull(card)
        assertEquals("CONSTANTIN", card?.firstName)
        assertEquals("KOLDEWEY", card?.lastName)
        assertEquals("C3LXV7G23", card?.documentNo)
        assertEquals("13/10/2003", card?.dateOfBirth)
        assertEquals("28/06/2028", card?.dateOfExpiry)
        assertEquals("D", card?.nationality)
    }

    @Test
    fun readsVisualXWhenIcelandMrzGenderIsUnspecified() {
        val card = CardConnector.parsePassport(
            listOf(
                "PAISLAEVARSDOTTIR<<THURIDUR<OESP".padEnd(44, '<'),
                "A3536444<7ISL1212123<3103108121212<1239<<<68",
                "Kyn / Sex X",
            )
        )

        assertNotNull(card)
        assertEquals("X", card?.gender)
        assertEquals("THURIDUR", card?.firstName)
        assertEquals("OESP", card?.middleName)
        assertEquals("AEVARSDOTTIR", card?.lastName)
    }

    @Test
    fun rejectsPassportDataWhenAllPrimaryCheckDigitsAreInvalid() {
        val invalidRowTwo = singaporeRowTwo
            .replaceRange(9, 10, "0")
            .replaceRange(19, 20, "8")
            .replaceRange(27, 28, "3")
        val card = CardConnector.parsePassport(
            listOf("PASGPHAMIDAH<BTE<ARIFFIN<<<<<<<<<<<<<<<<<<<<", invalidRowTwo)
        )

        assertNull(card)
    }
}
