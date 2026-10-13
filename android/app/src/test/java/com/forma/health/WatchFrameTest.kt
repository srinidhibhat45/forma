package com.forma.health

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WatchFrameTest {
    private val now=Instant.parse("2026-10-13T10:00:00Z")
    @Test fun liveHeartExpiresIndependentlyOfTransportAndDailyActivity() {
        val frame=WatchFrame("Watch6",now,heart=72.0,heartAt=now.minusSeconds(31),steps=4000.0,stepsAt=now.minusSeconds(20))
        assertNull(frame.currentHeart(now));assertEquals(4000.0,frame.currentSteps(now,ZoneOffset.UTC)!!,.001)
        assertNull(frame.copy(stepsAt=now.minusSeconds(301)).currentSteps(now,ZoneOffset.UTC))
        assertNull(frame.copy(stepsAt=now.minusSeconds(86400)).currentSteps(now,ZoneOffset.UTC))
    }
    @Test fun newHeartMessageDoesNotRefreshOldStepsOrRegressCorrections() {
        val old=WatchFrame("Watch6",now,steps=4000.0,stepsAt=now.minusSeconds(30))
        val new=WatchFrame("Watch6",now.plusSeconds(2),heart=75.0,heartAt=now.plusSeconds(2),steps=200.0,stepsAt=now.minusSeconds(300))
        val merged=WatchFrame.merge(old,new)
        assertEquals(4000.0,merged.steps!!,.001);assertEquals(old.stepsAt,merged.stepsAt);assertEquals(75.0,merged.heart!!,.001)
    }
    @Test fun latestDailyCounterCanResetAtMidnight() {
        val old=WatchFrame("Watch6",now,steps=10000.0,stepsAt=now.minusSeconds(60));val fresh=WatchFrame("Watch6",now,steps=5.0,stepsAt=now)
        assertEquals(5.0,WatchFrame.merge(old,fresh).steps!!,.001)
    }
    @Test fun boundedProtocolRoundTripsMeasurementTimes() {
        val f=WatchFrame("Watch6",now,72.0,now,4000.0,now.minusSeconds(30),200.0,now.minusSeconds(60),3.2,now.minusSeconds(60),8.0,now.minusSeconds(60))
        assertEquals(f,WatchFrame.decode(f.json().toByteArray(),now))
    }
    @Test fun oversizedInvalidAndFutureMessagesAreRejected() {
        fun rejected(bytes:ByteArray)=assertTrue(runCatching { WatchFrame.decode(bytes,now) }.isFailure)
        rejected(ByteArray(8193));rejected(WatchFrame("Watch6",now,999.0,now).json().toByteArray());rejected(WatchFrame("Watch6",now.plusSeconds(61)).json().toByteArray());rejected("""{"version":1,"sentAt":"2026-10-13T10:00:00Z","heart":72}""".toByteArray())
    }
}
