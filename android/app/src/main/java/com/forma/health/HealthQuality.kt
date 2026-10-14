package com.forma.health

import java.time.*
import kotlin.math.*

data class SyncReport(
    val granted:Set<String> = emptySet(), val historyAllowed:Boolean=false,
    val scannedFrom:LocalDate?=null, val historyComplete:Boolean=false,
    val latestRecordAt:Instant?=null, val devices:List<String> = emptyList(),
    val issues:List<String> = emptyList(),val dataVersion:Int=4
)
data class SyncProgress(val label:String="",val completedDays:Int=0,val totalDays:Int=0)
data class ObservedInsight(val title:String,val detail:String)
data class ConnectionState(val available:Boolean=false,val samsungInstalled:Boolean=false,
    val granted:Set<String> = emptySet(),val historyAvailable:Boolean=false,val historyAllowed:Boolean=false,
    val backgroundAvailable:Boolean=false,val backgroundAllowed:Boolean=false) {
    val hasReadAccess get()=granted.any { it!= "android.permission.health.READ_HEALTH_DATA_HISTORY" && it!="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" }
}
data class TimedStage(val start:Instant,val end:Instant,val name:String)
data class SleepSummary(val stages:List<SleepStage>,val asleepMinutes:Double,val coverage:Double,val efficiency:Double?,val intervals:List<TimedStage>)

object HealthQuality {
    /** Anchor at the actual wake time; neither overnight steps nor the first waking hour are lost. */
    fun postWakeWindows(wake:Instant,end:Instant):List<Pair<Instant,Instant>> {
        if(end<=wake)return emptyList()
        require(Duration.between(wake,end)<=Duration.ofHours(26))
        return generateSequence(wake) { it.plusSeconds(3600) }.takeWhile { it<end }
            .map { it to minOf(it.plusSeconds(3600),end) }.toList()
    }
    fun recentStart(today:LocalDate,lastSync:Instant?,zone:ZoneId):LocalDate =
        minOf(today.minusDays(2),lastSync?.atZone(zone)?.toLocalDate()?.minusDays(1) ?: today.minusDays(2)).coerceAtLeast(today.minusDays(364))

