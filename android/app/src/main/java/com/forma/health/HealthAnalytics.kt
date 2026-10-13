package com.forma.health

import java.time.*
import kotlin.math.*

data class SleepPattern(val nights:Int,val average:Double?,val shortfall:Double?,val bedtime:Double?,val wakeTime:Double?,val bedtimeSpread:Double?,val goalNights:Int)
data class ActivityWeek(val days:Int,val steps:Double?,val exercise:Double?,val closedDays:Int,val streak:Int)
object HealthAnalytics {
    /** Noon anchors the sleep clock, so 23:55 and 00:05 are ten minutes apart. */
    fun sleepClock(at:Instant,zone:ZoneId):Double { val t=at.atZone(zone);val minutes=t.hour*60+t.minute;return if(minutes<720)minutes+1440.0 else minutes.toDouble() }
    fun sleepPattern(days:List<HealthDay>,date:LocalDate,goal:Int,zone:ZoneId=ZoneId.systemDefault()):SleepPattern {
        val nights=calendarDays(days,date,7).filter { it["sleepMinutes"]!=null && (it["sleepCoverage"] ?: 100.0)>=90 }
        val bed=nights.mapNotNull { it.sleepStart?.let { at->sleepClock(at,zone) } };val wake=nights.mapNotNull { it.sleepEnd?.let { at->sleepClock(at,zone) } }
        val center=HealthQuality.median(bed)
        return SleepPattern(nights.size,nights.takeIf { it.isNotEmpty() }?.map { it["sleepMinutes"]!! }?.average(),nights.takeIf { it.isNotEmpty() }?.sumOf { (goal-it["sleepMinutes"]!!).coerceAtLeast(0.0) },center,HealthQuality.median(wake),if(bed.size>=3)HealthQuality.median(bed.map { abs(it-center!!) })else null,nights.count { it["sleepMinutes"]!!>=goal })
    }
    fun week(days:List<HealthDay>,date:LocalDate,prefs:Preferences):ActivityWeek {
        val week=calendarDays(days,date,7);val steps=week.mapNotNull { it["steps"] };val active=week.mapNotNull { it["activeMinutes"] }
        fun closed(d:HealthDay)=d["steps"]?.let { it>=prefs.stepGoal }==true
        var streak=0;var cursor=date;val rows=days.associateBy { it.date }
        while(rows[cursor]?.let(::closed)==true) { streak++;cursor=cursor.minusDays(1) }
        return ActivityWeek(steps.size,steps.takeIf { it.isNotEmpty() }?.sum(),active.takeIf { it.isNotEmpty() }?.sum(),week.count(::closed),streak)
    }
    fun latest(days:List<HealthDay>,date:LocalDate,key:String):Pair<LocalDate,Double>? = days.filter { it.date<=date && it[key]!=null }.maxByOrNull { it.date }?.let { it.date to it[key]!! }
    fun workouts(days:List<HealthDay>,date:LocalDate,count:Int)=days.filter { it.date<=date && it.date>date.minusDays(count.toLong()) }.flatMap { it.recordedWorkouts }.distinctBy { it.id }.sortedByDescending { it.start }
    /** Minute bins describe observed samples, never infer time in an unobserved gap. */
    fun heartBands(workout:RecordedWorkout):List<Int> = listOf(0.0..99.999,100.0..119.999,120.0..139.999,140.0..159.999,160.0..240.0).map { range->workout.heart.distinctBy { it.at.epochSecond/60 }.count { it.bpm in range } }
    fun pace(workout:RecordedWorkout):Double?=workout.values["distanceKm"]?.takeIf { it>0 }?.let { workout.minutes/it }
}
fun sleepClockLabel(minutes:Double?):String=minutes?.roundToInt()?.let { val m=Math.floorMod(it,1440);"%02d:%02d".format(m/60,m%60) } ?: "—"
