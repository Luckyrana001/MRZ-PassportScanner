package ocr

import ocr.utils.CheckSum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MrzReaderTest {
    private val row1 = "IDKYA7029804442<<3631<<<<<4715"
    private val row2 = "9609266M2106308<B034963670R<<6"
    private val row3 = "AMOS<KIPKOECH<KORIR<<<<<<<<<<<"
    private val mrz = listOf(row1, row2, row3).joinToString("\n")

    @Test
    fun detectsAndParsesMrz1() {
        val reader = MrzReader()
        val type = reader.getCardType(mrz)
        assertEquals(CardType.MRZType.MRZ1, type)
        assertTrue(reader.isValidCard(type, mrz))
        val card = reader.readDocument(type, mrz)
        assertEquals("AMOS", card.firstName)
        assertEquals("KIPKOECH", card.secondName)
        assertEquals("KORIR", card.lastName)
        assertEquals("KYA", card.country)
        assertEquals("960926", card.dateOfBirth)
        assertEquals("210630", card.dateOfExpiry)
    }

    @Test
    fun validatesIcaoCheckDigits() {
        assertTrue(CheckSum.check("960926", "6"))
        assertFalse(CheckSum.check("960926", "7"))
    }
}
