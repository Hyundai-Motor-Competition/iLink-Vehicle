package com.ilink.vehicle.domain

/**
 * F6-01 탑승 자동 인식의 차량 쪽 판정 (B2~B4). B1(호출 유효)·장소 일치는 서버가 다시 검증한다.
 * - B3: 운전석(1열 좌측, 좌핸들 기준) 제외
 * - B2: 정차 중일 때만
 * - B4: 열림 → 닫힘 1회 완결. 연달아 여닫으면 마지막 닫힘 후 settleMillis 동안 조용해야 1회 전송
 */
class DoorCycleDetector(private val settleMillis: Long = 3_000) {

    enum class Door(val serverCode: String?) {
        DRIVER(null),
        FRONT_RIGHT("FRONT_RIGHT"),
        REAR_LEFT("REAR_LEFT"),
        REAR_RIGHT("REAR_RIGHT"),
    }

    data class Cycle(val door: Door, val openedAt: Long, val closedAt: Long)

    private val openedAt = mutableMapOf<Door, Long>()
    private var pending: Cycle? = null

    /** 도어 상태 변경. 정차 중이 아니면 기록하지 않는다. */
    fun onDoor(door: Door, open: Boolean, now: Long, stopped: Boolean) {
        if (door == Door.DRIVER || !stopped) {
            openedAt.remove(door)
            return
        }
        if (open) {
            openedAt[door] = now
            pending = null // 다시 열렸으니 마지막 닫힘을 기다린다
        } else {
            val start = openedAt.remove(door) ?: return
            pending = Cycle(door, start, now)
        }
    }

    /** 주기적으로 호출. 완결된 개폐가 settle 시간을 넘기면 한 번 돌려준다. */
    fun poll(now: Long): Cycle? {
        val p = pending ?: return null
        if (openedAt.isNotEmpty() || now - p.closedAt < settleMillis) return null
        pending = null
        return p
    }

    /** 주행 시작 등으로 판정을 버릴 때 */
    fun reset() {
        openedAt.clear()
        pending = null
    }
}
