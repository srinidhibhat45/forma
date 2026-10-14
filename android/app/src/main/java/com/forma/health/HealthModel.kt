package com.forma.health

import java.time.LocalDate
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

data class SleepStage(val stage: String, val minutes: Double)
data class EnergySample(val hour: Double, val steps: Double? = null, val activeMinutes: Double? = null, val restMinutes: Double? = null, val heartRate: Double? = null, val durationMinutes:Double=60.0, val recordedAt:Instant?=null)
data class HeartPoint(val at:Instant,val bpm:Double)
data class EnergyReadReport(val from:Instant,val to:Instant,val stepsAllowed:Boolean,val heartAllowed:Boolean,
    val returnedBuckets:Int,val matchedBuckets:Int,val exactQueries:Int,val exactPostWakeSteps:Double?=null,
    val rawHeartMinutes:Int=0,val latestRawHeartAt:Instant?=null,val rawHeartFallbackBuckets:Int=0)
data class ActivityHour(val at:Instant,val steps:Double?=null,val calories:Double?=null,val exerciseMinutes:Double?=null)
data class RecordedWorkout(val id:String,val type:Int,val title:String,val start:Instant,val end:Instant,val values:Map<String,Double> = emptyMap(),val heart:List<HeartPoint> = emptyList()) {
    val minutes get()=java.time.Duration.between(start,end).toMillis()/60000.0
}
data class HealthDay(val date: LocalDate, val values: Map<String, Double> = emptyMap(), val stages: List<SleepStage> = emptyList(), val samples: List<EnergySample> = emptyList(), val sleepStart: Instant? = null, val sleepEnd: Instant? = null, val heartRecordedAt:Instant?=null, val heartTimeline:List<HeartPoint> = emptyList(),val activity:List<ActivityHour> = emptyList(),val recordedWorkouts:List<RecordedWorkout> = emptyList(),val energyRead:EnergyReadReport?=null) {
    operator fun get(key: String): Double? = values[key]
}
data class HealthData(val source: String, val days: List<HealthDay>, val syncedAt: Instant? = null, val demo: Boolean = false, val report:SyncReport?=null)
data class Factor(val label: String, val value: Double, val detail: String, val weight: Double)
data class Score(val value: Int? = null, val confidence: String = "Unavailable", val factors: List<Factor> = emptyList())
data class EnergyResult(val score: Score, val points: List<Pair<Double, Double>>, val recordedAt:Instant?=null)
data class Preferences(val name: String = "Your", val sleepGoal: Int = 480, val stepGoal: Int = 10000, val waterGoal: Int = 2500, val bedtime: String = "23:00", val backgroundSync: Boolean = false, val automaticSync: Boolean = true, val calorieGoal:Int=500, val exerciseGoal:Int=30)
data class JournalEntry(val date: LocalDate, val mood: Int, val habits: List<String>, val note: String)
data class ManualWorkout(val id: String, val date: LocalDate, val type: String, val minutes: Int, val intensity: String)

