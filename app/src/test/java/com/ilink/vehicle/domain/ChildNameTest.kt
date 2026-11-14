package com.ilink.vehicle.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChildNameTest {
    @Test fun `흔한 성씨일 때만 성을 뗀다`() {
        assertEquals("민지", givenName("김민지"))
        assertEquals("테스트", givenName("테스트"))
        assertEquals("테스트2", givenName("테스트2"))
    }

    @Test fun `사진 자리 이름`() {
        assertEquals("테스트", shortName("테스트"))
        assertEquals("늘이", shortName("남궁하늘이"))
    }
}
