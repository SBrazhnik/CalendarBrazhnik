package app.calstats

import app.calstats.ui.tileRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellTest {
    @Test fun tilePacking() {
        val rows = tileRows(
            listOf(
                Tab.CALENDAR to TileSize.LARGE,
                Tab.TASKS to TileSize.MEDIUM, Tab.FINANCE to TileSize.MEDIUM,
                Tab.HABITS to TileSize.SMALL, Tab.HEALTH to TileSize.SMALL, Tab.DOCS to TileSize.SMALL,
                Tab.NOTES to TileSize.MEDIUM, Tab.WISHES to TileSize.LARGE, Tab.ANALYSIS to TileSize.SMALL,
            ),
        ).map { r -> r.map { it.first } }
        assertEquals(
            listOf(
                listOf(Tab.CALENDAR), listOf(Tab.TASKS, Tab.FINANCE), listOf(Tab.HABITS, Tab.HEALTH, Tab.DOCS),
                listOf(Tab.NOTES), listOf(Tab.WISHES), listOf(Tab.ANALYSIS),
            ),
            rows,
        )
    }

    @Test fun telegramCommands() {
        assertEquals(TelegramBot.Command(TelegramBot.Kind.TASK, "Позвонить в банк"), TelegramBot.parseCommand("Задача: Позвонить в банк"))
        assertEquals(TelegramBot.Command(TelegramBot.Kind.EXPENSE, "кофе", 35050), TelegramBot.parseCommand("трата 350,50 ₽ кофе"))
        assertEquals(TelegramBot.Kind.NOTE, TelegramBot.parseCommand("https://ozon.ru/item").kind)
        assertEquals(TelegramBot.Kind.NOTE, TelegramBot.parseCommand("трата без суммы").kind)
    }

    @Test fun releaseParse() {
        val json = """{"tag_name":"build-35","body":"x","assets":[{"name":"Napravleniya-35.apk","size":12000000,
            "browser_download_url":"https://github.com/a/b/releases/download/build-35/Napravleniya-35.apk"}]}"""
        val r = Updater.parse(json)!!
        assertEquals(35L, r.version)
        assertEquals(12000000L, r.size)
        assertNull(Updater.parse("""{"tag_name":"build-1","assets":[]}"""))
    }
}
