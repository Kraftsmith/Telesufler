package app.teleprompter.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private val cues = listOf(Cue(5.0, 7.0, "a"), Cue(8.0, 9.0, "b"))
    private val timeline = Timeline(cues, listOf(0f, 100f), 200f)

    @Test
    fun staysAtStartBeforeFirstCue() {
        assertEquals(0f, timeline.posAt(1.0), 0.01f)
    }

    @Test
    fun scrollsThroughCueDuringItsTime() {
        assertEquals(50f, timeline.posAt(6.0), 0.01f)
    }

    @Test
    fun waitsThroughGapBetweenCues() {
        assertEquals(100f, timeline.posAt(7.5), 0.01f)
    }

    @Test
    fun endsAtFullHeight() {
        assertEquals(200f, timeline.posAt(20.0), 0.01f)
    }

    @Test
    fun timeAtIsTheInverseOfPosAt() {
        assertEquals(6.0, timeline.timeAt(50f), 0.01)
        assertEquals(8.5, timeline.timeAt(150f), 0.01)
    }

    @Test
    fun overlappingCuesStayInOrder() {
        val overlap = Timeline(listOf(Cue(1.0, 5.0, "a"), Cue(2.0, 3.0, "b")), listOf(0f, 100f), 200f)
        assertEquals(100f, overlap.posAt(2.0), 0.01f)
        assertEquals(150f, overlap.posAt(2.5), 0.01f)
    }
}
