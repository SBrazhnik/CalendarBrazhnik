package app.calstats

import app.calstats.work.DocScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocScanTest {
    @Test fun passportText() {
        val f = DocScan.parse("ПАСПОРТ\nВыдан 12.05.2015 ГУ МВД\n45 12 456789\nДата рождения 01.02.1990")
        assertTrue(f.numbers.contains("45 12 456789"))
        assertEquals(LocalDate.of(2015, 5, 12), f.issued(LocalDate.of(2026, 11, 22)))
    }

    @Test fun osago() {
        val f = DocScan.parse("Полис ХХХ0123456789 срок с 01.12.2025 по 30.11.2026")
        assertEquals(LocalDate.of(2026, 11, 30), f.expires(LocalDate.of(2026, 11, 22)))
    }

    @Test fun receiptQr() {
        val r = DocScan.receipt("t=20261122T1530&s=12990.00&fn=9999078900012345&i=12345&fp=1234567890&n=1")!!
        assertEquals(1_299_000L, r.sum)
        assertEquals(22, r.time.dayOfMonth)
        assertNull(DocScan.receipt("https://example.com"))
    }
}