    /** Resolve duplicate/overlapping stages without counting any minute twice or compressing gaps. */
    fun sleep(start:Instant,end:Instant,raw:List<TimedStage>):SleepSummary {
        require(end>start && Duration.between(start,end)<=Duration.ofDays(1))
        val clipped=raw.mapNotNull { val a=maxOf(start,it.start);val b=minOf(end,it.end);if(b>a)it.copy(start=a,end=b)else null }.distinct()
        val boundaries=(listOf(start,end)+clipped.flatMap { listOf(it.start,it.end) }).distinct().sorted()
        val stages=mutableListOf<SleepStage>();val resolved=mutableListOf<TimedStage>()
        boundaries.zipWithNext().forEach { (a,b) ->
            val names=clipped.filter { it.start<b && it.end>a }.map { it.name }.toSet()
            val name=when {
                names.isEmpty() || "Unknown" in names -> "Unknown"
                names.size==1 -> names.single()
                "Awake" in names -> "Unknown"
                else -> "Asleep"
            }
            resolved.add(TimedStage(a,b,name))
            val minutes=Duration.between(a,b).toMillis()/60000.0
            if(stages.lastOrNull()?.stage==name)stages[stages.lastIndex]=SleepStage(name,stages.last().minutes+minutes)
            else stages.add(SleepStage(name,minutes))
        }
        val total=stages.sumOf { it.minutes }
        val known=stages.filter { it.stage!="Unknown" }.sumOf { it.minutes }
        val asleep=stages.filter { it.stage in setOf("Asleep","REM","Light","Deep") }.sumOf { it.minutes }
        val coverage=known/total*100
        return SleepSummary(stages,asleep,coverage,if(coverage>=95)asleep/total*100 else null,resolved)
    }
    fun unionMinutes(intervals:List<Pair<Instant,Instant>>,start:Instant,end:Instant):Double {
        val clipped=intervals.mapNotNull { (a,b) -> val s=maxOf(a,start);val e=minOf(b,end);if(e>s)s to e else null }.sortedBy { it.first }
        var until=start;var total=0L
        clipped.forEach { (a,b) -> total+=Duration.between(maxOf(a,until),maxOf(b,until)).toMillis();until=maxOf(until,b) }
        return total/60000.0
    }
    fun median(values:List<Double>):Double? {
        val sorted=values.filter { it.isFinite() }.sorted();if(sorted.isEmpty())return null
        return if(sorted.size%2==1)sorted[sorted.size/2] else (sorted[sorted.size/2-1]+sorted[sorted.size/2])/2
    }
    fun freshness(at:Instant?,now:Instant=Instant.now()):String = when {
        at==null->"No timestamp shared"
        at>now.plusSeconds(60)->"Timestamp needs checking"
        Duration.between(at,now)<Duration.ofMinutes(15)->"Recent records"
        Duration.between(at,now)<Duration.ofHours(2)->"Sync catching up"
        else->"Older records · sync Samsung Health"
    }
    fun merge(existing:List<HealthDay>,replacement:List<HealthDay>,start:LocalDate,end:LocalDate)=
        (existing.filter { it.date<start || it.date>end }+replacement).associateBy { it.date }.values.sortedBy { it.date }.takeLast(365)
    fun insight(day:HealthDay?,days:List<HealthDay>,goal:Int):ObservedInsight {
        if(day==null || day.values.isEmpty())return ObservedInsight("Your records come first.","No measurements were shared for this date. Sync Samsung Health or choose a recorded day.")
        if((day["sleepCoverage"] ?: 100.0)<90)return ObservedInsight("Your night has missing detail.","Unknown sleep intervals prevent a reliable sleep-based readiness estimate.")
        day["sleepMinutes"]?.let { if(it<goal-60)return ObservedInsight("A shorter night than your goal.","${duration(it)} recorded · ${duration(goal.toDouble())} goal. This describes duration, not why your sleep changed.") }
        for(key in listOf("restingHeartRate","sleepHeartRate","hrv")) {
            val value=day[key] ?: continue
            val baseline=PersonalSignals.baseline(days,day.date,key) ?: continue
            val z=baseline.deviation(value)
            if(key!="hrv" && z>1.5)return ObservedInsight("Heart rate above your usual pattern.","${measurement(value)} bpm · typical ${measurement(baseline.typical)} bpm across ${baseline.count} prior days. This alone does not identify a cause.")
            if(key=="hrv" && z< -1.5)return ObservedInsight("HRV below your recent pattern.","${measurement(value)} ms · typical ${measurement(baseline.typical)} ms across ${baseline.count} prior days. Check alongside sleep, context and how you feel.")
        }
        val usable=listOf("hrv","restingHeartRate","sleepHeartRate").any { day[it]!=null && PersonalSignals.baseline(days,day.date,it)!=null }
        if(!usable)return ObservedInsight("Your baseline comes first.","Readiness needs sleep and at least seven prior days of a matching vital. Missing readings stay missing.")
        return ObservedInsight("No marked change in shared signals.","Your available measurements are close to their recent pattern. This is not an assessment of your overall health.")
    }
}

data class PersonalBaseline(val center:Double,val spread:Double,val count:Int,val logScale:Boolean) {
    fun deviation(value:Double):Double=((if(logScale)ln(value)else value)-center)/spread
    val typical get()=if(logScale)exp(center)else center
}

object PersonalSignals {
    const val MIN_BASELINE_DAYS=7
    fun baseline(days:List<HealthDay>,date:LocalDate,key:String):PersonalBaseline? {
        val logged=key=="hrv"
        val range=when(key) { "hrv"->1.0..500.0;"restingHeartRate","sleepHeartRate"->20.0..220.0;"respiratoryRate","sleepRespiratoryRate"->3.0..60.0;"spo2","sleepSpo2"->50.0..100.0;"skinTemperature"->20.0..45.0;"skinTemperatureDelta"->-20.0..20.0;else->return null }
        val values=days.filter { it.date<date && it.date>=date.minusDays(28) }.distinctBy { it.date }.mapNotNull { it[key] }
            .filter { it.isFinite() && it in range }.map { if(logged)ln(it)else it }
        if(values.size<MIN_BASELINE_DAYS)return null
        val center=HealthQuality.median(values)!!
        val mad=HealthQuality.median(values.map { abs(it-center) })!!*1.4826
        val floor=when(key) { "hrv"->.1;"respiratoryRate","sleepRespiratoryRate","spo2","sleepSpo2"->1.0;"skinTemperature","skinTemperatureDelta"->.15;else->3.0 }
        return PersonalBaseline(center,max(mad,floor),values.size,logged)
    }
}
