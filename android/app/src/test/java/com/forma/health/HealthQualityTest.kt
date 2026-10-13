package com.forma.health

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HealthQualityTest {
    private val start=Instant.parse("2026-10-12T22:00:00Z")
    private fun at(minutes:Long)=start.plusSeconds(minutes*60)
    private fun stage(a:Long,b:Long,name:String)=TimedStage(at(a),at(b),name)
    private val date=LocalDate.of(2026,10,13)
    @Test fun genericSleepIsCountedAndDuplicateStagesAreNotAddedTwice() {
        val result=HealthQuality.sleep(start,at(480),listOf(stage(0,480,"Asleep"),stage(0,480,"Asleep")))
        assertEquals(480.0,result.asleepMinutes,.001);assertEquals(100.0,result.coverage,.001)
        assertEquals("Asleep",result.stages.single().stage)
    }
    @Test fun gapsStayOnTimelineAndDoNotBecomeAwake() {
        val result=HealthQuality.sleep(start,at(480),listOf(stage(0,180,"Light"),stage(300,480,"Deep")))
        assertEquals(480.0,result.stages.sumOf { it.minutes },.001)
        assertEquals(360.0,result.asleepMinutes,.001);assertEquals(75.0,result.coverage,.001)
        assertEquals("Unknown",result.stages[1].stage);assertNull(result.efficiency)
        assertNull(Scores.sleep(HealthDay(date,mapOf("sleepMinutes" to 360.0,"sleepCoverage" to 75.0)),480).value)
    }
    @Test fun conflictingAwakeAndSleepingStagesAreUnknown() {
        val result=HealthQuality.sleep(start,at(120),listOf(stage(0,120,"Light"),stage(30,90,"Awake")))
        assertEquals(60.0,result.asleepMinutes,.001);assertEquals(50.0,result.coverage,.001)
    }
    @Test fun clippedIntervalsAndOverlappingWorkoutsHaveOneDuration() {
        assertEquals(90.0,HealthQuality.unionMinutes(listOf(at(-30) to at(60),at(30) to at(120)),start,at(90)),.001)
        assertEquals(60.0,HealthQuality.sleep(start,at(60),listOf(stage(-20,80,"REM"))).asleepMinutes,.001)
    }
    @Test fun catchupIncludesAllDaysAfterAProlongedDisconnect() {
        val sync=Instant.parse("2026-09-01T12:00:00Z")
        assertEquals(LocalDate.of(2026,8,31),HealthQuality.recentStart(date,sync,ZoneOffset.UTC))
        assertEquals(date.minusDays(2),HealthQuality.recentStart(date,Instant.parse("2026-10-13T12:00:00Z"),ZoneOffset.UTC))
    }
    @Test fun reconciliationRemovesDeletedDaysInTheReadWindowOnly() {
        val days=(0..4).map { HealthDay(date.minusDays(it.toLong()),mapOf("steps" to 1000.0)) }
        val merged=HealthQuality.merge(days,listOf(HealthDay(date,mapOf("steps" to 2000.0))),date.minusDays(2),date)
        assertEquals(3,merged.size);assertFalse(merged.any { it.date==date.minusDays(1) })
        assertEquals(2000.0,merged.last()["steps"]!!,.001)
    }
    @Test fun baselineNeedsSevenDistinctDaysAndResistsOneOutlier() {
        val days=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("hrv" to if(it==1)400.0 else 60.0)) }
        assertEquals(60.0,PersonalSignals.baseline(days,date,"hrv")!!.typical,.001)
        assertNull(PersonalSignals.baseline(days.take(6),date,"hrv"))
        assertNull(PersonalSignals.baseline(List(10) { days[0] },date,"hrv"))
    }
    @Test fun noReadinessFromHeartSignalsWithoutSleep() {
        val days=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)) }
        assertNull(Scores.recovery(HealthDay(date,mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)),days,480).value)
    }
    @Test fun overlappingLoadDoesNotTripleCountOneExercise() {
        val days=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)) }
        val day=HealthDay(date,mapOf("sleepMinutes" to 480.0,"hrv" to 60.0,"restingHeartRate" to 60.0),samples=listOf(EnergySample(8.0,steps=4000.0,activeMinutes=30.0,heartRate=120.0)))
        val result=Scores.energy(day,days,480)
        assertEquals(6.0,result.points[0].second-result.points[1].second,.001)
    }
    @Test fun partialHeartBucketIsScaledToItsActualDuration() {
        val days=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        val day=HealthDay(date,mapOf("sleepMinutes" to 480.0,"restingHeartRate" to 60.0),samples=listOf(EnergySample(8.0,heartRate=120.0,durationMinutes=30.0)))
        val result=Scores.energy(day,days,480)
        assertEquals(1.05,result.points[0].second-result.points[1].second,.001)
    }
    @Test fun oldReadTimestampIsNotPresentedAsCurrentData() {
        assertEquals("Older records · sync Samsung Health",HealthQuality.freshness(start,start.plus(Duration.ofHours(3))))
        assertEquals("No timestamp shared",HealthQuality.freshness(null,start))
    }
    @Test fun historicalReportAndNewStageTypesSurviveLocalStorage() {
        val data=HealthData("Samsung Health · Health Connect",listOf(HealthDay(date,mapOf("sleepMinutes" to 60.0,"sleepCoverage" to 50.0),listOf(SleepStage("Asleep",60.0),SleepStage("Unknown",60.0)),listOf(EnergySample(25.0,durationMinutes=30.0,recordedAt=start)))),start,report=SyncReport(setOf("read_sleep"),true,date.minusDays(364),true,start,listOf("Samsung Watch")))
        assertEquals(data,HealthJson.decode(HealthJson.encode(data)))
    }
    @Test fun sleepDayCanHaveTwentyThreeOrTwentyFiveHours() {
        val zone=ZoneId.of("America/New_York")
        val spring=LocalDate.of(2026,3,8);val autumn=LocalDate.of(2026,11,1)
        assertEquals(23L,Duration.between(spring.atStartOfDay(zone),spring.plusDays(1).atStartOfDay(zone)).toHours())
        assertEquals(25L,Duration.between(autumn.atStartOfDay(zone),autumn.plusDays(1).atStartOfDay(zone)).toHours())
    }
}
