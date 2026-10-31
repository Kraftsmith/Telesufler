package app.teleprompter.recorder

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.UUID

const val DEFAULT_TITLE = "Новый сценарий"

data class Scenario(
    val id: String = UUID.randomUUID().toString(),
    val title: String = DEFAULT_TITLE,
    val text: String = "",
    val cues: List<Cue> = emptyList(),
    val fileName: String = "",
    val useTimecodes: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayTitle: String get() = title.ifBlank { "Без названия" }

    fun blocks(): List<String> =
        if (cues.isNotEmpty()) cues.map { it.text }
        else text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun wordCount(): Int = blocks().sumOf { b -> b.split(Regex("""\s+""")).count { it.isNotEmpty() } }

    /** Subtitles know their own length, plain text is estimated from the reading speed. */
    fun readingSeconds(wpm: Int): Long =
        if (cues.isNotEmpty()) (cues.last().end - cues.first().start).toLong()
        else wordCount() * 60L / wpm

    fun preview(): String = blocks().take(3).joinToString(" ")

    fun matches(query: String): Boolean =
        query.isBlank() || title.contains(query.trim(), ignoreCase = true) ||
            blocks().any { it.contains(query.trim(), ignoreCase = true) }
}

/** "Мой ролик.ru.srt" becomes "Мой ролик.ru". */
fun titleFromFile(name: String): String = name.substringBeforeLast('.').trim().ifEmpty { name }

fun scenarioFromFile(name: String, raw: String): Scenario {
    val cues = parseSubtitles(raw)
    return Scenario(
        title = titleFromFile(name),
        text = if (cues.isEmpty()) raw else "",
        cues = cues,
        fileName = name,
        useTimecodes = cues.isNotEmpty(),
    )
}

fun cuesToJson(cues: List<Cue>): JSONArray {
    val json = JSONArray()
    cues.forEach { json.put(JSONObject().put("s", it.start).put("e", it.end).put("t", it.text)) }
    return json
}

fun cuesFromJson(arr: JSONArray): List<Cue> = List(arr.length()) { i ->
    val o = arr.getJSONObject(i)
    Cue(o.getDouble("s"), o.getDouble("e"), o.getString("t"))
}

/** Keeps every scenario as its own JSON file, so one damaged record cannot take the others with it. */
class ScenarioStore(private val dir: File) {
    fun list(): List<Scenario> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull(::read)
            .sortedByDescending { it.updatedAt }

    fun save(s: Scenario) {
        dir.mkdirs()
        val json = JSONObject()
            .put("id", s.id).put("title", s.title).put("text", s.text)
            .put("cues", cuesToJson(s.cues)).put("file", s.fileName)
            .put("timed", s.useTimecodes).put("updated", s.updatedAt)
        // Written aside first, so a crash mid-write leaves the previous version intact.
        val target = File(dir, "${s.id}.json")
        val tmp = File(dir, "${s.id}.tmp")
        tmp.writeText(json.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun read(file: File): Scenario? = try {
        val o = JSONObject(file.readText(Charsets.UTF_8))
        Scenario(
            id = o.getString("id"),
            title = o.getString("title"),
            text = o.getString("text"),
            cues = cuesFromJson(o.getJSONArray("cues")),
            fileName = o.getString("file"),
            useTimecodes = o.getBoolean("timed"),
            updatedAt = o.getLong("updated"),
        )
    } catch (_: JSONException) {
        null
    }
}
