package app.teleprompter.recorder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File

private const val SAMPLE = """Это пример текста. Чтобы выбрать другой, нажмите на название сценария вверху слева.

Читайте по красной стрелке. Текст движется сам, а скорость меняется кнопками внизу.

Нажмите на текст, чтобы поставить его на паузу. Потяните пальцем, чтобы перемотать.

Загрузите файл субтитров, и текст пойдёт точно по таймкодам.

Красная кнопка запускает запись. Текст стартует вместе с ней и в видео не попадает."""

/** Everything the screen shows about the script: its text, scroll position and settings. */
class PrompterState(context: Context) {
    private val prefs = context.getSharedPreferences("teleprompter", Context.MODE_PRIVATE)
    private val store = ScenarioStore(File(context.filesDir, "scenarios"))

    var wpm by mutableIntStateOf(prefs.getInt("wpm", 130))
    var tempo by mutableIntStateOf(prefs.getInt("tempo", 100))
    var size by mutableIntStateOf(prefs.getInt("size", 40))
    var panel by mutableIntStateOf(prefs.getInt("panel", 50))
    var dim by mutableIntStateOf(prefs.getInt("dim", 55))
    var mirror by mutableStateOf(prefs.getBoolean("mirror", false))
    var countdown by mutableIntStateOf(prefs.getInt("countdown", 3))
    var quality by mutableIntStateOf(prefs.getInt("quality", 1080))
    var frontCamera by mutableStateOf(prefs.getBoolean("front", true))

    var scenarios by mutableStateOf(loadScenarios())
        private set
    var currentId by mutableStateOf(
        prefs.getString("current", null)?.takeIf { id -> scenarios.any { it.id == id } } ?: scenarios.first().id,
    )
        private set

    val current: Scenario get() = scenarios.first { it.id == currentId }
    val text: String get() = current.text
    val cues: List<Cue> get() = current.cues
    val fileName: String get() = current.fileName

    var running by mutableStateOf(false)
    var pos by mutableFloatStateOf(0f)
        private set
    var active by mutableIntStateOf(0)
        private set

    private var clock by mutableDoubleStateOf(0.0)
    private var tops: List<Float> = emptyList()
    private var height = 0f
    private var timeline = Timeline(emptyList(), emptyList(), 0f)

    val timed: Boolean get() = current.useTimecodes && cues.isNotEmpty()

    var blocks by mutableStateOf(current.blocks())
        private set
    var words by mutableIntStateOf(current.wordCount().coerceAtLeast(1))
        private set

    /** Expected reading time in seconds. */
    val duration: Double
        get() = if (timed) (timeline.endTime - timeline.startTime) / (tempo / 100.0) else words * 60.0 / wpm

    /** Called on every scrolled frame too, so it must do nothing unless the layout really changed. */
    fun setLayout(newTops: List<Float>, newHeight: Float) {
        if (newHeight == height && newTops == tops) return
        val frac = if (height > 0) pos / height else 0f
        tops = newTops
        height = newHeight
        timeline = Timeline(cues, tops, height)
        pos = frac * height
        if (timed) clock = timeline.timeAt(pos)
        updateActive()
    }

    fun tick(dt: Float) {
        if (!running || height <= 0) return
        if (timed) {
            clock += dt * tempo / 100.0
            pos = timeline.posAt(clock)
        } else {
            pos += (wpm / 60f) * (height / words) * dt
        }
        if (pos >= height) {
            pos = height
            running = false
        }
        updateActive()
    }

    fun togglePlay() {
        if (!running && atEnd) rewind()
        running = !running
    }

    val atEnd: Boolean get() = height > 0 && pos >= height

    fun rewind() {
        pos = 0f
        clock = timeline.startTime
        updateActive()
    }

    fun dragBy(dy: Float) {
        pos = (pos - dy).coerceIn(0f, height)
        if (timed) clock = timeline.timeAt(pos)
        updateActive()
    }

