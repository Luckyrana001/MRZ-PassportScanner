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
    @Test
    fun parsesTwoLinePassportMrz() {
        val card = CardConnector.parsePassport(
            listOf(
                "PASGPHAMIDAH<BTE<ARIFFIN<<<<<<<<<<<<<<<<<<<<",
                "K0521119E1SGP7207129F2403122S7225825H<<<<<<26"
            )
        )

        assertNotNull(card)
        assertEquals("BTE", card?.firstName)
        assertEquals("ARIFFIN", card?.middleName)
        assertEquals("HAMIDAH", card?.lastName)
        assertEquals("K0521119E", card?.documentNo)
        assertEquals("12/07/1972", card?.dateOfBirth)
        assertEquals("S7225825H", card?.idNo)
        assertEquals("SGP", card?.nationality)
    }
}
