package com.forma.health

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Describe the existing calculation. Never adjust a score to make the cards differ. */
object ScoreDiagnostics {
    private fun precise(value:Double)=String.format(Locale.US,"%.2f",value)
    fun matchingReason(sleep:Score,recovery:Score,energy:EnergyResult):String? {
        val value=sleep.value ?: return null
        if(recovery.value!=value || energy.score.value!=value)return null
        val start=energy.points.firstOrNull()?.second ?: return null
        val end=energy.points.lastOrNull()?.second ?: return null
        val adjustment=end-start
        return "Sleep and recovery both round to $value. Energy started at ${precise(start)} and its net recorded adjustment is ${if(adjustment>=0)"+" else ""}${precise(adjustment)} points, so it also rounds to $value."
    }
    fun activityStatus(day:HealthDay?,energy:EnergyResult,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):String {
        if(energy.points.isEmpty())return "No usable energy trace. Sleep, baseline vitals and recorded activity are required."
        val at=energy.recordedAt ?: return "Activity timestamp unavailable. The energy value cannot be labeled current."
        val stamp=at.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM · HH:mm"))
        val recorded="Activity window ends $stamp · ${day?.samples?.size ?: 0} recorded buckets."
        return if(day?.date==now.atZone(zone).toLocalDate() && Duration.between(at,now)>Duration.ofHours(2))
            "$recorded Waiting for newer Samsung-shared activity; this is not a live energy reading."
        else recorded
    }
    fun report(day:HealthDay?,sleep:Score,recovery:Score,energy:EnergyResult,goal:Int,source:String):String=buildString {
        appendLine("Forma score details · ${day?.date ?: "no selected day"}")
        appendLine("Source: $source · sleep goal: $goal minutes")
        fun score(label:String,s:Score) {
            appendLine("$label: ${s.value ?: "unavailable"}/100 · ${s.confidence}")
            val total=s.factors.sumOf { it.weight }
            s.factors.forEach { f->appendLine("  ${f.label}: ${precise(f.value)} · ${precise(f.weight/total*100)}% of available weight · ${f.detail}") }
        }
        score("Sleep",sleep);score("Recovery",recovery)
        appendLine("Energy/body battery: ${energy.score.value ?: "unavailable"}/100 · ${energy.score.confidence}")
        energy.points.firstOrNull()?.let { appendLine("  Starting readiness: ${precise(it.second)}") }
        energy.points.lastOrNull()?.let { last->appendLine("  Unrounded energy: ${precise(last.second)} · net adjustment: ${precise(last.second-energy.points.first().second)}") }
        appendLine(activityStatus(day,energy))
        appendLine("Activity window end (ISO): ${energy.recordedAt ?: "not shared"}")
        appendLine("Recorded activity buckets: ${day?.samples?.size ?: 0}")
        fun amount(label:String,values:List<Double>) { appendLine("$label: ${if(values.isEmpty())"not shared" else precise(values.sum())}") }
        amount("Steps used by energy",day?.samples.orEmpty().mapNotNull { it.steps })
        amount("Exercise minutes used by energy",day?.samples.orEmpty().mapNotNull { it.activeMinutes })
        appendLine("HR-bearing energy buckets: ${day?.samples.orEmpty().count { it.heartRate!=null }}")
        amount("Explicit rest minutes",day?.samples.orEmpty().mapNotNull { it.restMinutes })
        for(key in listOf("sleepMinutes","sleepEfficiency","sleepCoverage","sleepRegularity","hrv","restingHeartRate","sleepHeartRate","steps","activeMinutes"))
            appendLine("$key: ${day?.get(key)?.let(::precise) ?: "not shared"}")
        matchingReason(sleep,recovery,energy)?.let(::appendLine)
        appendLine("Direct-watch HR and steps are displayed separately; they do not currently feed these scores.")
        appendLine("These are experimental Forma estimates, independent of Samsung's proprietary scores.")
    }
}
