package com.forma.health
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter

internal fun dataStamp(at:Instant?)=at?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM · HH:mm")) ?: "Not shared"
@Composable fun DataArrivalCard(state:FormaState,onClick:()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,color=c.surface,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(if(state.busy)Icons.Outlined.Sync else Icons.Outlined.Storage,null,tint=c.primary,modifier=Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start=12.dp)) {
                Text(if(state.busy)"Syncing records" else if(state.data.demo)"Sample records" else HealthQuality.freshness(state.data.report?.latestRecordAt),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                Text(if(state.data.demo)"Connect with the watch icon" else "Latest record · ${dataStamp(state.data.report?.latestRecordAt)}",fontSize=10.sp,lineHeight=16.sp,color=c.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=c.onSurfaceVariant,modifier=Modifier.size(19.dp))
        }
    }
}
@Composable fun WatchConnectionPanel(state:FormaState,onConnect:()->Unit,onSync:()->Unit,onCancel:()->Unit,onHistory:()->Unit,onSamsungHealth:()->Unit,onWatchApp:()->Unit,onHealthSettings:()->Unit,onBackground:(Boolean)->Unit,onAutomatic:(Boolean)->Unit={},onCompanion:()->Unit={}) {
    val c=state.connection
    HealthPanel(SignalLime) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.Watch,null,tint=SignalLime,modifier=Modifier.size(36.dp))
            Column(Modifier.weight(1f)) { Text("Samsung Health",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(if(c.hasReadAccess)"Read access allowed" else "Not connected",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if(!c.hasReadAccess)Text("Sync your watch in Samsung Health, then allow Forma access.",fontSize=12.sp,lineHeight=19.sp)
        else { ValueRow("Latest shared record",dataStamp(state.data.report?.latestRecordAt));ValueRow("Last sync",dataStamp(state.data.syncedAt)) }
        Button(onClick=if(c.hasReadAccess)onSync else onConnect,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(if(state.busy)"Syncing…" else if(c.hasReadAccess)"Sync now" else "Allow access & connect") }
        if(state.busy) {
            Text(state.syncProgress.label,fontSize=10.sp,lineHeight=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.syncProgress.totalDays>0)LinearProgressIndicator(progress={state.syncProgress.completedDays.toFloat()/state.syncProgress.totalDays},modifier=Modifier.fillMaxWidth(),drawStopIndicator={})
            TextButton(onClick=onCancel) { Text("Pause history import") }
        }
    }
    ActionRow("Watch companion",if(state.watch.enabled)state.watch.device ?: "Enabled · waiting for watch" else "Optional direct measurements",Icons.Outlined.Sensors,onCompanion)
    DetailDisclosure("Sync settings") {
        ActionRow("Change read access",icon=Icons.Outlined.LockOpen,onClick=onConnect)
        if(c.historyAvailable)OutlinedButton(onClick=onHistory,enabled=!state.busy && c.hasReadAccess,modifier=Modifier.fillMaxWidth()) { Text(if(c.historyAllowed)"Refresh available history" else "Allow older history") }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Refresh while open",fontSize=13.sp);Text("Checks each minute",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked=state.prefs.automaticSync,onCheckedChange=onAutomatic,enabled=c.hasReadAccess && !state.busy)
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Background refresh",fontSize=13.sp);Text(if(c.backgroundAvailable)"Android controls timing" else "Unavailable on this phone",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked=state.prefs.backgroundSync,onCheckedChange=onBackground,enabled=c.hasReadAccess && c.backgroundAvailable && !state.busy)
        }
    }
    DetailDisclosure("Help with syncing") {
        Text("Samsung Health must share each reading with Health Connect. The watch companion uses your existing Galaxy Wearable pairing.",fontSize=11.sp,lineHeight=18.sp)
        ActionRow("Samsung Health",icon=Icons.Outlined.FavoriteBorder,onClick=onSamsungHealth)
        ActionRow("Galaxy Wearable",icon=Icons.Outlined.Watch,onClick=onWatchApp)
        ActionRow("Sharing settings",icon=Icons.Outlined.Settings,onClick=onHealthSettings)
    }
}