object Scores {
    fun baseline(days: List<HealthDay>, date: LocalDate, key: String): Double? = days.filter { it.date < date && it.date >= date.minusDays(28) }.distinctBy { it.date }.mapNotNull { it[key] }.takeIf { it.size>=7 }?.let { HealthQuality.median(it) }
    fun sleep(day: HealthDay?, goal: Int): Score {
        val minutes = day?.get("sleepMinutes") ?: return Score()
        if(day["sleepCoverage"]?.let { it<90 }==true)return Score(null,"Incomplete sleep")
        if(goal<=0 || !minutes.isFinite())return Score()
        val factors = buildList {
            add(Factor("Sleep duration", (minutes / goal * 100).coerceIn(0.0,100.0), "${duration(minutes)} of your ${duration(goal.toDouble())} goal", .5))
            day["sleepEfficiency"]?.let { add(Factor("Sleep efficiency", it, "${it.roundToInt()}% of time in bed asleep", .3)) }
            day["sleepRegularity"]?.let { add(Factor("Schedule consistency", it, "${it.roundToInt()}% regularity", .2)) }
        }
        return Score(weighted(factors), if(factors.size == 3) "Moderate" else "Limited", factors)
    }
    fun recovery(day: HealthDay?, days: List<HealthDay>, goal: Int): Score {
        day ?: return Score()
        val s = sleep(day, goal)
        if(s.value==null)return Score(null,s.confidence)
        val factors=mutableListOf(Factor("Last night’s sleep",s.value.toDouble(),"Sleep score ${s.value}/100",.6))
        val hb=PersonalSignals.baseline(days,day.date,"hrv")
        val heartKey=if(day["restingHeartRate"]!=null && PersonalSignals.baseline(days,day.date,"restingHeartRate")!=null)"restingHeartRate" else "sleepHeartRate"
        val rb=PersonalSignals.baseline(days,day.date,heartKey)
        day["hrv"]?.takeIf { it.isFinite() && it>0 }?.let { if(hb!=null)factors.add(Factor("Heart rate variability",(75+12*hb.deviation(it).coerceIn(-3.0,1.25)).coerceIn(0.0,90.0),"${it.roundToInt()} ms · typical ${hb.typical.roundToInt()} ms · ${hb.count} prior days",.25)) }
        day[heartKey]?.takeIf { it.isFinite() && it>0 }?.let { if(rb!=null)factors.add(Factor(if(heartKey=="restingHeartRate")"Resting heart rate" else "Sleeping heart rate",(75-12*rb.deviation(it).coerceIn(-1.25,3.0)).coerceIn(0.0,90.0),"${it.roundToInt()} bpm · typical ${rb.typical.roundToInt()} bpm · ${rb.count} prior days",.15)) }
        if(factors.size<2)return Score(null,"Needs 7 baseline days",factors)
        return Score(weighted(factors),if(factors.size==3 && hb!!.count>=14 && rb!!.count>=14)"Moderate" else "Limited",factors)
    }

    fun energy(day: HealthDay?, days: List<HealthDay>, goal: Int): EnergyResult {
        val recovery = recovery(day, days, goal)
        if(recovery.value==null)return EnergyResult(recovery,emptyList())
        if(day?.samples.isNullOrEmpty())return EnergyResult(Score(null,"Needs activity data"),emptyList())
        val restingRate = day?.get("restingHeartRate") ?: day?.get("sleepHeartRate")
        var value = recovery.value.toDouble()
        val energyStart=day!!.sleepEnd?.let { wake->if(day.samples.any { it.recordedAt!=null })java.time.Duration.between(day.date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),wake).toMillis()/3600000.0 else wake.atZone(java.time.ZoneId.systemDefault()).let { it.hour+it.minute/60.0 } } ?: (day.samples.first().hour-1).coerceAtLeast(0.0)
        val points = mutableListOf(energyStart to value)
        day.samples.sortedBy { it.hour }.forEach { s ->
            val movement = max((s.activeMinutes ?: 0.0)*.16,(s.steps ?: 0.0)*.0015)
            val heart = if(s.heartRate != null && restingRate!=null) (s.heartRate-restingRate).coerceAtLeast(0.0)*.035*(s.durationMinutes/60.0).coerceIn(0.0,1.0) else 0.0
            value = (value-max(movement,heart)+(s.restMinutes ?: 0.0)*.025).coerceIn(0.0,100.0)
            points.add(s.hour to value)
        }
        return EnergyResult(Score(value.roundToInt(),"Experimental",listOf(Factor("Starting energy",recovery.value.toDouble(),"Initialized from today’s recovery estimate",1.0),Factor("Movement & heart load",recovery.value-value,"The strongest overlapping load is counted once; only explicit rest can add energy",1.0))),points,day.samples.mapNotNull { it.recordedAt }.maxOrNull())
    }
    private fun weighted(factors: List<Factor>) = (factors.sumOf { it.value*it.weight }/factors.sumOf { it.weight }).roundToInt()
}
fun duration(minutes: Double?): String = minutes?.let { val m=it.roundToInt(); "${m/60}h ${m%60}m" } ?: "—"
fun measurement(value: Double?, decimals: Int = 0): String = value?.let { if(decimals==0) "%,d".format(it.roundToInt()) else "%.${decimals}f".format(it) } ?: "—"

