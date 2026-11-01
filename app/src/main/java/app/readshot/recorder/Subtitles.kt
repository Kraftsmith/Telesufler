package app.readshot.recorder

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class Cue(val start: Double, val end: Double, val text: String)

private val TIME = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})$""")
private val ARROW = Regex("""^\s*([\d:.,]+)\s*-->\s*([\d:.,]+)""")
private val STYLE_TAG = Regex("""\{\\[^}]*\}""")
private val HTML_TAG = Regex("""<[^>]+>""")
private val CUE_NUMBER = Regex("""^\s*\d+\s*$""")

fun parseTime(s: String): Double? {
    val m = TIME.find(s.trim()) ?: return null
    val (h, min, sec, ms) = m.destructured
    return (h.toIntOrNull() ?: 0) * 3600.0 + min.toInt() * 60 + sec.toInt() + ms.padEnd(3, '0').toInt() / 1000.0
}

private fun cleanCue(s: String): String = s
    .replace(STYLE_TAG, "")
    .replace(HTML_TAG, "")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .trim()

/** Parses SRT and VTT. Returns an empty list when the text has no timecodes. */
fun parseSubtitles(raw: String): List<Cue> {
    class Draft(val start: Double, val end: Double, val lines: MutableList<String> = mutableListOf())

    val parsed = mutableListOf<Draft>()
    var cur: Draft? = null
    for (line in raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        val m = ARROW.find(line)
        val start = m?.let { parseTime(it.groupValues[1]) }
        val end = m?.let { parseTime(it.groupValues[2]) }
        if (start != null && end != null) {
            // A cue number with no blank line before it would otherwise land in the previous cue.
            cur?.lines?.lastOrNull()?.let { if (CUE_NUMBER.matches(it)) cur.lines.removeAt(cur.lines.lastIndex) }
            cur = Draft(start, end).also(parsed::add)
        } else if (line.isBlank()) {
            cur = null
        } else {
            val clean = cleanCue(line)
            if (clean.isNotEmpty()) cur?.lines?.add(clean)
        }
    }

    // Auto-generated YouTube captions repeat the previous line in every cue.
    val cues = mutableListOf<Cue>()
    var prevLines: List<String> = emptyList()
    for (d in parsed.sortedBy { it.start }) {
        val fresh = d.lines.filter { it !in prevLines }
        prevLines = d.lines
        if (fresh.isNotEmpty()) {
            cues += Cue(d.start, d.end, fresh.joinToString("\n"))
        } else if (cues.isNotEmpty()) {
            val last = cues.removeAt(cues.lastIndex)
            cues += last.copy(end = maxOf(last.end, d.end))
        }
    }
    return cues
}

/** Old Russian subtitles are often in Windows-1251, so UTF-8 is tried strictly first. */
fun decodeText(b: ByteArray): String {
    if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
    if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b))
            .toString()
    } catch (_: CharacterCodingException) {
        String(b, Charset.forName("windows-1251"))
    }
}
