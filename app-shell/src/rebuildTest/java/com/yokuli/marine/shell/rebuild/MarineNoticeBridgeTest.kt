package com.yokuli.marine.shell.rebuild

import com.yokuli.anchorwatch.data.database.AlarmEventEntity
import org.junit.Assert.*
import org.junit.Test

class MarineNoticeBridgeTest {
    @Test fun backgroundWarningRetainsEventIdentityAndTime() {
        val event=AlarmEventEntity(81,4,123456L,"WARNING_TRIGGERED")
        val notice=requireNotNull(event.asNotice())
        assertEquals("anchor-event:81",notice.id)
        assertEquals(123456L,notice.createdAt)
        assertEquals(NoticeSeverity.WARNING,notice.severity)
    }
    @Test fun ordinaryTelemetryDoesNotBecomeAlarm() {
        assertNull(AlarmEventEntity(82,4,123457L,"WIND_BASELINE_ESTABLISHED").asNotice())
    }
}
