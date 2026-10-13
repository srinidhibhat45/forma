package com.forma.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.*
import kotlin.reflect.KClass
import kotlin.math.max
import kotlin.math.min

class HealthReader(private val context: Context) {
    val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val samsung=setOf(DataOrigin("com.sec.android.app.shealth"))
    private val zone=ZoneId.systemDefault()
    val status get()=HealthConnectClient.getSdkStatus(context)
    fun permissions(): Set<String> {
        val types=mutableListOf<KClass<out Record>>(StepsRecord::class,DistanceRecord::class,ActiveCaloriesBurnedRecord::class,ExerciseSessionRecord::class,FloorsClimbedRecord::class,HeartRateRecord::class,RestingHeartRateRecord::class,HeartRateVariabilityRmssdRecord::class,SleepSessionRecord::class,OxygenSaturationRecord::class,RespiratoryRateRecord::class,WeightRecord::class,BodyFatRecord::class,LeanBodyMassRecord::class,BloodPressureRecord::class,BloodGlucoseRecord::class)
        if(status==HealthConnectClient.SDK_AVAILABLE && client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) types.add(SkinTemperatureRecord::class)
        return types.map { HealthPermission.getReadPermission(it) }.toSet()
    }
    suspend fun sync(existing: HealthData? = null): HealthData {
        check(status==HealthConnectClient.SDK_AVAILABLE) { "Health Connect is unavailable. Install or update it on your phone." }
        val granted=client.permissionController.getGrantedPermissions()
        check(granted.any { it in permissions() }) { "Grant Health Connect read access to sync your watch." }
        val now=Instant.now();val today=LocalDate.now(zone)
        val first=if(existing!=null && !existing.demo) today.minusDays(2) else today.minusDays(28)
        val start=first.atStartOfDay(zone).toInstant();val filter=TimeRangeFilter.between(start,now)
        suspend fun <T:Record> records(type:KClass<T>):List<T> {
            if(HealthPermission.getReadPermission(type) !in granted)return emptyList()
            val result=mutableListOf<T>();var token:String?=null
            do { val page=client.readRecords(ReadRecordsRequest(type,filter,dataOriginFilter=samsung,pageSize=1000,pageToken=token));result.addAll(page.records);token=page.pageToken } while(token!=null)
            return result
        }
        val metrics=buildSet {
            if(HealthPermission.getReadPermission(StepsRecord::class) in granted)add(StepsRecord.COUNT_TOTAL)
            if(HealthPermission.getReadPermission(DistanceRecord::class) in granted)add(DistanceRecord.DISTANCE_TOTAL)
            if(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted)add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if(HealthPermission.getReadPermission(FloorsClimbedRecord::class) in granted)add(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
        }
        val buckets=if(metrics.isEmpty())emptyList() else client.aggregateGroupByDuration(AggregateGroupByDurationRequest(metrics,filter,Duration.ofHours(1),dataOriginFilter=samsung))
        val sleeps=records(SleepSessionRecord::class)
        val hr=records(HeartRateRecord::class).flatMap { it.samples }
        val rhr=records(RestingHeartRateRecord::class)
        val hrv=records(HeartRateVariabilityRmssdRecord::class)
        val oxygen=records(OxygenSaturationRecord::class)
        val breath=records(RespiratoryRateRecord::class)
        val exercises=records(ExerciseSessionRecord::class)
        val weight=records(WeightRecord::class);val fat=records(BodyFatRecord::class);val lean=records(LeanBodyMassRecord::class)
        val bp=records(BloodPressureRecord::class);val glucose=records(BloodGlucoseRecord::class)
        val skin=if(HealthPermission.getReadPermission(SkinTemperatureRecord::class) in granted)records(SkinTemperatureRecord::class) else emptyList()
        fun Instant.date()=atZone(zone).toLocalDate()
        fun activeMinutes(a:Instant,b:Instant):Double {
            // Union recorded exercise intervals so overlapping sessions cannot double-count minutes.
            val intervals=exercises.mapNotNull { e -> val s=max(a.toEpochMilli(),e.startTime.toEpochMilli());val end=min(b.toEpochMilli(),e.endTime.toEpochMilli());if(end>s)s to end else null }.sortedBy{it.first}
            var total=0L;var end=Long.MIN_VALUE
            for((s,e) in intervals){total+=(e-max(s,end)).coerceAtLeast(0);end=max(end,e)}
            return total/60000.0
        }
        val days=generateSequence(first){it.plusDays(1)}.takeWhile{it<=today}.map { date ->
            val values=mutableMapOf<String,Double>();val daily=buckets.filter{it.startTime.date()==date}
            fun sum(key:String,measurements:List<Double>){if(measurements.isNotEmpty())values[key]=measurements.sum()}
            sum("steps",daily.mapNotNull{it.result[StepsRecord.COUNT_TOTAL]?.toDouble()})
            sum("distanceKm",daily.mapNotNull{it.result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers})
            sum("activeCalories",daily.mapNotNull{it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories})
            sum("floors",daily.mapNotNull{it.result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]})
            fun average(key:String,measurements:List<Double>){if(measurements.isNotEmpty())values[key]=measurements.average()}
            average("restingHeartRate",rhr.filter{it.time.date()==date}.map{it.beatsPerMinute.toDouble()})
            average("hrv",hrv.filter{it.time.date()==date}.map{it.heartRateVariabilityMillis})
            average("spo2",oxygen.filter{it.time.date()==date}.map{it.percentage.value})
            average("respiratoryRate",breath.filter{it.time.date()==date}.map{it.rate})
            weight.filter{it.time.date()==date}.maxByOrNull{it.time}?.let{values["weightKg"]=it.weight.inKilograms}
            fat.filter{it.time.date()==date}.maxByOrNull{it.time}?.let{values["bodyFat"]=it.percentage.value}
            lean.filter{it.time.date()==date}.maxByOrNull{it.time}?.let{values["leanMassKg"]=it.mass.inKilograms}
            bp.filter{it.time.date()==date}.maxByOrNull{it.time}?.let{values["systolic"]=it.systolic.inMillimetersOfMercury;values["diastolic"]=it.diastolic.inMillimetersOfMercury}
            glucose.filter{it.time.date()==date}.maxByOrNull{it.time}?.let{values["bloodGlucose"]=it.level.inMillimolesPerLiter}
            average("skinTemperature",skin.filter{it.endTime.date()==date}.mapNotNull{it.baseline?.inCelsius})
            val night=sleeps.filter{it.endTime.date()==date}.maxByOrNull{Duration.between(it.startTime,it.endTime).toMinutes()}
            val sleepingTypes=setOf(SleepSessionRecord.STAGE_TYPE_SLEEPING,SleepSessionRecord.STAGE_TYPE_LIGHT,SleepSessionRecord.STAGE_TYPE_DEEP,SleepSessionRecord.STAGE_TYPE_REM)
            night?.let { n ->
                val sleepingHr=hr.filter{sample -> n.stages.any{sample.time>=it.startTime && sample.time<it.endTime && it.stage in sleepingTypes}}.map{it.beatsPerMinute.toDouble()}
                if(sleepingHr.isNotEmpty())values["sleepHeartRate"]=sleepingHr.average()
                val napMinutes=sleeps.filter{it.endTime.date()==date && it!=n}.sumOf{session->session.stages.filter{it.stage in sleepingTypes}.sumOf{Duration.between(it.startTime,it.endTime).toMillis()/60000.0}}
                if(napMinutes>0)values["napMinutes"]=napMinutes
            }
            val stages=night?.stages?.mapNotNull { s -> val name=when(s.stage){SleepSessionRecord.STAGE_TYPE_AWAKE,SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "Awake";SleepSessionRecord.STAGE_TYPE_REM -> "REM";SleepSessionRecord.STAGE_TYPE_LIGHT -> "Light";SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep";else->null};name?.let{SleepStage(it,Duration.between(s.startTime,s.endTime).toMillis()/60000.0)} } ?: emptyList()
            night?.let { n ->
                val known=n.stages.filter{it.stage in setOf(SleepSessionRecord.STAGE_TYPE_SLEEPING,SleepSessionRecord.STAGE_TYPE_LIGHT,SleepSessionRecord.STAGE_TYPE_DEEP,SleepSessionRecord.STAGE_TYPE_REM)}.sumOf{Duration.between(it.startTime,it.endTime).toMillis()/60000.0}
                // Unknown stages and empty stage arrays do not silently become time asleep.
                if(known>0)values["sleepMinutes"]=known
                val session=Duration.between(n.startTime,n.endTime).toMillis()/60000.0
                if(known>0 && session>0)values["sleepEfficiency"]=(known/session*100).coerceIn(0.0,100.0)
            }
            val a=date.atStartOfDay(zone).toInstant();val b=minOf(date.plusDays(1).atStartOfDay(zone).toInstant(),now)
            if(exercises.any{it.startTime<b && it.endTime>a})values["activeMinutes"]=activeMinutes(a,b)
            val wake=night?.endTime ?: a
            val samples=daily.filter{it.endTime>wake && it.endTime<=now && it.endTime>wake}.mapNotNull { bucket ->
                val heart=hr.filter{it.time>=bucket.startTime && it.time<bucket.endTime}.map{it.beatsPerMinute.toDouble()}.takeIf{it.isNotEmpty()}?.average()
                val steps=bucket.result[StepsRecord.COUNT_TOTAL]?.toDouble()
                val minutes=activeMinutes(maxOf(wake,bucket.startTime),bucket.endTime)
                if(steps==null && heart==null && minutes==0.0)null else EnergySample(bucket.endTime.atZone(zone).let{it.hour+it.minute/60.0}.takeIf{it>0} ?:24.0,steps,minutes.takeIf{it>0},heartRate=heart)
            }
            HealthDay(date,values,stages,samples,night?.startTime,night?.endTime)
        }.filter{it.values.isNotEmpty()||it.samples.isNotEmpty()}.toList()
        val merged=(existing?.takeUnless{it.demo}?.days.orEmpty().filter{it.date<first}+days).sortedBy{it.date}.takeLast(365)
        check(merged.isNotEmpty()) { "No Samsung Health records were shared. Check Samsung Health → Settings → Health Connect, then sync your watch." }
        return HealthData("Samsung Health · Health Connect",merged,now)
    }
}
