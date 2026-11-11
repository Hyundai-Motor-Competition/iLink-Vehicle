package com.ilink.vehicle.domain

import com.ilink.vehicle.demo.DemoData
import com.ilink.vehicle.platform.voice.VoiceCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VoiceCuesTest {
    private val now = Instant.parse("2026-11-10T09:00:00Z")

    @Test fun routeConfirmedAnnouncesStartAndDeparture() {
        val cues = VoiceCues.onSession(DemoData.sessionSelecting(now), DemoData.sessionRouting(now)).map { it.banner.cue }
        assertEquals(listOf(VoiceCue.U02, VoiceCue.U03), cues)
    }

    @Test fun callSentAnnouncesOnce() {
        val cues = VoiceCues.onSession(DemoData.sessionRouting(now), DemoData.sessionCalled(now)).map { it.banner.cue }
        assertTrue(VoiceCue.U05 in cues)
        assertTrue(VoiceCues.onSession(DemoData.sessionCalled(now), DemoData.sessionCalled(now)).isEmpty())
    }

    @Test fun completionAndNotBoarded() {
        assertEquals(VoiceCue.U07, VoiceCues.onSession(DemoData.sessionBoarding(now), DemoData.sessionCompleted(now)).single().banner.cue)
        assertEquals(VoiceCue.U06, VoiceCues.onSession(DemoData.sessionCalled(now), DemoData.sessionNotBoarded(now)).single().banner.cue)
    }

    @Test fun preCallWarningOnlyBeforeThreeMinutes() {
        val r = DemoData.sessionRouting(now)
        val near = r.copy(eta = r.eta?.copy(remainingSeconds = 200))
        assertEquals(VoiceCue.U04, VoiceCues.onSession(r, near).single().banner.cue)
    }
}
