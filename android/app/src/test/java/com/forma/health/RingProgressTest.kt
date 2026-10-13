package com.forma.health

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RingProgressTest {
    @Test fun everySupportedMeasurementHasADiscoverableMetricView() { assertEquals(HealthJson.recognizedFields,healthCatalog.map { it.key }.toSet()) }
    @Test fun missingMovementIsNotZeroOrClosed() {
        val ring=RingProgress(null,10000)
        assertNull(ring.fraction);assertNull(ring.remaining);assertFalse(ring.closed)
        assertEquals(10000.0,RingProgress(0.0,10000).remaining!!,.001)
    }
    @Test fun goalsCanCloseAndExceedOneHundredPercent() {
        val ring=RingProgress(12000.0,10000)
        assertTrue(ring.closed);assertEquals(1.2,ring.fraction!!,.001);assertEquals(0.0,ring.remaining!!,.001)
        assertEquals(15.0,RingProgress(15.0,30).remaining!!,.001)
    }
    @Test fun invalidTargetsAndValuesDoNotProduceProgress() {
        assertNull(RingProgress(100.0,0).fraction)
        assertNull(RingProgress(Double.NaN,100).fraction)
        assertNull(RingProgress(-1.0,100).fraction)
    }
    @Test fun heartTimestampAndHourlyAveragesSurviveExport() {
        val at=Instant.parse("2026-10-13T10:05:00Z");val day=HealthDay(LocalDate.of(2026,10,13),mapOf("heartRateLatest" to 88.0,"heartRateAverage" to 79.0),heartRecordedAt=at,heartTimeline=listOf(HeartPoint(at.minusSeconds(300),79.0)))
        val data=HealthData("Samsung Health · Health Connect",listOf(day),at)
        assertEquals(data,HealthJson.decode(HealthJson.encode(data)))
    }
}
