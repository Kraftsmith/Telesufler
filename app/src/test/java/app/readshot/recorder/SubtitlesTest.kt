package app.readshot.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {
    @Test
    fun parsesSrtWithBomTagsAndMissingBlankLine() {
        val srt = "﻿1\r\n00:00:02,000 --> 00:00:04,500\r\n<i>Привет</i>, это {\\an8}первая\r\n\r\n" +
            "2\r\n00:00:05,000 --> 00:00:07,000\r\nВторая\r\nстрока\r\n" +
            "3\r\n00:00:08,000 --> 00:00:09,000\r\nТретья\r\n"
        assertEquals(
            listOf(
                Cue(2.0, 4.5, "Привет, это первая"),
                Cue(5.0, 7.0, "Вторая\nстрока"),
                Cue(8.0, 9.0, "Третья"),
            ),
            parseSubtitles(srt),
        )
    }

    @Test
    fun parsesVttWithShortTimesNotesAndSettings() {
        val vtt = "WEBVTT\n\nNOTE комментарий\n\n00:01.000 --> 00:02.500 align:center\nОдин &amp; два\n\n" +
            "01:00:03.000 --> 01:00:04.000\n<v Анна>Час\n"
        assertEquals(listOf(Cue(1.0, 2.5, "Один & два"), Cue(3603.0, 3604.0, "Час")), parseSubtitles(vtt))
    }

    @Test
    fun dropsRollingRepeatsFromYoutube() {
        val yt = "WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nhello\n\n" +
            "00:00:02.000 --> 00:00:02.010\nhello\nworld\n\n00:00:02.010 --> 00:00:04.000\nworld\n"
        assertEquals(listOf(Cue(0.0, 2.0, "hello"), Cue(2.0, 4.0, "world")), parseSubtitles(yt))
    }

    @Test
    fun plainTextHasNoCues() {
        assertTrue(parseSubtitles("просто текст\nещё строка").isEmpty())
    }

    @Test
    fun decodesWindows1251AndUtf8() {
        val cp1251 = byteArrayOf(0xCF.toByte(), 0xF0.toByte(), 0xE8.toByte(), 0xE2.toByte(), 0xE5.toByte(), 0xF2.toByte())
        assertEquals("Привет", decodeText(cp1251))
        assertEquals("Привет", decodeText("Привет".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decodesUtf16WithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Да".toByteArray(Charsets.UTF_16LE)
        assertEquals("Да", decodeText(bytes))
    }
}
