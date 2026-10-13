package com.forma.health

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HealthAnalyticsTest {
    private val date=LocalDate.of(2026,10,13)
    private val at=date.atStartOfDay(ZoneOffset.UTC).toInstant()
    @Test fun bedtimeWrapsAtMidnightAndMissingNightsDoNotBecomeDebt() {
        val days=listOf(HealthDay(date,mapOf("sleepMinutes" to 450.0),sleepStart=at.minusSeconds(300),sleepEnd=at.plusSeconds(7*3600)),HealthDay(date.minusDays(1),mapOf("sleepMinutes" to 490.0),sleepStart=at.minusSeconds(86400).plusSeconds(300),sleepEnd=at.minusSeconds(86400).plusSeconds(7*3600)))
        val p=HealthAnalytics.sleepPattern(days,date,480,ZoneOffset.UTC)
        assertEquals(2,p.nights);assertEquals(470.0,p.average!!,.001);assertEquals(30.0,p.shortfall!!,.001)
        assertEquals("00:00",sleepClockLabel(p.bedtime));assertNull(p.bedtimeSpread);assertEquals(1,p.goalNights)
    }
    @Test fun incompleteNightsAreExcludedFromPatterns() {
        val p=HealthAnalytics.sleepPattern(listOf(HealthDay(date,mapOf("sleepMinutes" to 200.0,"sleepCoverage" to 60.0))),date,480)
        assertEquals(0,p.nights);assertNull(p.average);assertNull(p.shortfall)
    }
    @Test fun goalStreakRequiresConsecutiveRecordedDates() {
        val days=listOf(HealthDay(date,mapOf("steps" to 11000.0)),HealthDay(date.minusDays(1),mapOf("steps" to 10000.0)),HealthDay(date.minusDays(3),mapOf("steps" to 14000.0)))
        val p=HealthAnalytics.week(days,date,Preferences());assertEquals(2,p.streak);assertEquals(3,p.days);assertEquals(35000.0,p.steps!!,.001);assertNull(p.exercise)
    }
    @Test fun observedHeartBandsDoNotFillGapsOrDoubleCountSameMinute() {
        val w=RecordedWorkout("one",56,"Run",at,at.plusSeconds(3600),heart=listOf(HeartPoint(at,90.0),HeartPoint(at.plusSeconds(10),91.0),HeartPoint(at.plusSeconds(1800),150.0)))
        assertEquals(listOf(1,0,0,1,0),HealthAnalytics.heartBands(w));assertNull(HealthAnalytics.pace(w))
    }
    @Test fun sessionsAndHourlyActivitySurviveExportWithoutChangingLegacyRecords() {
        val w=RecordedWorkout("one",56,"Run",at,at.plusSeconds(1800),mapOf("distanceKm" to 3.0,"heartRateAverage" to 120.0),listOf(HeartPoint(at.plusSeconds(60),120.0)))
        val day=HealthDay(date,mapOf("steps" to 1000.0,"proteinGrams" to 90.0),activity=listOf(ActivityHour(at,1000.0,40.0,30.0)),recordedWorkouts=listOf(w))
        val data=HealthData("Samsung Health · Health Connect",listOf(day),at,report=SyncReport())
        assertEquals(data,HealthJson.decode(HealthJson.encode(data)))
        assertEquals(10.0,HealthJson.decode("""{"version":1,"days":[{"date":"2026-10-13","steps":10}]}""").days.single()["steps"]!!,.001)
        assertEquals(10.0,HealthAnalytics.pace(w)!!,.001)
    }
    @Test fun infrequentBodyValuesAreDatedAndNeverTakenFromTheFuture() {
        val days=listOf(HealthDay(date.minusDays(10),mapOf("weightKg" to 72.0)),HealthDay(date.plusDays(1),mapOf("weightKg" to 73.0)))
        assertEquals(date.minusDays(10) to 72.0,HealthAnalytics.latest(days,date,"weightKg"));assertNull(HealthAnalytics.latest(days,date.minusDays(11),"weightKg"))
    }
    @Test fun additionalVitalBaselinesUsePriorDaysAndFiniteUnitSpecificVariability() {
        val days=(1..7).map { HealthDay(date.minusDays(it.toLong()),mapOf("respiratoryRate" to 14.0,"skinTemperatureDelta" to -.1)) }+HealthDay(date,mapOf("respiratoryRate" to 20.0))
        assertEquals(14.0,PersonalSignals.baseline(days,date,"respiratoryRate")!!.typical,.001)
        assertEquals(.15,PersonalSignals.baseline(days,date,"skinTemperatureDelta")!!.spread,.001)
        assertNull(PersonalSignals.baseline(days.drop(1),date,"respiratoryRate"))
    }
    @Test fun duplicateWorkoutIdsAreCountedOnceAcrossHistory() {
        val w=RecordedWorkout("one",56,"Run",at,at.plusSeconds(1800))
        assertEquals(1,HealthAnalytics.workouts(listOf(HealthDay(date,recordedWorkouts=listOf(w,w))),date,7).size)
    }
}