object HealthJson {
    private val bounds = mapOf("dietaryCalories" to 0.0..30000.0,"proteinGrams" to 0.0..3000.0,"carbsGrams" to 0.0..5000.0,"fatGrams" to 0.0..3000.0,"fiberGrams" to 0.0..1000.0,"sugarGrams" to 0.0..3000.0,"sodiumMg" to 0.0..100000.0,"waterMl" to 0.0..30000.0,"totalCalories" to 0.0..30000.0,"vo2Max" to 0.0..150.0,"speedKmh" to 0.0..300.0,"maxSpeedKmh" to 0.0..300.0,"powerWatts" to 0.0..3000.0,"maxPowerWatts" to 0.0..3000.0,"basalMetabolicRate" to 0.0..10000.0,"heightCm" to 30.0..300.0,"heartRateLatest" to 20.0..240.0,"heartRateAverage" to 20.0..240.0,"heartRateMin" to 20.0..240.0,"heartRateMax" to 20.0..240.0,"sleepHeartRate" to 20.0..220.0,"sleepCoverage" to 0.0..100.0,"sleepSpo2" to 50.0..100.0,"sleepRespiratoryRate" to 3.0..60.0,"skinTemperatureDelta" to -20.0..20.0,"napMinutes" to 0.0..1440.0,"steps" to 0.0..200000.0,"sleepMinutes" to 0.0..1440.0,"sleepEfficiency" to 0.0..100.0,"sleepRegularity" to 0.0..100.0,"restingHeartRate" to 20.0..220.0,"hrv" to 1.0..500.0,"spo2" to 50.0..100.0,"respiratoryRate" to 3.0..60.0,"skinTemperature" to 20.0..45.0,"activeMinutes" to 0.0..1440.0,"activeCalories" to 0.0..20000.0,"distanceKm" to 0.0..500.0,"floors" to 0.0..1000.0,"weightKg" to 15.0..500.0,"bodyFat" to 1.0..80.0,"leanMassKg" to 1.0..300.0,"systolic" to 40.0..300.0,"diastolic" to 20.0..200.0,"bloodGlucose" to .1..50.0,"samsungEnergy" to 0.0..100.0)
    val recognizedFields get()=bounds.keys
    fun validMeasurement(key:String,value:Double)=value.isFinite() && bounds[key]?.contains(value)==true
    fun decode(json: String, demo: Boolean = false): HealthData {
        val root = JSONObject(json); require(root.optInt("version")==1) { "Choose a Forma v1 JSON file." }
        val records = root.getJSONArray("days"); require(records.length() in 1..3660) { "File must contain 1–3,660 days." }
        val days = (0 until records.length()).map { i ->
            val d=records.getJSONObject(i); val date=LocalDate.parse(d.getString("date"))
            val values=buildMap { bounds.forEach { (k,range) -> if(d.has(k) && !d.isNull(k)) { require(d.get(k) is Number) { "$k must be numeric" }; val v=d.getDouble(k); require(v.isFinite() && v in range) { "$k is out of range on $date" }; put(k,v) } } }
            val stages = d.optJSONArray("sleepStages")?.let { a -> require(a.length()<=1000); (0 until a.length()).map { j -> val s=a.getJSONObject(j); val stage=s.getString("stage"); val minutes=s.getDouble("minutes");require(stage in listOf("Awake","REM","Light","Deep","Asleep","Unknown") && minutes.isFinite() && minutes>0 && minutes<=1440);SleepStage(stage,minutes) } } ?: emptyList()
            require(stages.sumOf { it.minutes }<=1440)
            val samples=d.optJSONArray("energySamples")?.let { a -> require(a.length()<=1440);(0 until a.length()).map { j -> val s=a.getJSONObject(j);val hour=s.getDouble("hour");require(hour.isFinite() && hour in 0.0..26.0);fun number(k:String,max:Double):Double? { if(!s.has(k)||s.isNull(k))return null;require(s.get(k) is Number);return s.getDouble(k).also { require(it.isFinite() && it in 0.0..max) } }; val sm=EnergySample(hour,number("steps",200000.0),number("activeMinutes",60.0),number("restMinutes",60.0),number("heartRate",240.0),number("durationMinutes",60.0)?:60.0,s.optString("recordedAt").takeIf{it.isNotBlank()}?.let(Instant::parse));require((sm.activeMinutes?:0.0)+(sm.restMinutes?:0.0)<=60);sm }.sortedBy { it.hour } } ?: emptyList()
            require(samples.map { it.hour }.distinct().size==samples.size) { "Energy samples must have unique hours" }
            fun points(a:JSONArray?,limit:Int):List<HeartPoint> = a?.let { require(it.length()<=limit);(0 until it.length()).map { j->val point=it.getJSONObject(j);HeartPoint(Instant.parse(point.getString("at")),point.getDouble("bpm").also { v->require(validMeasurement("heartRateAverage",v)) }) }.sortedBy { it.at }.distinctBy { it.at } }.orEmpty()
            val activity=d.optJSONArray("activityHours")?.let { a->require(a.length()<=26);(0 until a.length()).map { j->val h=a.getJSONObject(j);fun value(k:String,field:String):Double?=if(!h.has(k))null else h.getDouble(k).also { require(validMeasurement(field,it)) }
                ActivityHour(Instant.parse(h.getString("at")),value("steps","steps"),value("calories","activeCalories"),value("exerciseMinutes","activeMinutes")) } }.orEmpty()
            val workouts=d.optJSONArray("recordedWorkouts")?.let { a->require(a.length()<=200);(0 until a.length()).map { j->val w=a.getJSONObject(j);val start=Instant.parse(w.getString("start"));val end=Instant.parse(w.getString("end"));require(end>start && java.time.Duration.between(start,end)<=java.time.Duration.ofDays(7));val v=w.optJSONObject("values");val fields=listOf("distanceKm","activeCalories","totalCalories","steps","heartRateAverage","heartRateMin","heartRateMax","speedKmh","maxSpeedKmh","powerWatts","maxPowerWatts");RecordedWorkout(w.getString("id").take(200),w.getInt("type"),w.optString("title").take(160),start,end,buildMap { fields.forEach { k->if(v?.has(k)==true)put(k,v.getDouble(k).also { require(validMeasurement(k,it)) }) } },points(w.optJSONArray("heart"),10080).also { ps->require(ps.all { it.at>=start && it.at<end }) }) }.distinctBy { it.id } }.orEmpty()
            val energyRead=d.optJSONObject("energyRead")?.let { r->
                fun count(key:String)=r.getInt(key).also { require(it in 0..100000) }
                val from=Instant.parse(r.getString("from"));val to=Instant.parse(r.getString("to"));require(to>=from && java.time.Duration.between(from,to)<=java.time.Duration.ofHours(26))
                EnergyReadReport(from,to,r.getBoolean("stepsAllowed"),r.getBoolean("heartAllowed"),count("returnedBuckets"),count("matchedBuckets"),count("exactQueries"),
                    if(r.has("exactPostWakeSteps"))r.getDouble("exactPostWakeSteps").also { require(validMeasurement("steps",it)) }else null,
                    count("rawHeartMinutes"),r.optString("latestRawHeartAt").takeIf { it.isNotBlank() }?.let(Instant::parse)?.also { require(it>=from && it<to) },count("rawHeartFallbackBuckets"))
            }
            HealthDay(date,values,stages,samples,d.optString("sleepStart").takeIf { it.isNotEmpty() }?.let(Instant::parse),d.optString("sleepEnd").takeIf { it.isNotEmpty() }?.let(Instant::parse),
                d.optString("heartRecordedAt").takeIf { it.isNotBlank() }?.let(Instant::parse),
                points(d.optJSONArray("heartTimeline"),26),activity,workouts,energyRead)
        }.sortedBy { it.date }
        require(days.map { it.date }.distinct().size==days.size) { "Duplicate dates in file" }
        val report=root.optJSONObject("syncReport")?.let { r->
            fun strings(k:String):List<String> { val a=r.optJSONArray(k) ?: return emptyList();return (0 until a.length()).map(a::getString) }
            SyncReport(strings("granted").toSet(),r.optBoolean("historyAllowed"),r.optString("scannedFrom").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },r.optBoolean("historyComplete"),r.optString("latestRecordAt").takeIf { it.isNotBlank() }?.let { Instant.parse(it) },strings("devices"),strings("issues"),r.optInt("dataVersion",1))
        }
        return HealthData(root.optString("source","Imported file").take(120),days,root.optString("syncedAt").takeIf { it.isNotEmpty() }?.let(Instant::parse) ?: if(demo)null else Instant.now(),demo || root.optBoolean("demo",false) || root.optString("source").startsWith("Demo"),report)
    }
    fun encode(data: HealthData): String = JSONObject().apply {
        put("version",1);put("source",data.source);put("demo",data.demo);data.syncedAt?.let { put("syncedAt",it.toString()) }
        data.report?.let { r-> put("syncReport",JSONObject().put("granted",JSONArray(r.granted.toList())).put("historyAllowed",r.historyAllowed).put("scannedFrom",r.scannedFrom?.toString()).put("historyComplete",r.historyComplete).put("latestRecordAt",r.latestRecordAt?.toString()).put("devices",JSONArray(r.devices)).put("issues",JSONArray(r.issues)).put("dataVersion",r.dataVersion)) }
        put("days",JSONArray(data.days.map { d -> JSONObject().apply {
            put("date",d.date.toString());d.values.forEach { (k,v)->put(k,v) }
            d.sleepStart?.let { put("sleepStart",it.toString()) };d.sleepEnd?.let { put("sleepEnd",it.toString()) }
            d.heartRecordedAt?.let { put("heartRecordedAt",it.toString()) };put("heartTimeline",JSONArray(d.heartTimeline.map { JSONObject().put("at",it.at.toString()).put("bpm",it.bpm) }))
            put("activityHours",JSONArray(d.activity.map { h->JSONObject().put("at",h.at.toString()).apply { h.steps?.let { put("steps",it) };h.calories?.let { put("calories",it) };h.exerciseMinutes?.let { put("exerciseMinutes",it) } } }))
            put("recordedWorkouts",JSONArray(d.recordedWorkouts.map { w->JSONObject().put("id",w.id).put("type",w.type).put("title",w.title).put("start",w.start.toString()).put("end",w.end.toString()).put("values",JSONObject(w.values)).put("heart",JSONArray(w.heart.map { JSONObject().put("at",it.at.toString()).put("bpm",it.bpm) })) }))
            put("sleepStages",JSONArray(d.stages.map { JSONObject().put("stage",it.stage).put("minutes",it.minutes) }))
            put("energySamples",JSONArray(d.samples.map { s -> JSONObject().apply { put("hour",s.hour);s.steps?.let{put("steps",it)};s.activeMinutes?.let{put("activeMinutes",it)};s.restMinutes?.let{put("restMinutes",it)};s.heartRate?.let{put("heartRate",it)};put("durationMinutes",s.durationMinutes);s.recordedAt?.let{put("recordedAt",it.toString())} } }))
            d.energyRead?.let { r->put("energyRead",JSONObject().put("from",r.from.toString()).put("to",r.to.toString()).put("stepsAllowed",r.stepsAllowed).put("heartAllowed",r.heartAllowed).put("returnedBuckets",r.returnedBuckets).put("matchedBuckets",r.matchedBuckets).put("exactQueries",r.exactQueries).put("exactPostWakeSteps",r.exactPostWakeSteps).put("rawHeartMinutes",r.rawHeartMinutes).put("latestRawHeartAt",r.latestRawHeartAt?.toString()).put("rawHeartFallbackBuckets",r.rawHeartFallbackBuckets)) }
        } }))
    }.toString(2)
}

fun calendarDays(days:List<HealthDay>,date:LocalDate,count:Int):List<HealthDay> { val byDate=days.associateBy{it.date};return (0 until count).map{val d=date.minusDays((count-1-it).toLong());byDate[d] ?: HealthDay(d)} }
