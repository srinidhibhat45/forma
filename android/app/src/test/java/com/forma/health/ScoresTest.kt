package com.forma.health
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class ScoresTest {
    private val date=LocalDate.of(2026,10,13)
    private val previous=listOf(HealthDay(date.minusDays(1),mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)))
    @Test fun noInventedSleep(){assertNull(Scores.sleep(HealthDay(date),480).value)}
    @Test fun noRecoveryFromSleepAlone(){assertNull(Scores.recovery(HealthDay(date,mapOf("sleepMinutes" to 480.0)),emptyList(),480).value)}
    @Test fun noEnergyWithoutSamples(){assertNull(Scores.energy(HealthDay(date,mapOf("sleepMinutes" to 480.0,"hrv" to 60.0)),previous,480).score.value)}
    @Test fun documentedSleepWeights(){assertEquals(91,Scores.sleep(HealthDay(date,mapOf("sleepMinutes" to 468.0,"sleepEfficiency" to 90.0,"sleepRegularity" to 75.0)),480).value)}
    @Test fun missingInputsReduceCoverage(){val score=Scores.sleep(HealthDay(date,mapOf("sleepMinutes" to 240.0)),480);assertEquals(50,score.value);assertEquals("Limited",score.confidence);assertEquals(1,score.factors.size)}
    @Test fun baselineExcludesCurrentFutureAndOldRecords(){val history=listOf(HealthDay(date.minusDays(29),mapOf("hrv" to 200.0)),HealthDay(date.minusDays(2),mapOf("hrv" to 50.0)),HealthDay(date.minusDays(1),mapOf("hrv" to 60.0)),HealthDay(date,mapOf("hrv" to 300.0)),HealthDay(date.plusDays(1),mapOf("hrv" to 300.0)));assertEquals(55.0,Scores.baseline(history,date,"hrv")!!,.001)}
    @Test fun activityDepletesRecordedRestReplenishes(){val day=HealthDay(date,mapOf("sleepMinutes" to 480.0,"hrv" to 60.0,"restingHeartRate" to 60.0),samples=listOf(EnergySample(7.0,steps=4000.0,activeMinutes=30.0,heartRate=120.0),EnergySample(8.0,restMinutes=60.0)));val e=Scores.energy(day,previous,480);assertTrue(e.points[1].second<e.points[0].second);assertTrue(e.points[2].second>e.points[1].second)}
    @Test fun energyStaysBounded(){val day=HealthDay(date,mapOf("sleepMinutes" to 480.0,"hrv" to 60.0,"restingHeartRate" to 60.0),samples=(7..23).map{EnergySample(it.toDouble(),steps=15000.0,activeMinutes=60.0,heartRate=200.0)});val e=Scores.energy(day,previous,480);assertTrue(e.points.all{it.second in 0.0..100.0});assertEquals(0,e.score.value)}
    @Test fun goalAdjustsSleepEstimate(){val day=HealthDay(date,mapOf("sleepMinutes" to 468.0));assertTrue(Scores.sleep(day,540).value!!<Scores.sleep(day,480).value!!)}
    @Test fun sleepingHeartRateFallbackUsesRecordedBaseline(){val days=listOf(HealthDay(date.minusDays(1),mapOf("sleepHeartRate" to 60.0)));val day=HealthDay(date,mapOf("sleepMinutes" to 480.0,"sleepHeartRate" to 58.0));val score=Scores.recovery(day,days,480);assertNotNull(score.value);assertTrue(score.factors.any{it.label=="Sleeping heart rate"});assertFalse(score.factors.any{it.label=="Resting heart rate"})}
    @Test fun calendarWindowDoesNotStretchOverMissingDays(){val days=listOf(HealthDay(date.minusDays(20),mapOf("steps" to 4000.0)),HealthDay(date,mapOf("steps" to 8000.0)));val window=calendarDays(days,date,7);assertEquals(7,window.size);assertEquals(date.minusDays(6),window.first().date);assertNull(window.first()["steps"]);assertEquals(8000.0,window.last()["steps"]!!,.001)}
    @Test fun sparseBaselineDoesNotClaimModerateCoverage(){val days=(1..15).map{HealthDay(date.minusDays(it.toLong()),if(it==1)mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)else emptyMap())};val d=HealthDay(date,mapOf("sleepMinutes" to 480.0,"hrv" to 60.0,"restingHeartRate" to 60.0));assertEquals("Limited",Scores.recovery(d,days,480).confidence)}
}
