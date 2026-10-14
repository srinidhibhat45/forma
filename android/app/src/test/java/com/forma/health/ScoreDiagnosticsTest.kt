package com.forma.health

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.exp

class ScoreDiagnosticsTest {
    private val date=LocalDate.of(2026,10,14)
    private val prior=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)) }
    private fun day(steps:Double=100.0)=HealthDay(date,mapOf("sleepMinutes" to 412.8,"sleepEfficiency" to 86.0,"hrv" to 60*exp(.1*11/12),"restingHeartRate" to 57.25),samples=listOf(EnergySample(10.0,steps=steps,heartRate=57.25,recordedAt=Instant.parse("2026-10-14T10:00:00Z"))))
    @Test fun realInputsCanRoundAllThreeScoresTo86AndExplainTheFractionalLoad() {
        val d=day();val sleep=Scores.sleep(d,480);val recovery=Scores.recovery(d,prior,480);val energy=Scores.energy(d,prior,480)
        assertEquals(86,sleep.value);assertEquals(86,recovery.value);assertEquals(86,energy.score.value)
        assertEquals(85.85,energy.points.last().second,.0001)
        assertTrue(ScoreDiagnostics.matchingReason(sleep,recovery,energy)!!.contains("-0.15 points"))
        val active=day(2000.0)
        assertEquals(86,Scores.sleep(active,480).value);assertEquals(86,Scores.recovery(active,prior,480).value)
        val spent=Scores.energy(active,prior,480);assertEquals(83,spent.score.value)
        assertNull(ScoreDiagnostics.matchingReason(sleep,recovery,spent))
    }
    @Test fun oldActivityIsFlaggedForTodayButNotMistakenForStaleHistoricalData() {
        val d=day();val energy=Scores.energy(d,prior,480);val now=Instant.parse("2026-10-14T13:00:00Z")
        assertTrue(ScoreDiagnostics.activityStatus(d,energy,now,ZoneOffset.UTC).contains("Waiting for newer"))
        assertFalse(ScoreDiagnostics.activityStatus(d.copy(date=date.minusDays(1)),energy,now,ZoneOffset.UTC).contains("Waiting for newer"))
        assertTrue(ScoreDiagnostics.activityStatus(d,energy.copy(recordedAt=null),now,ZoneOffset.UTC).contains("timestamp unavailable"))
    }
    @Test fun copiedReportDistinguishesMissingInputsFromZeroAndExplainsRemainingWeights() {
        val d=day().copy(values=day().values-"hrv");val sleep=Scores.sleep(d,480);val recovery=Scores.recovery(d,prior,480);val energy=Scores.energy(d,prior,480)
        val text=ScoreDiagnostics.report(d,sleep,recovery,energy,480,"Samsung Health · Health Connect")
        assertTrue(text.contains("80.00% of available weight"));assertTrue(text.contains("20.00% of available weight"))
        assertTrue(text.contains("hrv: not shared"));assertTrue(text.contains("Explicit rest minutes: not shared"))
        assertTrue(text.contains("Steps used by energy: 100.00"));assertTrue(text.contains("Unrounded energy: 85.85"))
        assertTrue(text.contains("2026-10-14T10:00:00Z"));assertTrue(text.contains("do not currently feed these scores"))
    }
}
