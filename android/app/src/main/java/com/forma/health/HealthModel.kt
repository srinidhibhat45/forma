package com.forma.health

import java.time.LocalDate
import java.time.Instant
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class SleepStage(val stage: String, val minutes: Double)
data class EnergySample(val hour: Double, val steps: Double? = null, val activeMinutes: Double? = null, val restMinutes: Double? = null, val heartRate: Double? = null)
data class HealthDay(val date: LocalDate, val values: Map<String, Double> = emptyMap(), val stages: List<SleepStage> = emptyList(), val samples: List<EnergySample> = emptyList(), val sleepStart: Instant? = null, val sleepEnd: Instant? = null) {
    operator fun get(key: String): Double? = values[key]
}
data class HealthData(val source: String, val days: List<HealthDay>, val syncedAt: Instant? = null, val demo: Boolean = false)
data class Factor(val label: String, val value: Double, val detail: String, val weight: Double)
data class Score(val value: Int? = null, val confidence: String = "Unavailable", val factors: List<Factor> = emptyList())
data class EnergyResult(val score: Score, val points: List<Pair<Double, Double>>)
data class Preferences(val name: String = "Your", val sleepGoal: Int = 480, val stepGoal: Int = 10000, val waterGoal: Int = 2500, val bedtime: String = "23:00", val backgroundSync: Boolean = false)
data class JournalEntry(val date: LocalDate, val mood: Int, val habits: List<String>, val note: String)
data class ManualWorkout(val id: String, val date: LocalDate, val type: String, val minutes: Int, val intensity: String)

object Scores {
    fun baseline(days: List<HealthDay>, date: LocalDate, key: String): Double? = days.filter { it.date < date && it.date >= date.minusDays(28) }.mapNotNull { it[key] }.takeIf { it.isNotEmpty() }?.average()
    fun sleep(day: HealthDay?, goal: Int): Score {
        val minutes = day?.get("sleepMinutes") ?: return Score()
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
        val factors = buildList {
            s.value?.let { add(Factor("Last night’s sleep", it.toDouble(), "Sleep score $it/100", .6)) }
            val hb = baseline(days, day.date, "hrv")
            val heartKey = if(day["restingHeartRate"] != null && baseline(days,day.date,"restingHeartRate")!=null) "restingHeartRate" else "sleepHeartRate"
            val rb = baseline(days, day.date, heartKey)
            day["hrv"]?.let { if(hb != null && hb > 0) add(Factor("Heart rate variability", (70 + (it/hb-1)*150).coerceIn(0.0,100.0), "${it.roundToInt()} ms · baseline ${hb.roundToInt()} ms", .25)) }
            day[heartKey]?.let { if(rb != null && it > 0) add(Factor(if(heartKey=="restingHeartRate") "Resting heart rate" else "Sleeping heart rate", (75+(rb/it-1)*200).coerceIn(0.0,100.0), "${it.roundToInt()} bpm · baseline ${rb.roundToInt()} bpm", .15)) }
        }
        if(factors.size < 2) return Score(null,"Needs baseline",factors)
        return Score(weighted(factors), if(listOf("hrv",if(factors.any{it.label=="Resting heart rate"})"restingHeartRate" else "sleepHeartRate").all{key->days.count{it.date<day.date && it.date>=day.date.minusDays(28) && it[key]!=null}>=14} && factors.size==3) "Moderate" else "Limited",factors)
    }
    fun energy(day: HealthDay?, days: List<HealthDay>, goal: Int): EnergyResult {
        val recovery = recovery(day, days, goal)
        if(recovery.value == null || day?.samples.isNullOrEmpty()) return EnergyResult(Score(null,"Needs activity data"),emptyList())
        val restingRate = day?.get("restingHeartRate") ?: day?.get("sleepHeartRate")
        var value = recovery.value.toDouble()
        val energyStart=day!!.sleepEnd?.atZone(java.time.ZoneId.systemDefault())?.let{it.hour+it.minute/60.0} ?: (day.samples.first().hour-1).coerceAtLeast(0.0)
        val points = mutableListOf(energyStart to value)
        day.samples.sortedBy { it.hour }.forEach { s ->
            val movement = (s.activeMinutes ?: 0.0)*.16 + (s.steps ?: 0.0)*.0015
            val heart = if(s.heartRate != null && restingRate!=null) (s.heartRate-restingRate).coerceAtLeast(0.0)*.035 else 0.0
            value = (value-movement-heart+(s.restMinutes ?: 0.0)*.025).coerceIn(0.0,100.0)
            points.add(s.hour to value)
        }
        return EnergyResult(Score(value.roundToInt(),"Experimental",listOf(Factor("Starting energy",recovery.value.toDouble(),"Initialized from today’s recovery estimate",1.0),Factor("Movement & heart load",recovery.value-value,"Activity uses energy; explicitly recorded rest adds it",1.0))),points)
    }
    private fun weighted(factors: List<Factor>) = (factors.sumOf { it.value*it.weight }/factors.sumOf { it.weight }).roundToInt()
}
fun duration(minutes: Double?): String = minutes?.let { val m=it.roundToInt(); "${m/60}h ${m%60}m" } ?: "—"
fun measurement(value: Double?, decimals: Int = 0): String = value?.let { if(decimals==0) "%,d".format(it.roundToInt()) else "%.${decimals}f".format(it) } ?: "—"

