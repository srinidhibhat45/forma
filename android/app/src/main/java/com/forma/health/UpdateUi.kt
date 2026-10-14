package com.forma.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun AppUpdateBanner(release:AppRelease,onClick:()->Unit) {
    Surface(onClick=onClick,color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(23.dp))
            Column(Modifier.weight(1f).padding(start=13.dp)) {
                Text("A fresh Forma is ready",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                Text("${release.versionName} · Update in place",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
            }
            Text("Update",fontSize=11.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable fun AppUpdatePanel(state:AppUpdateState,onCheck:()->Unit,onInstall:()->Unit,onCancel:()->Unit,onAutomatic:(Boolean)->Unit,onInstallationSettings:()->Unit={}) {
    val colors=MaterialTheme.colorScheme
    Row(verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Outlined.SystemUpdate,null,tint=colors.primary,modifier=Modifier.size(42.dp))
        Column(Modifier.padding(start=17.dp)) {
            Text("Forma ${BuildConfig.VERSION_NAME}",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
            Text("Installed build ${BuildConfig.VERSION_CODE}",fontSize=11.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
        }
    }
    val release=state.release
    Text(when {
        state.downloading->"${if(state.progress>=1)"Verifying your update" else "Downloading your update"}…"
        state.checking->"Checking GitHub for a new release…"
        release!=null->if(state.ready)"Your update is ready to install." else "A new version is available."
        state.hasChecked && state.noReleases->"No releases have been published yet."
        state.hasChecked->"You have the latest available Forma."
        else->"Keep Forma moving forward."
    },fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.8).sp)
    if(release!=null) {
        Text("${release.versionName} · build ${release.versionCode} · ${"%.1f".format(release.bytes/1024.0/1024)} MB",fontSize=12.sp,color=colors.onSurfaceVariant)
        if(release.notes.isNotBlank()) DetailDisclosure("What’s new") { Text(release.notes,fontSize=12.sp,lineHeight=20.sp,color=colors.onSurfaceVariant) }
        if(state.downloading) {
            LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth().height(4.dp),drawStopIndicator={})
            Text("${(state.progress*100).toInt()}%",fontSize=11.sp,color=colors.onSurfaceVariant)
            TextButton(onClick=onCancel) { Text("Cancel download") }
        } else Button(onClick=onInstall,enabled=!state.checking,modifier=Modifier.fillMaxWidth().height(49.dp),shape=RoundedCornerShape(12.dp)) {
            Text(if(state.ready)"Install update" else "Download & update")
        }
    }
    state.error?.let { Text(it,fontSize=12.sp,lineHeight=21.sp,color=colors.error) }
    OutlinedButton(onClick=onCheck,enabled=!state.checking && !state.downloading,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
        Text(if(state.checking)"Checking…" else "Check for updates",fontSize=12.sp)
    }
    HorizontalDivider(color=colors.outline)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Automatic update checks",fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            Text("While open and periodically in the background",fontSize=10.sp,lineHeight=17.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
        }
        Switch(checked=state.automatic,onCheckedChange=onAutomatic)
    }
    if(state.checkedAt>0) Text("Last checked ${Instant.ofEpochMilli(state.checkedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))}",fontSize=10.sp,color=colors.onSurfaceVariant)
    Text("Signed updates from GitHub · Android confirms installation.",fontSize=10.sp,lineHeight=17.sp,color=colors.onSurfaceVariant)
    DetailDisclosure("Installation help") {
        Text("Allow Forma to install updates when Android asks. Samsung Auto Blocker may require a change in Security settings. Your local records stay in place; health data is never sent with update requests.",fontSize=11.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
        TextButton(onClick=onInstallationSettings) { Text("Open installation security settings",fontSize=11.sp) }
    }
}
