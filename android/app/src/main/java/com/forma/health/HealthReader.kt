package com.forma.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.*
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import java.time.*
import kotlin.reflect.KClass

/** Reads Samsung's shared records, never claims a direct Bluetooth or live sensor connection. */
data class ChangeCheck(val changed:Boolean,val historyChanged:Boolean=false)

class HealthReader(private val context:Context,private val injectedClient:HealthConnectClient?=null) {
    val client by lazy { injectedClient ?: HealthConnectClient.getOrCreate(context) }
    private val samsung=setOf(DataOrigin("com.sec.android.app.shealth"))
    val status get()=if(injectedClient!=null)HealthConnectClient.SDK_AVAILABLE else HealthConnectClient.getSdkStatus(context)
    private fun feature(id:Int)=status==HealthConnectClient.SDK_AVAILABLE && client.features.getFeatureStatus(id)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    private fun recordTypes():List<KClass<out Record>> {
        val types=mutableListOf<KClass<out Record>>(StepsRecord::class,DistanceRecord::class,ActiveCaloriesBurnedRecord::class,ExerciseSessionRecord::class,FloorsClimbedRecord::class,HeartRateRecord::class,RestingHeartRateRecord::class,HeartRateVariabilityRmssdRecord::class,SleepSessionRecord::class,OxygenSaturationRecord::class,RespiratoryRateRecord::class,WeightRecord::class,BodyFatRecord::class,LeanBodyMassRecord::class,BloodPressureRecord::class,BloodGlucoseRecord::class,TotalCaloriesBurnedRecord::class,Vo2MaxRecord::class,PowerRecord::class,SpeedRecord::class,BasalMetabolicRateRecord::class,HeightRecord::class,NutritionRecord::class,HydrationRecord::class)
        if(feature(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE))types.add(SkinTemperatureRecord::class)
        return types
    }
    fun permissions()=recordTypes().map { HealthPermission.getReadPermission(it) }.toSet()
    suspend fun changeScope()=client.permissionController.getGrantedPermissions().intersect(connectionPermissions()).sorted().joinToString("|")
    suspend fun changeToken():String {
        val granted=client.permissionController.getGrantedPermissions()
        val types=recordTypes().filter { HealthPermission.getReadPermission(it) in granted }.toSet()
        check(types.isNotEmpty()) { "Allow read access first." }
        return client.getChangesToken(ChangesTokenRequest(types,dataOriginFilters=samsung))
    }
    /** Do not consume a changed checkpoint until a replacement snapshot finishes successfully. */
    private fun recordTime(record:Record,now:Instant):Instant?=when(record) { is TotalCaloriesBurnedRecord->record.endTime;is SpeedRecord->record.samples.filter { it.time<=now }.maxOfOrNull { it.time };is PowerRecord->record.samples.filter { it.time<=now }.maxOfOrNull { it.time };is Vo2MaxRecord->record.time;is BasalMetabolicRateRecord->record.time;is HeightRecord->record.time;is SleepSessionRecord->record.endTime;is HeartRateRecord->record.samples.filter { it.time<=now }.maxOfOrNull { it.time };is StepsRecord->record.endTime
                is NutritionRecord->record.endTime;is HydrationRecord->record.endTime;is DistanceRecord->record.endTime;is ActiveCaloriesBurnedRecord->record.endTime;is FloorsClimbedRecord->record.endTime;is ExerciseSessionRecord->record.endTime;is SkinTemperatureRecord->record.deltas.filter { it.time<=now }.maxOfOrNull { it.time }
                is RestingHeartRateRecord->record.time;is HeartRateVariabilityRmssdRecord->record.time;is OxygenSaturationRecord->record.time;is RespiratoryRateRecord->record.time;is WeightRecord->record.time;is BodyFatRecord->record.time;is LeanBodyMassRecord->record.time;is BloodPressureRecord->record.time;is BloodGlucoseRecord->record.time;else->null }
    suspend fun changes(token:String):ChangeCheck {
        var cursor=token;var changed=false;var history=false
        val now=Instant.now();val recent=LocalDate.now().minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()
        repeat(100) {
            val response=client.getChanges(cursor)
            if(response.changesTokenExpired)return ChangeCheck(true,true)
            response.changes.forEach { change->
                changed=true
                val record=(change as? androidx.health.connect.client.changes.UpsertionChange)?.record
                // A deleted record has no timestamp. A full reconciliation removes its cached measurements.
                val at=when(record) {
                    is NutritionRecord->record.startTime;is HydrationRecord->record.startTime;is TotalCaloriesBurnedRecord->record.startTime;is PowerRecord->record.startTime;is SpeedRecord->record.startTime;is SleepSessionRecord->record.startTime;is StepsRecord->record.startTime
                    is HeartRateRecord->record.startTime;is SkinTemperatureRecord->record.startTime
                    is ExerciseSessionRecord->record.startTime;is DistanceRecord->record.startTime
                    is ActiveCaloriesBurnedRecord->record.startTime;is FloorsClimbedRecord->record.startTime
                    else->record?.let { recordTime(it,now) }
                }
                if(at==null || at<recent)history=true
            }
            if(!response.hasMore)return ChangeCheck(changed,history)
            check(response.nextChangesToken!=cursor) { "Health Connect change pagination stalled." }
            cursor=response.nextChangesToken
        }
        return ChangeCheck(true,true)
    }
    fun connectionPermissions()=permissions()+if(feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY))setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)else emptySet()
    suspend fun connection():ConnectionState {
        val available=status==HealthConnectClient.SDK_AVAILABLE
        val installed=runCatching { context.packageManager.getApplicationInfo("com.sec.android.app.shealth",0).enabled }.getOrDefault(false)
        val granted=if(available)client.permissionController.getGrantedPermissions()else emptySet()
        return ConnectionState(available,installed,granted.intersect(connectionPermissions()+HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND),
            feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY),HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted,
            feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND),HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted)
    }
    suspend fun sync(existing:HealthData?=null,fullHistory:Boolean=false,onProgress:(SyncProgress)->Unit={},onData:suspend(HealthData)->Unit={}):HealthData {
        check(status==HealthConnectClient.SDK_AVAILABLE) { "Install or update Health Connect to read Samsung Health records." }
        val granted=client.permissionController.getGrantedPermissions().intersect(connectionPermissions())
        check(granted.any { it in permissions() }) { "Allow Forma to read the measurements you want to use." }
        val zone=ZoneId.systemDefault();val now=Instant.now();val today=now.atZone(zone).toLocalDate()
        val original=existing?.takeIf { !it.demo && it.source=="Samsung Health · Health Connect" }
        val history=feature(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) && HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        val target=today.minusDays(if(history)364 else 28)
        val first=today.minusDays(2)
        val catchupFrom=HealthQuality.recentStart(today,original?.syncedAt,zone).coerceAtLeast(target)
        val previous=original?.report
        val reset=fullHistory || previous?.dataVersion!=SyncReport().dataVersion || previous?.granted!=granted || previous?.historyAllowed!=history
        var data=original ?: HealthData("Samsung Health · Health Connect",emptyList())
        var cursor=if(reset)first else minOf(previous?.scannedFrom ?: first,first)
        var catchingUp=catchupFrom<first
        var report=SyncReport(granted,history,cursor,false,devices=previous?.devices.orEmpty())
        suspend fun publish(batch:Batch,start:LocalDate,end:LocalDate,scanned:LocalDate) {
            report=report.copy(scannedFrom=scanned,historyComplete=scanned<=target && !catchingUp,
                latestRecordAt=listOfNotNull(report.latestRecordAt,batch.latest).maxOrNull(),
                devices=(report.devices+batch.devices).distinct().take(12),issues=(report.issues+batch.issues).distinct().take(12))
            data=HealthData("Samsung Health · Health Connect",HealthQuality.merge(data.days,batch.days,start,end),Instant.now(),report=report)
            if(data.days.isNotEmpty())onData(data)
        }
        onProgress(SyncProgress("Reading recent Samsung Health records",0,if(history)365 else 29))
        publish(readRange(first,today,now,zone,granted),first,today,cursor)
        var gap=first
        while(gap>catchupFrom) {
            val start=maxOf(catchupFrom,gap.minusDays(14));val end=gap.minusDays(1)
            onProgress(SyncProgress("Catching up missed days · $start to $end"))
            publish(readRange(start,end,now,zone,granted),start,end,cursor);gap=start;delay(250)
        }
        catchingUp=false
        // Recent data is already visible. Backfill is bounded and resumable at each completed batch.
        while(cursor>target) {
            currentCoroutineContext().ensureActive()
            val start=maxOf(target,cursor.minusDays(14));val end=cursor.minusDays(1)
            onProgress(SyncProgress("Importing history · ${start} to ${end}",java.time.temporal.ChronoUnit.DAYS.between(cursor,today).toInt()+1,if(history)365 else 29))
            val batch=readRange(start,end,now,zone,granted)
            cursor=start;publish(batch,start,end,cursor);delay(250)
        }
        report=report.copy(historyComplete=true);data=data.copy(report=report);if(data.days.isNotEmpty())onData(data)
        // A complete deletion must not leave the final old batch in the cache. An empty day
        // keeps the native empty state exportable without representing any missing metric as zero.
        if(data.days.isEmpty()) { data=data.copy(days=listOf(HealthDay(today)),report=report.copy(latestRecordAt=null));onData(data) }
        onProgress(SyncProgress("Records ready",if(history)365 else 29,if(history)365 else 29))
        return data
    }
    private data class Batch(val days:List<HealthDay>,val latest:Instant?,val devices:List<String>,val issues:List<String>)
    private suspend fun readRange(first:LocalDate,last:LocalDate,now:Instant,zone:ZoneId,granted:Set<String>):Batch {
        val start=first.atStartOfDay(zone).toInstant();val finish=minOf(last.plusDays(1).atStartOfDay(zone).toInstant(),now)
        val filter=TimeRangeFilter.between(start,finish)
        val rawFilter=TimeRangeFilter.between(first.minusDays(1).atStartOfDay(zone).toInstant(),finish)
        var latest:Instant?=null;val devices=mutableSetOf<String>();val issues=mutableSetOf<String>()
        fun observe(record:Record) {
            val at=recordTime(record,now)
            at?.takeIf { it<=now }?.let { latest=maxOf(latest ?: it,it) }
            record.metadata.device?.takeIf { it.type==Device.TYPE_WATCH }?.let { d->devices.add(listOfNotNull(d.manufacturer,d.model).joinToString(" ").ifBlank { "Watch device reported" }.take(100)) }
        }
        suspend fun <T:Record> records(type:KClass<T>):List<T> {
            if(HealthPermission.getReadPermission(type) !in granted)return emptyList()
            val result=mutableListOf<T>();var token:String?=null;val tokens=mutableSetOf<String>()
            do {
                currentCoroutineContext().ensureActive()
                val page=client.readRecords(ReadRecordsRequest(type,rawFilter,dataOriginFilter=samsung,pageSize=500,pageToken=token))
                page.records.forEach(::observe);result.addAll(page.records)
                token=page.pageToken?.takeIf { it.isNotBlank() }
                check(token==null || tokens.add(token!!)) { "Health Connect returned a repeated page. Retry sync." }
            } while(token!=null)
            // Provider IDs identify revisions; keep newest copy. Empty IDs must never collapse records.
            return result.filter { it.metadata.id.isBlank() }+result.filter { it.metadata.id.isNotBlank() }.groupBy { it.metadata.id }.values.map { copies->copies.maxBy { it.metadata.lastModifiedTime } }
        }
        val sleeps=records(SleepSessionRecord::class).filter { it.endTime<=now && Duration.between(it.startTime,it.endTime)<=Duration.ofDays(1) }
            .groupBy { it.startTime to it.endTime }.values.map { copies->copies.maxBy { it.metadata.lastModifiedTime } }
        val exercises=records(ExerciseSessionRecord::class).filter { it.startTime<now && it.endTime<=now && Duration.between(it.startTime,it.endTime)<=Duration.ofDays(7) }.groupBy { Triple(it.startTime,it.endTime,it.exerciseType) }.values.map { it.maxBy { r->r.metadata.lastModifiedTime } }
        val nutrition=records(NutritionRecord::class)
        val rhr=records(RestingHeartRateRecord::class);val hrv=records(HeartRateVariabilityRmssdRecord::class)
        val oxygen=records(OxygenSaturationRecord::class);val breathing=records(RespiratoryRateRecord::class)
        val weight=records(WeightRecord::class);val fat=records(BodyFatRecord::class);val lean=records(LeanBodyMassRecord::class)
        val bp=records(BloodPressureRecord::class);val glucose=records(BloodGlucoseRecord::class)
        val vo2=records(Vo2MaxRecord::class);val basal=records(BasalMetabolicRateRecord::class);val height=records(HeightRecord::class)
        val skin=if(feature(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE))records(SkinTemperatureRecord::class)else emptyList()
        val metrics=buildSet {
            if(HealthPermission.getReadPermission(HydrationRecord::class) in granted)add(HydrationRecord.VOLUME_TOTAL)
            if(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted)add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if(HealthPermission.getReadPermission(SpeedRecord::class) in granted){add(SpeedRecord.SPEED_AVG);add(SpeedRecord.SPEED_MAX)}
            if(HealthPermission.getReadPermission(PowerRecord::class) in granted){add(PowerRecord.POWER_AVG);add(PowerRecord.POWER_MAX)}
            if(HealthPermission.getReadPermission(StepsRecord::class) in granted)add(StepsRecord.COUNT_TOTAL)
            if(HealthPermission.getReadPermission(DistanceRecord::class) in granted)add(DistanceRecord.DISTANCE_TOTAL)
            if(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted)add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if(HealthPermission.getReadPermission(FloorsClimbedRecord::class) in granted)add(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
            if(HealthPermission.getReadPermission(HeartRateRecord::class) in granted)add(HeartRateRecord.BPM_AVG)
        }
        val hours=if(metrics.isEmpty())emptyList()else client.aggregateGroupByDuration(AggregateGroupByDurationRequest(metrics,filter,Duration.ofHours(1),dataOriginFilter=samsung))
        // Civil-day buckets respect the user's local timezone and 23/25-hour DST days.
        val daily=if(metrics.isEmpty())emptyList()else client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics,
            TimeRangeFilter.between(first.atStartOfDay(),finish.atZone(zone).toLocalDateTime()),Period.ofDays(1),dataOriginFilter=samsung))
        // Aggregate endpoints are query boundaries, not measurement timestamps. Read the actual latest step record.
        if(HealthPermission.getReadPermission(StepsRecord::class) in granted)client.readRecords(ReadRecordsRequest(StepsRecord::class,filter,dataOriginFilter=samsung,ascendingOrder=false,pageSize=1)).records.forEach(::observe)
        for(type in listOf(TotalCaloriesBurnedRecord::class,SpeedRecord::class,PowerRecord::class))if(HealthPermission.getReadPermission(type) in granted)client.readRecords(ReadRecordsRequest(type,filter,dataOriginFilter=samsung,ascendingOrder=false,pageSize=1)).records.forEach(::observe)
        fun summary(n:SleepSessionRecord)=HealthQuality.sleep(n.startTime,n.endTime,n.stages.map { s->TimedStage(s.startTime,s.endTime,when(s.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,SleepSessionRecord.STAGE_TYPE_OUT_OF_BED->"Awake"
                SleepSessionRecord.STAGE_TYPE_REM->"REM";SleepSessionRecord.STAGE_TYPE_LIGHT->"Light";SleepSessionRecord.STAGE_TYPE_DEEP->"Deep"
                SleepSessionRecord.STAGE_TYPE_SLEEPING->"Asleep";else->"Unknown"
            }) })
        val summaries=sleeps.associateWith(::summary)
        val primaryNights=sleeps.groupBy { it.endTime.atZone(zone).toLocalDate() }.values.map { nights->nights.maxBy { Duration.between(it.startTime,it.endTime) } }
        val sleepingIntervals=primaryNights.map { summaries.getValue(it) }.flatMap { s->s.intervals.filter { it.name in setOf("Asleep","Light","Deep","REM") }.map { it.start to it.end } }
        // Stream heart pages into minute bins only inside sleep intervals: bounded memory even with high-frequency HR.
        val sleepHeart=mutableMapOf<Long,Pair<Long,Int>>();val workoutHeart=mutableMapOf<Long,Pair<Long,Int>>()
        val latestHeart=mutableMapOf<LocalDate,HeartPoint>();val heartRange=mutableMapOf<LocalDate,Pair<Double,Double>>()
        if(HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            var token:String?=null;val tokens=mutableSetOf<String>()
            do {
                currentCoroutineContext().ensureActive()
                val page=client.readRecords(ReadRecordsRequest(HeartRateRecord::class,rawFilter,dataOriginFilter=samsung,pageSize=100,pageToken=token))
                page.records.forEach { record->
                    observe(record)
                    record.samples.filter { it.time<=now && it.beatsPerMinute in 20..240 }.forEach { sample->
                        val date=sample.time.atZone(zone).toLocalDate();val bpm=sample.beatsPerMinute.toDouble()
                        if(date in first..last) {
                            if(latestHeart[date]?.at?.let { it<sample.time }!=false)latestHeart[date]=HeartPoint(sample.time,bpm)
                            val range=heartRange[date];heartRange[date]=minOf(range?.first ?: bpm,bpm) to maxOf(range?.second ?: bpm,bpm)
                        }
                        if(exercises.any { sample.time>=it.startTime && sample.time<it.endTime }) {
                            val minute=sample.time.epochSecond/60;val old=workoutHeart[minute] ?: (0L to 0)
                            workoutHeart[minute]=(old.first+sample.beatsPerMinute) to (old.second+1)
                        }
                        if(sleepingIntervals.any { sample.time>=it.first && sample.time<it.second }) {
                            val minute=sample.time.epochSecond/60;val old=sleepHeart[minute] ?: (0L to 0)
                            sleepHeart[minute]=(old.first+sample.beatsPerMinute) to (old.second+1)
                        }
                    }
                }
                token=page.pageToken?.takeIf { it.isNotBlank() };check(token==null || tokens.add(token!!)) { "Heart data pagination stalled. Retry sync." }
            } while(token!=null)
        }
        val workoutMetrics=metrics.filter { it !in setOf(HeartRateRecord.BPM_AVG,HydrationRecord.VOLUME_TOTAL,FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL) }.toSet()
        val recorded=exercises.filter { it.startTime>=start && it.startTime<finish }.map { e->
            currentCoroutineContext().ensureActive()
            val result=if(workoutMetrics.isEmpty())null else client.aggregate(AggregateRequest(workoutMetrics,TimeRangeFilter.between(e.startTime,e.endTime),dataOriginFilter=samsung))
            val heart=workoutHeart.mapNotNull { (minute,p)->val t=Instant.ofEpochSecond(minute*60);if(t>=e.startTime && t<e.endTime)HeartPoint(t,p.first.toDouble()/p.second)else null }.sortedBy { it.at }
            val v=buildMap<String,Double> {
                result?.get(DistanceRecord.DISTANCE_TOTAL)?.let { put("distanceKm",it.inKilometers) };result?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.let { put("activeCalories",it.inKilocalories) };result?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.let { put("totalCalories",it.inKilocalories) };result?.get(StepsRecord.COUNT_TOTAL)?.let { put("steps",it.toDouble()) }
                result?.get(SpeedRecord.SPEED_AVG)?.let { put("speedKmh",it.inKilometersPerHour) };result?.get(SpeedRecord.SPEED_MAX)?.let { put("maxSpeedKmh",it.inKilometersPerHour) };result?.get(PowerRecord.POWER_AVG)?.let { put("powerWatts",it.inWatts) };result?.get(PowerRecord.POWER_MAX)?.let { put("maxPowerWatts",it.inWatts) }
                if(heart.isNotEmpty()) { put("heartRateAverage",heart.map { it.bpm }.average());put("heartRateMin",heart.minOf { it.bpm });put("heartRateMax",heart.maxOf { it.bpm }) }
            }.filter { (k,v)->HealthJson.validMeasurement(k,v) }
            if(result!=null)delay(100)
            RecordedWorkout(e.metadata.id.ifBlank { "${e.startTime}/${e.endTime}/${e.exerciseType}" }.take(200),e.exerciseType,e.title.orEmpty().take(160),e.startTime,e.endTime,v,heart)
        }
        val exerciseIntervals=exercises.map { it.startTime to it.endTime }
        fun Instant.date()=atZone(zone).toLocalDate()
        val energyMetrics=metrics.filter { it==StepsRecord.COUNT_TOTAL || it==HeartRateRecord.BPM_AVG }.toSet()
        val energyHours=mutableMapOf<LocalDate,List<AggregationResultGroupedByDuration>>()
        for(night in primaryNights.filter { it.endTime.date() in first..last }) {
            currentCoroutineContext().ensureActive()
            val date=night.endTime.date();val end=minOf(date.plusDays(1).atStartOfDay(zone).toInstant(),now)
            if(night.endTime<end && energyMetrics.isNotEmpty()) {
                // Query from wake itself. Dropping the overlapping civil-hour bucket used to
                // discard post-wake activity; prorating a whole bucket would invent its timing.
                energyHours[date]=client.aggregateGroupByDuration(AggregateGroupByDurationRequest(
                    energyMetrics,TimeRangeFilter.between(night.endTime,end),Duration.ofHours(1),dataOriginFilter=samsung))
                delay(100)
            }
        }
        val days=generateSequence(first) { it.plusDays(1) }.takeWhile { it<=last }.map { date->
            val values=mutableMapOf<String,Double>()
            val meals=nutrition.filter { it.startTime.atZone(zone).toLocalDate()==date }
            fun nutrient(key:String,amount:(NutritionRecord)->Double?) { val numbers=meals.mapNotNull(amount);if(numbers.isNotEmpty())values[key]=numbers.sum() }
            nutrient("dietaryCalories") { it.energy?.inKilocalories };nutrient("proteinGrams") { it.protein?.inGrams };nutrient("carbsGrams") { it.totalCarbohydrate?.inGrams };nutrient("fatGrams") { it.totalFat?.inGrams };nutrient("fiberGrams") { it.dietaryFiber?.inGrams };nutrient("sugarGrams") { it.sugar?.inGrams };nutrient("sodiumMg") { it.sodium?.inMilligrams }
            latestHeart[date]?.let { values["heartRateLatest"]=it.bpm }
            heartRange[date]?.let { values["heartRateMin"]=it.first;values["heartRateMax"]=it.second }
            val heartTimeline=hours.filter { it.startTime.atZone(zone).toLocalDate()==date }.mapNotNull { h->h.result[HeartRateRecord.BPM_AVG]?.toDouble()?.takeIf { HealthJson.validMeasurement("heartRateAverage",it) }?.let { HeartPoint(h.startTime,it) } }
            val bucket=daily.find { it.startTime.toLocalDate()==date }
            bucket?.result?.let { a->a[HydrationRecord.VOLUME_TOTAL]?.let { values["waterMl"]=it.inMilliliters };a[StepsRecord.COUNT_TOTAL]?.let { values["steps"]=it.toDouble() };a[DistanceRecord.DISTANCE_TOTAL]?.let { values["distanceKm"]=it.inKilometers };a[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { values["activeCalories"]=it.inKilocalories };a[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.let { values["floors"]=it };a[HeartRateRecord.BPM_AVG]?.let { values["heartRateAverage"]=it.toDouble() }
                a[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { values["totalCalories"]=it.inKilocalories }
                a[SpeedRecord.SPEED_AVG]?.let { values["speedKmh"]=it.inKilometersPerHour };a[SpeedRecord.SPEED_MAX]?.let { values["maxSpeedKmh"]=it.inKilometersPerHour }
                a[PowerRecord.POWER_AVG]?.let { values["powerWatts"]=it.inWatts };a[PowerRecord.POWER_MAX]?.let { values["maxPowerWatts"]=it.inWatts } }
            vo2.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["vo2Max"]=it.vo2MillilitersPerMinuteKilogram }
            basal.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["basalMetabolicRate"]=it.basalMetabolicRate.inKilocaloriesPerDay }
            height.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["heightCm"]=it.height.inMeters*100 }
            fun median(key:String,numbers:List<Double>) { HealthQuality.median(numbers)?.let { values[key]=it } }
            median("restingHeartRate",rhr.filter { it.time.date()==date }.map { it.beatsPerMinute.toDouble() })
            median("hrv",hrv.filter { it.time.date()==date }.map { it.heartRateVariabilityMillis })
            median("spo2",oxygen.filter { it.time.date()==date }.map { it.percentage.value })
            median("respiratoryRate",breathing.filter { it.time.date()==date }.map { it.rate })
            weight.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["weightKg"]=it.weight.inKilograms }
            fat.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["bodyFat"]=it.percentage.value }
            lean.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["leanMassKg"]=it.mass.inKilograms }
            bp.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["systolic"]=it.systolic.inMillimetersOfMercury;values["diastolic"]=it.diastolic.inMillimetersOfMercury }
            glucose.filter { it.time.date()==date }.maxByOrNull { it.time }?.let { values["bloodGlucose"]=it.level.inMillimolesPerLiter }
            val temperature=skin.flatMap { r->r.deltas.filter { it.time<=now }.map { it.time to (it.delta.inCelsius to r.baseline?.inCelsius) } }.filter { it.first.date()==date }
            median("skinTemperatureDelta",temperature.map { it.second.first })
            median("skinTemperature",temperature.mapNotNull { (_,p)->p.second?.let { it+p.first } })
            val night=sleeps.filter { it.endTime.date()==date }.maxByOrNull { Duration.between(it.startTime,it.endTime) }

            val sleep=night?.let { summaries[it] }
            if(night!=null && sleep!=null) {
                if(sleep.coverage>0)values["sleepMinutes"]=sleep.asleepMinutes
                values["sleepCoverage"]=sleep.coverage;sleep.efficiency?.let { values["sleepEfficiency"]=it }
                if(sleep.coverage<95)issues.add("Some sleep intervals have unknown stages; sleep estimates may be unavailable.")
                median("sleepSpo2",oxygen.filter { it.time>=night.startTime && it.time<night.endTime }.map { it.percentage.value })
                median("sleepRespiratoryRate",breathing.filter { it.time>=night.startTime && it.time<night.endTime }.map { it.rate })
                val minutes=sleepHeart.filter { (minute,_)->val time=Instant.ofEpochSecond(minute*60);time>=night.startTime && time<night.endTime }
                // At least 12 independently observed minutes spanning half the night, rather than one noisy sample.
                if(minutes.size>=12 && Duration.ofMinutes((minutes.keys.maxOrNull()!!-minutes.keys.minOrNull()!!))>=Duration.between(night.startTime,night.endTime).dividedBy(2))median("sleepHeartRate",minutes.values.map { it.first.toDouble()/it.second })
                val naps=sleeps.filter { it.endTime.date()==date && it!=night }.flatMap { s->summaries[s]!!.intervals.filter { it.name in setOf("Asleep","Light","Deep","REM") }.flatMap { listOf(it.start to minOf(it.end,night.startTime),maxOf(it.start,night.endTime) to it.end) } }
                val napMinutes=HealthQuality.unionMinutes(naps, date.atStartOfDay(zone).toInstant(),date.plusDays(1).atStartOfDay(zone).toInstant())
                if(napMinutes>0)values["napMinutes"]=napMinutes
            }
            val a=date.atStartOfDay(zone).toInstant();val b=minOf(date.plusDays(1).atStartOfDay(zone).toInstant(),now)
            if(exerciseIntervals.any { it.first<b && it.second>a })values["activeMinutes"]=HealthQuality.unionMinutes(exerciseIntervals,a,b)
            val wake=night?.endTime
            val samples=if(wake==null)emptyList()else HealthQuality.postWakeWindows(wake,b).mapNotNull { (from,to)->
                val h=energyHours[date]?.firstOrNull { it.startTime==from }
                val steps=h?.result?.get(StepsRecord.COUNT_TOTAL)?.toDouble()?.takeIf { HealthJson.validMeasurement("steps",it) }
                val heart=h?.result?.get(HeartRateRecord.BPM_AVG)?.toDouble()?.takeIf { HealthJson.validMeasurement("heartRateAverage",it) }
                val minutes=HealthQuality.unionMinutes(exerciseIntervals,from,to)
                if(steps==null && heart==null && minutes==0.0)null else EnergySample(Duration.between(a,to).toMillis()/3600000.0,steps,minutes.takeIf { it>0 },heartRate=heart,durationMinutes=Duration.between(from,to).toMillis()/60000.0,recordedAt=to)
            }
            val valid=values.filter { (key,value)->HealthJson.validMeasurement(key,value).also { if(!it)issues.add("Some $key records were outside the supported measurement range.") } }
            val activity=hours.filter { it.startTime.date()==date }.mapNotNull { h->val steps=h.result[StepsRecord.COUNT_TOTAL]?.toDouble()?.takeIf { HealthJson.validMeasurement("steps",it) };val cal=h.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { HealthJson.validMeasurement("activeCalories",it) };val exercise=HealthQuality.unionMinutes(exerciseIntervals,h.startTime,h.endTime).takeIf { it>0 };if(steps==null && cal==null && exercise==null)null else ActivityHour(h.startTime,steps,cal,exercise) }
            HealthDay(date,valid,sleep?.stages.orEmpty(),samples,night?.startTime,night?.endTime,latestHeart[date]?.at,heartTimeline,activity,recorded.filter { it.start.atZone(zone).toLocalDate()==date })
        }.filter { it.values.isNotEmpty() || it.samples.isNotEmpty() || it.sleepStart!=null || it.recordedWorkouts.isNotEmpty() }.toList()
        return Batch(days,latest,devices.toList(),issues.toList())
    }
}
