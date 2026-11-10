package com.ilink.vehicle.domain

import com.ilink.vehicle.data.model.SessionView

/**
 * F4-04 · F4-07: 오래된 stateVersion과 종료 이후 늦은 응답은 버린다.
 * 차량 앱은 COMPLETED를 스스로 만들지 않는다 (서버 응답·이벤트만 반영).
 */
object SessionReducer {
    fun merge(prev: SessionView?, next: SessionView): SessionView = when {
        prev == null || prev.sessionId != next.sessionId -> next
        prev.isTerminal -> prev
        next.stateVersion < prev.stateVersion -> prev
        else -> next
    }
}
