package com.forma.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter

private fun stamp(at:Instant?)=at?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) ?: "Not shared yet"

@Composable fun DataArrivalCard(state:FormaState,onClick:()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,color=c.surface,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(if(state.busy)Icons.Outlined.Sync else Icons.Outlined.Watch,null,tint=c.primary,modifier=Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start=13.dp)) {
                Text(if(state.busy)state.syncProgress.label else if(state.data.demo)"Connect your watch data" else if(state.data.days.none { it.values.isNotEmpty() || it.recordedWorkouts.isNotEmpty() })"No shared measurements yet" else if(!state.prefs.automaticSync && !state.prefs.backgroundSync)"Health refresh paused" else HealthQuality.freshness(state.data.report?.latestRecordAt),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text(if(state.data.demo)"Samsung Health → Forma" else "Latest reported record · ${stamp(state.data.report?.latestRecordAt)}",fontSize=10.sp,lineHeight=16.sp,color=c.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=c.onSurfaceVariant,modifier=Modifier.size(19.dp))
        }
    }
}

@Composable fun WatchConnectionPanel(state:FormaState,onConnect:()->Unit,onSync:()->Unit,onCancel:()->Unit,
    onHistory:()->Unit,onSamsungHealth:()->Unit,onWatchApp:()->Unit,onHealthSettings:()->Unit,
    onBackground:(Boolean)->Unit,onImport:()->Unit,onExport:()->Unit,onAutomatic:(Boolean)->Unit={}) {
    val colors=MaterialTheme.colorScheme;val connection=state.connection
    val report=state.data.report;val native=!state.data.demo && state.data.source=="Samsung Health · Health Connect"
    Surface(color=colors.primary,shape=RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(21.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Watch,null,modifier=Modifier.size(44.dp),tint=colors.onPrimary)
            Column(Modifier.padding(start=18.dp)) {
                Text("Your watch.\nYour rhythm.",fontSize=27.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold,color=colors.onPrimary)
                Text(if(native)"${state.data.days.count { it.values.isNotEmpty() || it.recordedWorkouts.isNotEmpty() }} days with shared measurements" else "Galaxy Watch6 Classic",fontSize=11.sp,color=colors.onPrimary,modifier=Modifier.padding(top=9.dp))
            }
        }
    }
    Text(when { state.busy->"Your records are arriving.";native->"Shared data, in perspective.";connection.hasReadAccess->"Access allowed. Waiting for records.";else->"Three steps. One connection." },fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold)
    ConnectionStep("01","Sync Samsung Health",if(connection.samsungInstalled)"Open Samsung Health and let your paired watch finish syncing. In its Settings → Health Connect, enable sharing for your measurements." else "Install Samsung Health and connect your watch in Galaxy Wearable.")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick=onSamsungHealth,modifier=Modifier.weight(1f)) { Text("Samsung Health",fontSize=11.sp) }
        OutlinedButton(onClick=onWatchApp,modifier=Modifier.weight(1f)) { Text("Galaxy Wearable",fontSize=11.sp) }
    }
    ConnectionStep("02","Allow Forma to read",if(connection.hasReadAccess)"Read access is allowed. Partial permissions work; missing fields stay unavailable." else "Choose the measurements Forma can read. Allow older history when offered to bring in up to a year of shared records.")
    Button(onClick=if(connection.hasReadAccess)onSync else onConnect,enabled=!state.busy,modifier=Modifier.fillMaxWidth().height(49.dp),shape=RoundedCornerShape(12.dp)) {
        if(state.busy)CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp,color=colors.onPrimary)
        Text(if(state.busy)"Reading your records…" else if(connection.hasReadAccess)"Sync now" else "Allow access & connect",fontSize=12.sp,modifier=Modifier.padding(start=8.dp))
    }
    if(state.busy) {
        Text(state.syncProgress.label,fontSize=11.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
        if(state.syncProgress.totalDays>0)LinearProgressIndicator(progress={state.syncProgress.completedDays.toFloat()/state.syncProgress.totalDays},modifier=Modifier.fillMaxWidth(),drawStopIndicator={})
        TextButton(onClick=onCancel) { Text("Pause history import") }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick=onConnect,enabled=!state.busy) { Text("Change read access",fontSize=11.sp) }
        TextButton(onClick=onHealthSettings) { Text("Sharing settings",fontSize=11.sp) }
    }
    ConnectionStep("03","Recent first. History follows.","Forma loads recent records first, then imports older days in resumable batches. It refreshes while open and can read in the background when you allow it. Samsung must publish the watch records first.")
    if(native) {
        Text("Latest reported record · ${stamp(report?.latestRecordAt)}",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        Text("Last read · ${stamp(state.data.syncedAt)}\n${HealthQuality.freshness(report?.latestRecordAt)}",fontSize=11.sp,lineHeight=19.sp,color=colors.onSurfaceVariant)
        Text("Recorded days · ${state.data.days.first().date} to ${state.data.days.last().date}",fontSize=11.sp,color=colors.onSurfaceVariant)
        Text(if(report?.historyComplete==true)"Available history scan complete" else "History import can continue on the next sync",fontSize=11.sp,color=colors.onSurfaceVariant)
        report?.devices?.forEach { Text("Watch metadata · $it",fontSize=11.sp,color=colors.onSurfaceVariant) }
    }
    if(connection.historyAvailable)OutlinedButton(onClick=onHistory,enabled=!state.busy && connection.hasReadAccess,modifier=Modifier.fillMaxWidth()) { Text(if(connection.historyAllowed)"Refresh available history" else "Allow older history",fontSize=12.sp) }
    else Text("Older-history access is unavailable on this Health Connect version. Initial import uses a recent window; previously saved days remain on your phone.",fontSize=11.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Refresh while open",fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text("Checks for new shared records each minute",fontSize=10.sp,color=colors.onSurfaceVariant) }
        Switch(checked=state.prefs.automaticSync,onCheckedChange=onAutomatic)
    }
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Background refresh",fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text(if(connection.backgroundAvailable)"Optional · Android controls timing" else "Requires a supported Health Connect version",fontSize=10.sp,lineHeight=17.sp,color=colors.onSurfaceVariant) }
        Switch(checked=state.prefs.backgroundSync,onCheckedChange=onBackground,enabled=connection.hasReadAccess && connection.backgroundAvailable)
    }
    HorizontalDivider(color=colors.outline)
    Text("What has arrived",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
    val recent=state.data.days.takeLast(28).takeIf { native }.orEmpty()
    listOf("Steps & movement" to listOf("steps","activeMinutes"),"Calories" to listOf("activeCalories","totalCalories"),"Speed, power & VO₂" to listOf("speedKmh","powerWatts","vo2Max"),"Sleep & stages" to listOf("sleepMinutes"),"Resting / sleeping heart rate" to listOf("restingHeartRate","sleepHeartRate"),"HRV" to listOf("hrv"),"Oxygen & breathing" to listOf("spo2","respiratoryRate"),"Skin temperature" to listOf("skinTemperature","skinTemperatureDelta"),"Weight & body composition" to listOf("weightKg","bodyFat","leanMassKg"),"Blood pressure & glucose" to listOf("systolic","bloodGlucose")).forEach { (label,keys)->
        val count=recent.count { d->keys.any { d[it]!=null } }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,fontSize=11.sp,modifier=Modifier.weight(1f))
            Text(if(count==0)"Not shared" else "$count days",fontSize=11.sp,color=if(count==0)colors.onSurfaceVariant else colors.primary)
        }
    }
    report?.issues?.forEach { Text(it,fontSize=11.sp,lineHeight=18.sp,color=colors.error) }
    Text("Samsung Health may combine phone and watch data. A shared record is only identified as watch-origin when its device metadata says so. Forma cannot force watch uploads or recover history Samsung hasn’t shared.",fontSize=11.sp,lineHeight=19.sp,color=colors.onSurfaceVariant)
    Text("Samsung’s proprietary sleep/energy scores, raw ECG and PPG aren’t available through this connection. Forma’s readiness and body-energy scores are explained estimates; seven baseline days are required, and more data does not make them clinically validated.",fontSize=11.sp,lineHeight=19.sp,color=colors.onSurfaceVariant)
    OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text("Import Forma JSON",fontSize=12.sp) }
    OutlinedButton(onClick=onExport,modifier=Modifier.fillMaxWidth()) { Text("Export health records",fontSize=12.sp) }
}

@Composable private fun ConnectionStep(number:String,title:String,description:String) {
    Row(Modifier.fillMaxWidth()) {
        Text(number,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,modifier=Modifier.width(32.dp).padding(top=3.dp))
        Column { Text(title,fontSize=15.sp,fontWeight=FontWeight.SemiBold);Text(description,fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
    }
}
