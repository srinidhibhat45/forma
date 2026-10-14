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
    private fun <T> aggregateParameter(request:Any,key:String):T =
        request.javaClass.getDeclaredField(key).apply { isAccessible=true }.get(request) as T
    private fun step(at:Instant)=StepsRecord(at.minusSeconds(60),null,at,null,100,Metadata.manualEntry())
    private fun reader(changes:Map<String,ChangesResponse> = emptyMap(),records:List<Record> = emptyList(),onRead:(ReadRecordsRequest<*>)->Unit={},onHours:(AggregateGroupByDurationRequest)->List<AggregationResultGroupedByDuration> = {emptyList()},onAggregate:(AggregateRequest)->AggregationResult = {AggregationResult(emptyMap(),emptyMap(),emptySet())},onDays:(AggregateGroupByPeriodRequest)->List<AggregationResultGroupedByPeriod> = {emptyList()}):HealthReader {
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
                "aggregateGroupByPeriod"->onDays(args!![0] as AggregateGroupByPeriodRequest)
                "aggregate"->{val request=args!![0] as AggregateRequest;assertEquals(setOf(origin),aggregateParameter<Set<DataOrigin>>(request,"dataOriginFilter"));onAggregate(request)}
                "readRecords"->{
                    val request=args!![0] as ReadRecordsRequest<*>;onRead(request)
                    assertEquals(setOf(origin),request.dataOriginFilter)
                    val selected=records.filter { request.recordType.isInstance(it) }.filter { r->
                        val at=when(r) { is SleepSessionRecord->r.endTime;is HeartRateRecord->r.endTime;is ExerciseSessionRecord->r.endTime;is NutritionRecord->r.startTime;is StepsRecord->r.endTime;is RestingHeartRateRecord->r.time;else->now }
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
        val result=reader(records=listOf<Record>(sleep,steps,exercise,resting),onHours={ request->
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
        val day=reader(records=listOf<Record>(sleep,exercise,resting)).sync().days.first { it.date==date }
        assertEquals(10.0,day.samples.single().activeMinutes!!,.001)
        assertNull(day.samples.single().steps);assertNull(day.samples.single().heartRate)
        val previous=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        val energy=Scores.energy(day,previous,480)
        assertEquals(1.6,energy.points.first().second-energy.points.last().second,.001)
    }
    private fun night(date:LocalDate,wakingMinute:Int):SleepSessionRecord {
        val end=date.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(wakingMinute*60L);val start=end.minusSeconds(8*3600)
        return SleepSessionRecord(start,null,end,null,Metadata.manualEntry(),stages=listOf(SleepSessionRecord.Stage(start,end,SleepSessionRecord.STAGE_TYPE_SLEEPING)))
    }
    private fun totals(steps:Long?=null,heart:Long?=null)=AggregationResult(buildMap {
        steps?.let { put(StepsRecord.COUNT_TOTAL.metricKey,it) };heart?.let { put(HeartRateRecord.BPM_AVG.metricKey,it) }
    },emptyMap(),setOf(origin))
    @Test fun emptyHourlySummariesUseExactPostWakeWindowsWithoutSpreadingDailySteps()=runBlocking {
        val date=today.minusDays(1);val sleep=night(date,21*60+20);val wake=sleep.endTime
        val end=date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val windows=HealthQuality.postWakeWindows(wake,end);val queries=mutableListOf<Pair<Instant,Instant>>()
        val daily=AggregationResultGroupedByPeriod(totals(steps=9000),date.atStartOfDay(),date.plusDays(1).atStartOfDay())
        val day=reader(records=listOf<Record>(sleep,step(wake.plusSeconds(600))),onDays={listOf(daily)},onAggregate={ request->
            val range=aggregateParameter<TimeRangeFilter>(request,"timeRangeFilter");val from=range.startTime!!;val to=range.endTime!!
            queries.add(from to to);assertTrue(from>=wake);assertTrue(to<=end)
            when {
                from==wake && to==end->totals(steps=1600,heart=100)
                from==windows[0].first->totals(steps=1000,heart=100)
                from==windows[1].first->totals(steps=600,heart=90)
                else->totals()
            }
        }).sync().days.first { it.date==date }
        assertEquals(9000.0,day["steps"]!!,.001)
        assertEquals(1600.0,day.samples.sumOf { it.steps ?: 0.0 },.001);assertEquals(2,day.samples.size)
        assertEquals(4,queries.size);assertEquals(4,day.energyRead!!.exactQueries)
        assertEquals(1600.0,day.energyRead!!.exactPostWakeSteps!!,.001)
        assertEquals(windows[1].second,day.samples.last().recordedAt)
        assertEquals(day,HealthJson.decode(HealthJson.encode(HealthData("Samsung Health · Health Connect",listOf(day)))).days.single())
    }
    @Test fun onePartialWindowReusesTheExactProbeAndScalesHeartLoad()=runBlocking {
        val date=today.minusDays(1);val sleep=night(date,23*60+40);val wake=sleep.endTime
        var queries=0
        val day=reader(records=listOf<Record>(sleep,RestingHeartRateRecord(wake,null,60,Metadata.manualEntry())),onAggregate={ request->
            val range=aggregateParameter<TimeRangeFilter>(request,"timeRangeFilter")
            assertEquals(wake,range.startTime);queries++;totals(heart=120)
        }).sync().days.first { it.date==date }
        assertEquals(1,queries);assertEquals(20.0,day.samples.single().durationMinutes,.001)
        val prior=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        val energy=Scores.energy(day,prior,480)
        assertEquals(.7,energy.points.first().second-energy.points.last().second,.001)
    }
    @Test fun dailyStepsAndOvernightHeartNeverBecomePostWakeActivity()=runBlocking {
        val date=today.minusDays(1);val sleep=night(date,8*60+20);val wake=sleep.endTime
        val at=wake.minusSeconds(300)
        val heart=HeartRateRecord(at.minusSeconds(1),null,at.plusSeconds(1),null,listOf(HeartRateRecord.Sample(at,80)),Metadata.manualEntry())
        val daily=AggregationResultGroupedByPeriod(totals(steps=1200),date.atStartOfDay(),date.plusDays(1).atStartOfDay())
        var queries=0
        val day=reader(records=listOf<Record>(sleep,heart,step(at)),onDays={listOf(daily)},onAggregate={queries++;totals()}).sync().days.first { it.date==date }
        assertEquals(1200.0,day["steps"]!!,.001);assertTrue(day.samples.isEmpty());assertEquals(1,queries)
        assertEquals(0,day.energyRead!!.rawHeartMinutes);assertNull(day.energyRead!!.latestRawHeartAt)
        val ready=day.copy(values=day.values+("restingHeartRate" to 60.0))
        val prior=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        assertNull(Scores.energy(ready,prior,480).score.value)
    }
    @Test fun timestampedHeartFallbackUsesObservedMinutesAndTheActualLatestReading()=runBlocking {
        val date=today.minusDays(1);val wake=night(date,8*60+20).endTime.plusSeconds(30);val start=wake.minusSeconds(8*3600)
        val sleep=SleepSessionRecord(start,null,wake,null,Metadata.manualEntry(),stages=listOf(SleepSessionRecord.Stage(start,wake,SleepSessionRecord.STAGE_TYPE_SLEEPING)))
        val last=wake.plusSeconds(130)
        val heart=HeartRateRecord(wake.minusSeconds(60),null,last.plusSeconds(1),null,listOf(
            HeartRateRecord.Sample(wake.minusSeconds(30),200),HeartRateRecord.Sample(wake.plusSeconds(25),104),
            HeartRateRecord.Sample(wake.plusSeconds(35),120),HeartRateRecord.Sample(last,80)),Metadata.manualEntry())
        val day=reader(records=listOf<Record>(sleep,heart,RestingHeartRateRecord(wake,null,60,Metadata.manualEntry()))).sync().days.first { it.date==date }
        val sample=day.samples.single()
        assertEquals(96.0,sample.heartRate!!,.001);assertEquals(2.0,sample.durationMinutes,.001);assertEquals(last,sample.recordedAt)
        assertEquals(Duration.between(date.atStartOfDay(ZoneId.systemDefault()).toInstant(),last).toMillis()/3600000.0,sample.hour,.00001)
        assertNull(sample.steps);assertNull(sample.activeMinutes)
        assertEquals(2,day.energyRead!!.rawHeartMinutes);assertEquals(1,day.energyRead!!.rawHeartFallbackBuckets)
        assertFalse(day.energyRead!!.stepsAllowed);assertEquals(0,day.energyRead!!.exactQueries)
        val prior=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("restingHeartRate" to 60.0)) }
        val energy=Scores.energy(day,prior,480)
        assertEquals(.042,energy.points.first().second-energy.points.last().second,.00001);assertEquals(last,energy.recordedAt)
    }
    @Test fun overlappingOrWrongLengthSummaryIsRejectedInsteadOfBeingAssignedToWake()=runBlocking {
        val date=today.minusDays(1);val sleep=night(date,8*60+20);val wake=sleep.endTime
        val day=reader(records=listOf<Record>(sleep,step(wake.minusSeconds(600))),onHours={ request->
            if(aggregateParameter<TimeRangeFilter>(request,"timeRangeFilter").startTime==wake)listOf(
                AggregationResultGroupedByDuration(totals(steps=1000,heart=180),wake.minusSeconds(1200),wake.plusSeconds(2400),ZoneOffset.UTC),
                AggregationResultGroupedByDuration(totals(steps=500,heart=140),wake,wake.plusSeconds(300),ZoneOffset.UTC)
            )else emptyList()
        }).sync().days.first { it.date==date }
        assertTrue(day.samples.isEmpty());assertEquals(2,day.energyRead!!.returnedBuckets)
        assertEquals(0,day.energyRead!!.matchedBuckets);assertEquals(1,day.energyRead!!.exactQueries)
    }
    @Test fun sparseValidSummariesTakePriorityOverRawHeartAndNeedNoExactRetries()=runBlocking {
        val date=today.minusDays(1);val sleep=night(date,8*60+20);val wake=sleep.endTime;val from=wake.plusSeconds(3600)
        val heart=HeartRateRecord(from.plusSeconds(60),null,from.plusSeconds(120),null,listOf(HeartRateRecord.Sample(from.plusSeconds(90),180)),Metadata.manualEntry())
        val day=reader(records=listOf<Record>(sleep,step(from.plusSeconds(600)),heart),onHours={ request->
            if(aggregateParameter<TimeRangeFilter>(request,"timeRangeFilter").startTime==wake)listOf(
                AggregationResultGroupedByDuration(totals(steps=800,heart=100),from,from.plusSeconds(3600),ZoneOffset.UTC)
            )else emptyList()
        },onAggregate={error("Valid sparse summaries must not be retried")}).sync().days.first { it.date==date }
        assertEquals(800.0,day.samples.single().steps!!,.001);assertEquals(100.0,day.samples.single().heartRate!!,.001)
        assertEquals(0,day.energyRead!!.exactQueries);assertEquals(0,day.energyRead!!.rawHeartFallbackBuckets)
    }
    @Test fun olderCacheVersionRebuildsAllowedHistoryOnceAndCurrentVersionUsesRecentReads()=runBlocking {
        val granted=setOf(HealthPermission.getReadPermission(SleepSessionRecord::class),HealthPermission.getReadPermission(HeartRateRecord::class))
        val old=HealthData("Samsung Health · Health Connect",listOf(HealthDay(today.minusDays(10),mapOf("sleepMinutes" to 480.0))),now,report=SyncReport(granted,scannedFrom=today.minusDays(28),historyComplete=true,dataVersion=2))
        val starts=mutableListOf<Instant>()
        val upgraded=reader(onRead={starts.add(it.timeRangeFilter.startTime!!)}).sync(old)
        assertEquals(4,upgraded.report!!.dataVersion)
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
