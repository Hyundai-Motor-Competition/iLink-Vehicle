package com.ilink.vehicle.domain

import com.ilink.vehicle.data.model.SessionStatus
import com.ilink.vehicle.data.model.SessionView
import org.junit.Assert.assertSame
import org.junit.Test

class SessionReducerTest {
    private fun v(version: Int, status: String = "ACTIVE") = SessionView(1, version, SessionStatus(session = status))

    @Test fun `낮은 버전은 버린다 (F4-04)`() {
        val prev = v(5)
        assertSame(prev, SessionReducer.merge(prev, v(4)))
    }

    @Test fun `종료 후 늦은 이벤트로 되돌아가지 않는다 (QA-18)`() {
        val done = v(9, "COMPLETED")
        assertSame(done, SessionReducer.merge(done, v(10)))
    }
}