    fun nudgeSpeed(dir: Int) {
        if (timed) tempo = (tempo + dir * 5).coerceIn(50, 200)
        else wpm = (wpm + dir * 10).coerceIn(50, 300)
        save()
    }

    fun setPlainText(value: String) {
        updateCurrent { it.copy(text = value, cues = emptyList(), fileName = "", useTimecodes = false) }
        restart()
    }

    /** Replaces the content of the current scenario with a file. */
    fun loadFile(name: String, raw: String) {
        val loaded = scenarioFromFile(name, raw)
        updateCurrent {
            it.copy(
                title = if (it.title == DEFAULT_TITLE) loaded.title else it.title,
                text = loaded.text,
                cues = loaded.cues,
                fileName = name,
                useTimecodes = loaded.useTimecodes,
            )
        }
        restart()
    }

    fun chooseTimecodes(on: Boolean) {
        updateCurrent { it.copy(useTimecodes = on && it.cues.isNotEmpty()) }
        restart()
    }

    fun rename(title: String) {
        updateCurrent { it.copy(title = title) }
    }

    fun select(id: String) {
        if (id == currentId) return
        currentId = id
        save()
        restart()
    }

    fun create() {
        add(Scenario())
    }

    fun createFromFile(name: String, raw: String) {
        add(scenarioFromFile(name, raw))
    }

    fun delete(id: String) {
        store.delete(id)
        val rest = scenarios.filter { it.id != id }
        if (rest.isEmpty()) {
            scenarios = emptyList()
            add(Scenario())
            return
        }
        // The list must stay valid for readers of current, so the selection moves first.
        if (id == currentId) currentId = rest.first().id
        scenarios = rest
        save()
        restart()
    }

    private fun add(s: Scenario) {
        store.save(s)
        scenarios = listOf(s) + scenarios
        currentId = s.id
        save()
        restart()
    }

    private fun updateCurrent(change: (Scenario) -> Scenario) {
        val updated = change(current).copy(updatedAt = System.currentTimeMillis())
        store.save(updated)
        scenarios = (scenarios.filter { it.id != updated.id } + updated).sortedByDescending { it.updatedAt }
    }

    private fun restart() {
        blocks = current.blocks()
        words = current.wordCount().coerceAtLeast(1)
        running = false
        pos = 0f
        timeline = Timeline(cues, tops, height)
        clock = timeline.startTime
        updateActive()
    }

    private fun updateActive() {
        var lo = 0
        var hi = tops.lastIndex
        var idx = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (tops[mid] <= pos + 1) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        active = idx
    }

    /** First launch after the single-script versions turns the old script into the first scenario. */
    private fun loadScenarios(): List<Scenario> {
        store.list().takeIf { it.isNotEmpty() }?.let { return it }
        val oldText = prefs.getString("text", null)
        val oldFile = prefs.getString("file", "").orEmpty()
        val oldCues = prefs.getString("cues", null)
            ?.let { raw -> runCatching { cuesFromJson(JSONArray(raw)) }.getOrNull() }
            .orEmpty()
        val first = when {
            oldCues.isNotEmpty() || (oldText != null && !oldText.startsWith("Это пример текста")) -> Scenario(
                title = if (oldFile.isEmpty()) "Мой сценарий" else titleFromFile(oldFile),
                text = oldText.orEmpty(),
                cues = oldCues,
                fileName = oldFile,
                useTimecodes = prefs.getBoolean("timed", false) && oldCues.isNotEmpty(),
            )
            else -> Scenario(title = "Пример", text = SAMPLE)
        }
        store.save(first)
        prefs.edit().remove("text").remove("file").remove("cues").remove("timed").apply()
        return listOf(first)
    }

    fun save() {
        prefs.edit()
            .putInt("wpm", wpm).putInt("tempo", tempo).putInt("size", size).putInt("panel", panel)
            .putInt("dim", dim).putBoolean("mirror", mirror).putInt("countdown", countdown)
            .putInt("quality", quality).putBoolean("front", frontCamera)
            .putString("current", currentId)
            .apply()
    }
}
