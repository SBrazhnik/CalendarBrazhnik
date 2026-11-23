package app.calstats

import app.calstats.work.CardsKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsTest {
    @Test fun ean() {
        assertTrue(CardsKit.ean13ok("4607001771234".let { it.dropLast(1) + checkDigit(it.dropLast(1)) }))
        assertEquals("CODE_128", CardsKit.guess("ABC123"))
    }

    private fun checkDigit(s: String): Char {
        val sum = s.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return '0' + (10 - sum % 10) % 10
    }

    @Test fun pkpass() {
        val json = """{"organizationName":"Пятёрочка","description":"Выручай-карта","backgroundColor":"rgb(227, 30, 36)",
            "barcodes":[{"format":"PKBarcodeFormatQR","message":"7000123456789"}]}"""
        val c = CardsKit.fromPassJson(json, "1")!!
        assertEquals("Пятёрочка", c.store)
        assertEquals("7000123456789", c.number)
        assertEquals("QR_CODE", c.format)
        assertEquals(0xFFE31E24.toInt(), c.color)
    }

    @Test fun sharedText() {
        assertEquals("2775001234567", CardsKit.numberFromText("Моя карта Лента: 2775 0012 3456 7"))
    }
}
