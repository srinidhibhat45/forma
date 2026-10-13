package com.forma.health

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun DirectWatchCard(state:WatchLinkState,onSetup:()->Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.frame) { while(true) { now=Instant.now();delay(1000) } }
    HealthSection("Direct from your watch",if(state.connected)"Nearby companion" else "Watch link",SignalCyan,"Setup ↗",onSetup) {
        HealthPanel(SignalCyan) {
            if(!state.enabled)Text("Enable the Forma watch companion for direct heart-rate measurements and daily activity updates.",fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val f=state.frame
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OverviewFact("Heart rate",measurement(f?.currentHeart(now)),"bpm · measured on watch",SignalPink,Modifier.weight(1f))
                    OverviewFact("Watch steps",measurement(f?.currentSteps(now,ZoneId.systemDefault())),"Daily count · last 5 min",SignalLime,Modifier.weight(1f))
                }
                Text(state.device ?: "Open Forma on the paired watch",fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                fun stamp(at:Instant?)=at?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM · HH:mm:ss")) ?: "No reading yet"
                Text("HR measured · ${stamp(f?.heartAt)}\nSteps measured · ${stamp(f?.stepsAt)}",fontSize=10.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(f?.calories!=null)Text("Watch-reported daily calories · ${measurement(f.calories)} kcal · ${stamp(f.caloriesAt)}",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.error ?: if(state.connected)"Tap Measure heart rate on your watch for a 60-second live check. Daily activity arrives when Wear OS publishes it." else "Companion not reachable nearby. Keep Galaxy Wearable connected and install the matching Forma watch APK.",fontSize=10.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable fun DirectWatchSetup(state:WatchLinkState,onEnable:(Boolean)->Unit,onFind:()->Unit,onDownload:()->Unit) {
    Text("Watch measurements.\nStraight to your phone.",fontSize=26.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold)
    Text("Install Forma’s companion on your Galaxy Watch once, then allow heart-rate and activity access on the watch. Your existing Galaxy Wearable pairing is reused; no separate Bluetooth pairing or account is needed.",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick=onDownload,modifier=Modifier.fillMaxWidth()) { Text("Get the watch companion") }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) { Text("Receive watch measurements",fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text("Optional · matching Forma companion required",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked=state.enabled,onCheckedChange=onEnable)
    }
    if(state.enabled)OutlinedButton(onClick=onFind,modifier=Modifier.fillMaxWidth()) { Text("Find my Forma watch") }
    Text(if(state.connected)"Connected nearby · ${state.device ?: "Forma watch"}" else "No nearby Forma companion connected",fontSize=12.sp,color=if(state.connected)SignalCyan else MaterialTheme.colorScheme.onSurfaceVariant)
    Text("On the watch, choose Enable daily activity for batched steps, distance, floors and calories. Choose Measure heart rate for a 60-second check while the watch app is visible. This does not take over your Samsung workouts. Heart rate expires from the live display after 30 seconds; fresh watch steps can update today’s step ring for five minutes.",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Samsung Health remains the source for sleep, HRV, workout history, and the other daily totals. Direct-watch calories remain labeled separately because they are not guaranteed to equal Samsung’s active calories. Forma’s energy and recovery estimates are experimental and are recalculated from shared historical records.",fontSize=11.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Transport uses Google Play services’ encrypted Wear OS Data Layer. Forma accepts nearby companion messages only; the underlying transport can use Google’s encrypted relay if connectivity changes. Turning this off discards the phone’s direct-watch cache and rejects new messages. Choose Stop daily activity on the watch to stop its monitoring.",fontSize=10.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
