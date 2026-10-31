package app.teleprompter.recorder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

private const val SAMPLE = """Это пример текста. Замените его своим: нажмите «Текст» вверху слева.

Читайте по красной стрелке. Текст движется сам, а скорость меняется кнопками внизу.

Нажмите на текст, чтобы поставить его на паузу. Потяните пальцем, чтобы перемотать.

Загрузите файл субтитров, и текст пойдёт точно по таймкодам.

Красная кнопка запускает запись. Текст стартует вместе с ней и в видео не попадает."""

/** Everything the screen shows about the script: its text, scroll position and settings. */
class PrompterState(context: Context) {
    private val prefs = context.getSharedPreferences("teleprompter", Context.MODE_PRIVATE)

    var wpm by mutableIntStateOf(prefs.getInt("wpm", 130))
    var tempo by mutableIntStateOf(prefs.getInt("tempo", 100))
    var size by mutableIntStateOf(prefs.getInt("size", 40))
    var panel by mutableIntStateOf(prefs.getInt("panel", 50))
    var dim by mutableIntStateOf(prefs.getInt("dim", 55))
    var mirror by mutableStateOf(prefs.getBoolean("mirror", false))
    var countdown by mutableIntStateOf(prefs.getInt("countdown", 3))
    var quality by mutableIntStateOf(prefs.getInt("quality", 1080))
    var frontCamera by mutableStateOf(prefs.getBoolean("front", true))

    var text by mutableStateOf(prefs.getString("text", SAMPLE) ?: SAMPLE)
        private set
    var cues by mutableStateOf(loadCues())
        private set
    var fileName by mutableStateOf(prefs.getString("file", "") ?: "")
        private set
    var useTimecodes by mutableStateOf(prefs.getBoolean("timed", false) && cues.isNotEmpty())
        private set

    var running by mutableStateOf(false)
    var pos by mutableFloatStateOf(0f)
        private set
    var active by mutableIntStateOf(0)
        private set

    private var clock by mutableDoubleStateOf(0.0)
    private var tops: List<Float> = emptyList()
    private var height = 0f
    private var timeline = Timeline(emptyList(), emptyList(), 0f)

    val timed: Boolean get() = useTimecodes && cues.isNotEmpty()

    var blocks by mutableStateOf(computeBlocks())
        private set
    var words by mutableIntStateOf(countWords())
        private set

    private fun computeBlocks(): List<String> =
        if (cues.isNotEmpty()) cues.map { it.text }
        else text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun countWords(): Int =
        computeBlocks().sumOf { b -> b.split(Regex("""\s+""")).count { it.isNotEmpty() } }.coerceAtLeast(1)

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
        text = value
        cues = emptyList()
        fileName = ""
        useTimecodes = false
        restart()
    }

    fun loadFile(name: String, raw: String) {
        val parsed = parseSubtitles(raw)
        fileName = name
        cues = parsed
        text = if (parsed.isEmpty()) raw else ""
        useTimecodes = parsed.isNotEmpty()
        restart()
    }

    fun chooseTimecodes(on: Boolean) {
        useTimecodes = on && cues.isNotEmpty()
        restart()
    }

    private fun restart() {
        blocks = computeBlocks()
        words = countWords()
        running = false
        pos = 0f
        timeline = Timeline(cues, tops, height)
        clock = timeline.startTime
        updateActive()
        save()
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

    private fun loadCues(): List<Cue> {
        val raw = prefs.getString("cues", null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Cue(o.getDouble("s"), o.getDouble("e"), o.getString("t"))
        }
    }

    fun save() {
        val json = JSONArray()
        cues.forEach { json.put(JSONObject().put("s", it.start).put("e", it.end).put("t", it.text)) }
        prefs.edit()
            .putInt("wpm", wpm).putInt("tempo", tempo).putInt("size", size).putInt("panel", panel)
            .putInt("dim", dim).putBoolean("mirror", mirror).putInt("countdown", countdown)
            .putInt("quality", quality).putBoolean("front", frontCamera)
            .putString("text", text).putString("file", fileName)
            .putString("cues", json.toString()).putBoolean("timed", useTimecodes)
            .apply()
    }
}
