package com.forma.health

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable fun ScoreDiagnosticsPanel(title:String,day:HealthDay?,sleep:Score,recovery:Score,energy:EnergyResult,goal:Int,source:String) {
    val context=LocalContext.current
    var copied by remember(title,day,sleep,recovery,energy) { mutableStateOf(false) }
    HealthPanel(SignalCyan) {
        ScoreDiagnostics.matchingReason(sleep,recovery,energy)?.let {
            Text("Why the scores match",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=SignalCyan)
            Text(it,fontSize=11.sp,lineHeight=18.sp)
        }
        if(title=="Recovery score") {
            val total=recovery.factors.sumOf { it.weight }
            if(total>0)Text("Available inputs · "+recovery.factors.joinToString(" · ") { "${it.label} ${"%.0f".format(it.weight/total*100)}%" },fontSize=11.sp,lineHeight=18.sp)
            if(recovery.factors.none { it.label=="Heart rate variability" })Text("HRV is not contributing: a current shared HRV reading and at least seven prior matching days are needed.",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(ScoreDiagnostics.activityStatus(day,energy),fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(title=="Body energy") {
            Text("Samsung-shared hourly records drive this estimate. Live companion heart rate and steps currently update their own displays, not energy.",fontSize=11.sp,lineHeight=18.sp)
            Text("${day?.samples?.mapNotNull { it.steps }?.takeIf { it.isNotEmpty() }?.sum()?.let { measurement(it)+" steps" } ?: "No step counts"} in the energy buckets · ${day?.samples.orEmpty().count { it.heartRate!=null }} buckets with heart rate",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick={
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip=ClipData.newPlainText("Forma score details",ScoreDiagnostics.report(day,sleep,recovery,energy,goal,source))
            if(android.os.Build.VERSION.SDK_INT>=33)clip.description.extras=android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) }
            clipboard.setPrimaryClip(clip);copied=true
        },modifier=Modifier.fillMaxWidth()) { Text(if(copied)"Score details copied" else "Copy score details") }
        Text("Copies only this date’s score inputs and calculation details. Review them before sharing.",fontSize=9.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
