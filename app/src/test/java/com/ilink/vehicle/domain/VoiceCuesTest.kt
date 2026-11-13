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

    /** 도착 3분 전(또는 700m)에 한 번: 준비 알림 · 정차하면 합류 안내 (가드 G6) */
    @Test fun prepareNoticeAtThreeMinutesOnce() {
        val r = DemoData.sessionRouting(now)
        val far = r.copy(eta = r.eta?.copy(remainingSeconds = 200), route = r.route?.copy(remainingMeters = 1200))
        val near = r.copy(eta = r.eta?.copy(remainingSeconds = 170))
        assertTrue(VoiceCues.onSession(r, far).isEmpty())
        assertEquals(VoiceCue.U04, VoiceCues.onSession(far, near).single().banner.cue)
        assertTrue(VoiceCues.onSession(near, near.copy(eta = near.eta?.copy(remainingSeconds = 120))).isEmpty())
    }

    @Test fun nearByDistanceToo() {
        val r = DemoData.sessionRouting(now)
        val near = r.copy(route = r.route?.copy(remainingMeters = 650))
        assertEquals(VoiceCue.U04, VoiceCues.onSession(r, near).single().banner.cue)
    }
}
