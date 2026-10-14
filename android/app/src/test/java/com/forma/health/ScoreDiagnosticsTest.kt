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
    @Test fun twoEqualRoundedScoresExposeTheirDifferentUnderlyingCalculations() {
        val sleep=Score(86,"Limited",listOf(Factor("Duration",82.0,"",.5),Factor("Efficiency",93.5,"",.3)))
        val recovery=Score(86,"Limited",listOf(Factor("Sleep",86.0,"",.6),Factor("Sleeping heart",85.5,"",.15)))
        val report=ScoreDiagnostics.report(null,sleep,recovery,EnergyResult(Score(null,"Needs activity data"),emptyList()),480,"Samsung Health · Health Connect")
        assertTrue(report.contains("Before rounding: 86.31"));assertTrue(report.contains("Before rounding: 85.90"))
        assertTrue(report.contains("Energy/body battery: unavailable"));assertFalse(report.contains("Unrounded energy:"))
    }
    @Test fun readerDiagnosticsSeparateMissingSummariesFromTimestampedFallbacks() {
        val from=Instant.parse("2026-10-14T08:20:00Z");val to=from.plusSeconds(7200);val latest=from.plusSeconds(600)
        val d=day().copy(energyRead=EnergyReadReport(from,to,true,true,0,0,3,1500.0,2,latest,1))
        val report=ScoreDiagnostics.report(d,Scores.sleep(d,480),Scores.recovery(d,prior,480),Scores.energy(d,prior,480),480,"Samsung Health · Health Connect")
        assertTrue(report.contains("Hourly summaries: 0 returned · 0 match"))
        assertTrue(report.contains("Exact-time fallback queries: 3"));assertTrue(report.contains("Exact post-wake step probe: 1500.00"))
        assertTrue(report.contains("Raw post-wake HR: 2 observed minutes"));assertTrue(report.contains("Raw HR fallback buckets: 1"))
    }
}