object HealthJson {
    private val bounds = mapOf("sleepHeartRate" to 20.0..220.0,"napMinutes" to 0.0..1440.0,"steps" to 0.0..200000.0,"sleepMinutes" to 0.0..1440.0,"sleepEfficiency" to 0.0..100.0,"sleepRegularity" to 0.0..100.0,"restingHeartRate" to 20.0..220.0,"hrv" to 1.0..500.0,"spo2" to 50.0..100.0,"respiratoryRate" to 3.0..60.0,"skinTemperature" to 20.0..45.0,"activeMinutes" to 0.0..1440.0,"activeCalories" to 0.0..20000.0,"distanceKm" to 0.0..500.0,"floors" to 0.0..1000.0,"weightKg" to 15.0..500.0,"bodyFat" to 1.0..80.0,"leanMassKg" to 1.0..300.0,"systolic" to 40.0..300.0,"diastolic" to 20.0..200.0,"bloodGlucose" to .1..50.0,"samsungEnergy" to 0.0..100.0)
    fun decode(json: String, demo: Boolean = false): HealthData {
        val root = JSONObject(json); require(root.optInt("version")==1) { "Choose a Forma v1 JSON file." }
        val records = root.getJSONArray("days"); require(records.length() in 1..3660) { "File must contain 1–3,660 days." }
        val days = (0 until records.length()).map { i ->
            val d=records.getJSONObject(i); val date=LocalDate.parse(d.getString("date"))
            val values=buildMap { bounds.forEach { (k,range) -> if(d.has(k) && !d.isNull(k)) { require(d.get(k) is Number) { "$k must be numeric" }; val v=d.getDouble(k); require(v.isFinite() && v in range) { "$k is out of range on $date" }; put(k,v) } } }
            val stages = d.optJSONArray("sleepStages")?.let { a -> require(a.length()<=1000); (0 until a.length()).map { j -> val s=a.getJSONObject(j); val stage=s.getString("stage"); val minutes=s.getDouble("minutes");require(stage in listOf("Awake","REM","Light","Deep") && minutes.isFinite() && minutes>0 && minutes<=1440);SleepStage(stage,minutes) } } ?: emptyList()
            require(stages.sumOf { it.minutes }<=1440)
            val samples=d.optJSONArray("energySamples")?.let { a -> require(a.length()<=1440);(0 until a.length()).map { j -> val s=a.getJSONObject(j);val hour=s.getDouble("hour");require(hour.isFinite() && hour in 0.0..24.0);fun number(k:String,max:Double):Double? { if(!s.has(k)||s.isNull(k))return null;require(s.get(k) is Number);return s.getDouble(k).also { require(it.isFinite() && it in 0.0..max) } }; val sm=EnergySample(hour,number("steps",15000.0),number("activeMinutes",60.0),number("restMinutes",60.0),number("heartRate",240.0));require((sm.activeMinutes?:0.0)+(sm.restMinutes?:0.0)<=60);sm }.sortedBy { it.hour } } ?: emptyList()
            require(samples.map { it.hour }.distinct().size==samples.size) { "Energy samples must have unique hours" }
            HealthDay(date,values,stages,samples,d.optString("sleepStart").takeIf { it.isNotEmpty() }?.let(Instant::parse),d.optString("sleepEnd").takeIf { it.isNotEmpty() }?.let(Instant::parse))
        }.sortedBy { it.date }
        require(days.map { it.date }.distinct().size==days.size) { "Duplicate dates in file" }
        return HealthData(root.optString("source","Imported file").take(120),days,root.optString("syncedAt").takeIf { it.isNotEmpty() }?.let(Instant::parse) ?: if(demo)null else Instant.now(),demo || root.optBoolean("demo",false) || root.optString("source").startsWith("Demo"))
    }
    fun encode(data: HealthData): String = JSONObject().apply {
        put("version",1);put("source",data.source);put("demo",data.demo);data.syncedAt?.let { put("syncedAt",it.toString()) }
        put("days",JSONArray(data.days.map { d -> JSONObject().apply {
            put("date",d.date.toString());d.values.forEach { (k,v)->put(k,v) }
            d.sleepStart?.let { put("sleepStart",it.toString()) };d.sleepEnd?.let { put("sleepEnd",it.toString()) }
            put("sleepStages",JSONArray(d.stages.map { JSONObject().put("stage",it.stage).put("minutes",it.minutes) }))
            put("energySamples",JSONArray(d.samples.map { s -> JSONObject().apply { put("hour",s.hour);s.steps?.let{put("steps",it)};s.activeMinutes?.let{put("activeMinutes",it)};s.restMinutes?.let{put("restMinutes",it)};s.heartRate?.let{put("heartRate",it)} } }))
        } }))
    }.toString(2)
}

fun calendarDays(days:List<HealthDay>,date:LocalDate,count:Int):List<HealthDay> { val byDate=days.associateBy{it.date};return (0 until count).map{val d=date.minusDays((count-1-it).toLong());byDate[d] ?: HealthDay(d)} }
