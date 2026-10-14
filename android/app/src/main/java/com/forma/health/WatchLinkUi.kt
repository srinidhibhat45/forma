package com.forma.health

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun DirectWatchCard(state:WatchLinkState,onSetup:()->Unit) {
    ActionRow("Watch companion",if(state.connected)state.device ?: "Nearby" else "Not reachable",androidx.compose.material.icons.Icons.Outlined.Watch,onSetup)
}
@Composable fun DirectWatchSetup(state:WatchLinkState,onEnable:(Boolean)->Unit,onFind:()->Unit,onDownload:()->Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.frame) { while(true) { now=Instant.now();delay(1000) } }
    HealthPanel(SignalCyan) {
        Text(state.device ?: "Forma on your watch",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Text(if(state.connected)"Connected nearby" else "Open the companion on your paired watch",fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.enabled)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OverviewFact("Live heart rate",measurement(state.frame?.currentHeart(now)),"bpm · 60-second check",SignalPink,Modifier.weight(1f))
            OverviewFact("Watch steps",measurement(state.frame?.currentSteps(now,ZoneId.systemDefault())),"Fresh within 5 min",SignalLime,Modifier.weight(1f))
        }
    }
    OutlinedButton(onClick=onDownload,modifier=Modifier.fillMaxWidth()) { Text("Watch install guide") }
    Text("Encrypted Wear OS transport may use Google’s encrypted relay.",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text("Receive watch measurements",fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        Switch(checked=state.enabled,onCheckedChange=onEnable)
    }
    if(state.enabled)OutlinedButton(onClick=onFind,modifier=Modifier.fillMaxWidth()) { Text("Find my Forma watch") }
    state.error?.let { Text(it,fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.error) }
    DetailDisclosure("How to use the companion") {
        Text("On your watch: Measure heart rate for a 60-second check, or Enable daily activity for batched activity readings. Uses your Galaxy Wearable pairing and does not take over Samsung workouts.",fontSize=11.sp,lineHeight=19.sp)
        Text("Sleep, HRV, history and score inputs still come from Samsung Health. Live companion readings update their own displays. Turning this off clears the phone's watch cache; Stop daily activity on the watch stops monitoring.",fontSize=11.sp,lineHeight=19.sp)
        state.frame?.let { f->ValueRow("Heart measured",dataStamp(f.heartAt));ValueRow("Steps measured",dataStamp(f.stepsAt));f.calories?.let { ValueRow("Watch daily calories","${measurement(it)} kcal") } }
    }
}
