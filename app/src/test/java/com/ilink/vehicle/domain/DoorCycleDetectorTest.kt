package com.ilink.vehicle.domain

import com.ilink.vehicle.domain.DoorCycleDetector.Door
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoorCycleDetectorTest {
    private val d = DoorCycleDetector(settleMillis = 3_000)

    @Test fun `뒷좌석 문이 열렸다 닫히면 settle 후 1회 (QA-35)`() {
        d.onDoor(Door.REAR_RIGHT, open = true, now = 0, stopped = true)
        d.onDoor(Door.REAR_RIGHT, open = false, now = 5_000, stopped = true)
        assertNull(d.poll(6_000))
        assertEquals(Door.REAR_RIGHT, d.poll(8_000)?.door)
        assertNull(d.poll(9_000))
    }

    @Test fun `운전석 문은 무시 (QA-36)`() {
        d.onDoor(Door.DRIVER, open = true, now = 0, stopped = true)
        d.onDoor(Door.DRIVER, open = false, now = 1_000, stopped = true)
        assertNull(d.poll(10_000))
    }

    @Test fun `주행 중 개폐는 무시 (QA-36)`() {
        d.onDoor(Door.REAR_LEFT, open = true, now = 0, stopped = false)
        d.onDoor(Door.REAR_LEFT, open = false, now = 1_000, stopped = false)
        assertNull(d.poll(10_000))
    }

    @Test fun `여러 번 여닫으면 마지막 닫힘 기준 1회 (B4)`() {
        d.onDoor(Door.REAR_LEFT, true, 0, true)
        d.onDoor(Door.REAR_LEFT, false, 1_000, true)
        d.onDoor(Door.REAR_LEFT, true, 2_000, true)
        d.onDoor(Door.REAR_LEFT, false, 3_000, true)
        assertNull(d.poll(5_000))
        assertEquals(3_000L, d.poll(6_000)?.closedAt)
        assertNull(d.poll(20_000))
    }
}
