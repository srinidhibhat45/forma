package com.forma.health

import androidx.health.connect.client.*
import androidx.health.connect.client.changes.*
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.response.*
import androidx.health.connect.client.aggregate.*
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.*

/** Exercises the real reader with SDK responses, without implying a physical Samsung sync was tested. */
class HealthReaderIntegrationTest {
    private val now=Instant.now()
    private val today=LocalDate.now()
    private val origin=DataOrigin("com.sec.android.app.shealth")
    // SDK 1.1 hides aggregation-request accessors; inspect only our injected test requests.
    @Suppress("UNCHECKED_CAST")
    private fun <T> aggregateParameter(request:AggregateGroupByDurationRequest,key:String):T =
        request.javaClass.getDeclaredField(key).apply { isAccessible=true }.get(request) as T
    private fun step(at:Instant)=StepsRecord(at.minusSeconds(60),null,at,null,100,Metadata.manualEntry())
    private fun reader(changes:Map<String,ChangesResponse> = emptyMap(),records:List<Record> = emptyList(),onRead:(ReadRecordsRequest<*>)->Unit={},onHours:(AggregateGroupByDurationRequest)->List<AggregationResultGroupedByDuration> = {emptyList()}):HealthReader {
        val permissions=setOf(HealthPermission.getReadPermission(SleepSessionRecord::class),HealthPermission.getReadPermission(HeartRateRecord::class))+records.map { HealthPermission.getReadPermission(it::class) }
        val controller=object:PermissionController {
            override suspend fun getGrantedPermissions()=permissions
            override suspend fun revokeAllPermissions()=Unit
        }
        val features=object:HealthConnectFeatures { override fun getFeatureStatus(feature:Int)=HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE }
        val fake=Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader,arrayOf(HealthConnectClient::class.java)) { _,method,args->
            when(method.name) {
                "getPermissionController"->controller
                "getFeatures"->features
                "getChanges"->changes.getValue(args!![0] as String)
                "getChangesToken"->{ val request=args!![0] as ChangesTokenRequest;assertEquals(setOf(origin),request.dataOriginFilters);"checkpoint" }
                "aggregateGroupByDuration"->{val request=args!![0] as AggregateGroupByDurationRequest;assertEquals(setOf(origin),aggregateParameter<Set<DataOrigin>>(request,"dataOriginFilter"));onHours(request)}
                "aggregateGroupByPeriod"->emptyList<Any>()
                "aggregate"->AggregationResult(emptyMap(),emptyMap(),emptySet())
                "readRecords"->{
                    val request=args!![0] as ReadRecordsRequest<*>;onRead(request)
                    assertEquals(setOf(origin),request.dataOriginFilter)
                    val selected=records.filter { request.recordType.isInstance(it) }.filter { r->
                        val at=when(r) { is SleepSessionRecord->r.endTime;is HeartRateRecord->r.endTime;is ExerciseSessionRecord->r.endTime;is NutritionRecord->r.startTime;else->now }
                        val range=request.timeRangeFilter;at>=range.startTime!! && at<range.endTime!!
                    }
                    // An empty final token is permitted by the API and must not trigger another read.
                    ReadRecordsResponse(selected,"")
                }
                else->error("Unexpected SDK call: ${method.name}")
            }
        } as HealthConnectClient
        return HealthReader(InstrumentationRegistry.getInstrumentation().targetContext,fake)
    }
    @Test fun aWalkInTheWakingHourReachesEnergyWithoutIncludingPreWakeMeasurements()=runBlocking {
        val date=today.minusDays(1);val midnight=date.atStartOfDay(ZoneId.systemDefault()).toInstant();val wake=midnight.plusSeconds(8*3600+20*60)
        val start=wake.minusSeconds(8*3600)
        val sleep=SleepSessionRecord(start,null,wake,null,Metadata.manualEntry(),stages=listOf(SleepSessionRecord.Stage(start,wake,SleepSessionRecord.STAGE_TYPE_SLEEPING)))
        val steps=StepsRecord(wake.plusSeconds(300),null,wake.plusSeconds(1200),null,2000,Metadata.manualEntry())
        val exercise=ExerciseSessionRecord(wake.plusSeconds(300),null,wake.plusSeconds(1200),null,Metadata.manualEntry(),ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        val resting=RestingHeartRateRecord(wake,null,60,Metadata.manualEntry())
        var postWakeQueries=0
        val result=reader(records=listOf(sleep,steps,exercise,resting),onHours={ request->
            val range=aggregateParameter<TimeRangeFilter>(request,"timeRangeFilter")
            if(range.startTime==wake) {
                postWakeQueries++;assertEquals(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),range.endTime)
                listOf(AggregationResultGroupedByDuration(AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 2000L,HeartRateRecord.BPM_AVG.metricKey to 110L),emptyMap(),setOf(origin)),wake,wake.plusSeconds(3600),ZoneOffset.UTC))
            }else emptyList()
        }).sync()
        val day=result.days.first { it.date==date };val sample=day.samples.single()
        assertEquals(1,postWakeQueries);assertEquals(2000.0,sample.steps!!,.001);assertEquals(15.0,sample.activeMinutes!!,.001)
        assertEquals(110.0,sample.heartRate!!,.001);assertEquals(wake.plusSeconds(3600),sample.recordedAt)
        val previous=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        val energy=Scores.energy(day,previous,480)
        assertEquals(3.0,energy.points.first().second-energy.points.last().second,.001)
    }
    @Test fun recordedExerciseCanUpdateEnergyEvenWhenHourlyHeartAndStepsAreNotShared()=runBlocking {
        val date=today.minusDays(1);val wake=date.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(8*3600+20*60)
        val start=wake.minusSeconds(8*3600)
        val sleep=SleepSessionRecord(start,null,wake,null,Metadata.manualEntry(),stages=listOf(SleepSessionRecord.Stage(start,wake,SleepSessionRecord.STAGE_TYPE_SLEEPING)))
        val exercise=ExerciseSessionRecord(wake.plusSeconds(300),null,wake.plusSeconds(900),null,Metadata.manualEntry(),ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        val resting=RestingHeartRateRecord(wake,null,60,Metadata.manualEntry())
        val day=reader(records=listOf(sleep,exercise,resting)).sync().days.first { it.date==date }
        assertEquals(10.0,day.samples.single().activeMinutes!!,.001)
        assertNull(day.samples.single().steps);assertNull(day.samples.single().heartRate)
        val previous=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        val energy=Scores.energy(day,previous,480)
        assertEquals(1.6,energy.points.first().second-energy.points.last().second,.001)
    }
    @Test fun olderCacheVersionRebuildsAllowedHistoryOnceAndCurrentVersionUsesRecentReads()=runBlocking {
        val granted=setOf(HealthPermission.getReadPermission(SleepSessionRecord::class),HealthPermission.getReadPermission(HeartRateRecord::class))
        val old=HealthData("Samsung Health · Health Connect",listOf(HealthDay(today.minusDays(10),mapOf("sleepMinutes" to 480.0))),now,report=SyncReport(granted,scannedFrom=today.minusDays(28),historyComplete=true,dataVersion=2))
        val starts=mutableListOf<Instant>()
        val upgraded=reader(onRead={starts.add(it.timeRangeFilter.startTime!!)}).sync(old)
        assertEquals(3,upgraded.report!!.dataVersion)
        assertTrue(starts.min()<=today.minusDays(28).atStartOfDay(ZoneId.systemDefault()).toInstant())
        starts.clear();reader(onRead={starts.add(it.timeRangeFilter.startTime!!)}).sync(upgraded)
        assertTrue(starts.min()>=today.minusDays(3).atStartOfDay(ZoneId.systemDefault()).toInstant())
    }
    @Test fun changedRecentRecordsAvoidHistoricalScanButLaterPagesStillReconcileOldCorrections()=runBlocking {
        val first=ChangesResponse(listOf(UpsertionChange(step(now.minusSeconds(600)))),"next",true,false)
        val next=ChangesResponse(listOf(UpsertionChange(step(now.minus(Duration.ofDays(10))))),"end",false,false)
        assertEquals(ChangeCheck(true,true),reader(mapOf("start" to first,"next" to next)).changes("start"))
        assertEquals(ChangeCheck(true,false),reader(mapOf("start" to first,"next" to ChangesResponse(emptyList(),"end",false,false))).changes("start"))
    }
    @Test fun deletedAndExpiredChangesRequireReconciliation()=runBlocking {
        assertTrue(reader(mapOf("start" to ChangesResponse(listOf(DeletionChange("removed")),"end",false,false))).changes("start").historyChanged)
        assertTrue(reader(mapOf("start" to ChangesResponse(emptyList(),"end",false,true))).changes("start").historyChanged)
        assertEquals(ChangeCheck(false,false),reader(mapOf("start" to ChangesResponse(emptyList(),"end",false,false))).changes("start"))
    }
    @Test fun aCompletedEmptyReconciliationRemovesTheFinalDeletedCachedDay()=runBlocking {
        val old=HealthData("Samsung Health · Health Connect",listOf(HealthDay(today.minusDays(10),mapOf("steps" to 4000.0))),now.minusSeconds(86400))
        var saved:HealthData?=null
        val result=reader().sync(old,fullHistory=true,onData={saved=it})
        assertEquals(result,saved);assertTrue(result.days.all { it.values.isEmpty() });assertNull(result.report!!.latestRecordAt)
    }
    @Test fun sessionsAndNutritionAreRetainedWithoutInventingUnsharedTotals()=runBlocking {
        val start=now.minusSeconds(3600).minusNanos(now.nano.toLong()).let { it.minusSeconds(it.epochSecond%60) }
        val end=start.plusSeconds(1800)
        val exercise=ExerciseSessionRecord(start,null,end,null,Metadata.manualEntry(),ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,title="Evening run")
        val heart=HeartRateRecord(start,null,end,null,listOf(HeartRateRecord.Sample(start.plusSeconds(60),110),HeartRateRecord.Sample(start.plusSeconds(1200),140)),Metadata.manualEntry())
        val food=NutritionRecord(startTime=start,startZoneOffset=null,endTime=start.plusSeconds(60),endZoneOffset=null,metadata=Metadata.manualEntry(),protein=androidx.health.connect.client.units.Mass.grams(25.0))
        val result=reader(records=listOf<Record>(exercise,exercise,heart,food)).sync()
        val day=result.days.first { it.date==start.atZone(ZoneId.systemDefault()).toLocalDate() };val w=day.recordedWorkouts.single()
        assertEquals("Evening run",w.title);assertEquals(30.0,w.minutes,.001);assertEquals(2,w.heart.size);assertEquals(125.0,w.values["heartRateAverage"]!!,.001)
        assertNull(w.values["distanceKm"]);assertEquals(25.0,day["proteinGrams"]!!,.001);assertNull(day["dietaryCalories"])
        assertEquals(30.0,day["activeMinutes"]!!,.001)
    }
    @Test fun genericSleepAndCurrentHeartReachDifferentFieldsWithoutInventedBaselines()=runBlocking {
        val wake=today.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(3600)
        val start=wake.minusSeconds(8*3600)
        val night=SleepSessionRecord(start,null,wake,null,Metadata.manualEntry(),stages=listOf(SleepSessionRecord.Stage(start,wake,SleepSessionRecord.STAGE_TYPE_SLEEPING)))
        val heartAt=now.minusSeconds(60)
        val heart=HeartRateRecord(heartAt.minusSeconds(1),null,heartAt.plusSeconds(1),null,listOf(HeartRateRecord.Sample(heartAt,88)),Metadata.manualEntry())
        var pages=0
        val result=reader(records=listOf<Record>(night,heart),onRead={pages++}).sync()
        val day=result.days.first { it.date==today }
        assertEquals(480.0,day["sleepMinutes"]!!,.001);assertEquals("Asleep",day.stages.single().stage)
        assertEquals(88.0,day["heartRateLatest"]!!,.001);assertEquals(heartAt,day.heartRecordedAt)
        assertNull(day["sleepHeartRate"]);assertNull(day["hrv"])
        assertTrue(result.report!!.historyComplete);assertTrue(pages<20)
    }
}
