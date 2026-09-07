package com.flockyou.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerHardwareOuiTest {
    @Test
    fun `walk OUIs preserve vendor evidence without asserting product`() {
        assertEquals("SAMJIN Co., Ltd.", DetectionPatterns.getManufacturerFromOui("A4:57:A0"))
        assertEquals("SJIT Co., Ltd.", DetectionPatterns.getManufacturerFromOui("34:FC:99"))
        assertEquals("Samsung Electronics Co.,Ltd", DetectionPatterns.getManufacturerFromOui("5C:C1:D7"))
    }
}
