package app.teleprompter.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScenarioStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { ScenarioStore(File(folder.root, "scenarios")) }

    @Test
    fun savesEveryScenarioAsItsOwnRecord() {
        val a = Scenario(title = "Первый", text = "раз", updatedAt = 1)
        val b = Scenario(
            title = "Второй",
            cues = listOf(Cue(1.0, 2.5, "реплика")),
            fileName = "ролик.srt",
            useTimecodes = true,
            updatedAt = 2,
        )
        store.save(a)
        store.save(b)
        assertEquals(listOf(b, a), store.list())
        assertEquals(2, File(folder.root, "scenarios").listFiles()!!.size)
    }

    @Test
    fun savingAgainReplacesTheSameRecord() {
        val a = Scenario(title = "Старое", updatedAt = 1)
        store.save(a)
        store.save(a.copy(title = "Новое", updatedAt = 2))
        assertEquals(listOf("Новое"), store.list().map { it.title })
        assertFalse(File(folder.root, "scenarios/${a.id}.tmp").exists())
    }

    @Test
    fun deleteRemovesOnlyThatRecord() {
        val a = Scenario(title = "Первый")
        val b = Scenario(title = "Второй")
        store.save(a)
        store.save(b)
        store.delete(a.id)
        assertEquals(listOf(b), store.list())
    }

    @Test
    fun damagedRecordDoesNotHideTheOthers() {
        val a = Scenario(title = "Целый")
        store.save(a)
        File(folder.root, "scenarios/broken.json").writeText("{не json")
        assertEquals(listOf(a), store.list())
    }

    @Test
    fun emptyStoreListsNothing() {
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun subtitleFileBecomesTimedScenario() {
        val s = scenarioFromFile("Мой ролик.srt", "1\n00:00:01,000 --> 00:00:02,000\nПривет\n")
        assertEquals("Мой ролик", s.title)
        assertEquals(listOf("Привет"), s.blocks())
        assertTrue(s.useTimecodes)
    }

    @Test
    fun textFileBecomesPlainScenario() {
        val s = scenarioFromFile("заметки.txt", "Первая строка\n\nВторая строка\n")
        assertEquals("заметки", s.title)
        assertEquals(listOf("Первая строка", "Вторая строка"), s.blocks())
        assertEquals(4, s.wordCount())
        assertFalse(s.useTimecodes)
    }

    @Test
    fun searchLooksInTitleAndText() {
        val s = Scenario(title = "Реклама", text = "Скидки на кофе")
        assertTrue(s.matches(""))
        assertTrue(s.matches("рекл"))
        assertTrue(s.matches(" КОФЕ "))
        assertFalse(s.matches("чай"))
    }

    @Test
    fun readingTimeComesFromSubtitlesOrSpeed() {
        assertEquals(60L, Scenario(text = "один два три четыре пять").readingSeconds(5))
        val timed = Scenario(cues = listOf(Cue(5.0, 7.0, "а"), Cue(8.0, 95.0, "б")))
        assertEquals(90L, timed.readingSeconds(130))
    }

    @Test
    fun blankTitleStillHasAName() {
        assertEquals("Без названия", Scenario(title = "  ").displayTitle)
    }
}
