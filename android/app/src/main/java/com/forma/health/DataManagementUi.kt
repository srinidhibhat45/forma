package com.forma.health

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.format.DateTimeFormatter

@Composable fun DataManagementPanel(state:FormaState,onImport:()->Unit,onExport:()->Unit,onCoverage:()->Unit,onPrivacy:()->Unit,onClear:()->Unit) {
    val recorded=state.data.days.filter { it.values.isNotEmpty() || it.recordedWorkouts.isNotEmpty() }
    val fields=healthCatalog.count { m->recorded.any { it[m.key]!=null } }
    HealthPanel(SignalCyan) {
        Text(if(state.data.demo)"Sample data" else if(state.data.source=="Samsung Health · Health Connect")"Samsung Health records" else "Imported records",fontSize=16.sp,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            OverviewFact("Recorded days",recorded.size.toString(),"On this phone",SignalCyan,Modifier.weight(1f))
            OverviewFact("Measurements",fields.toString(),"With shared values",SignalCyan,Modifier.weight(1f))
        }
        if(recorded.isNotEmpty())ValueRow("Date range",recorded.first().date.format(DateTimeFormatter.ofPattern("d MMM"))+" — "+recorded.last().date.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
    }
    ActionRow("Data availability","See what has been shared",Icons.Outlined.BarChart,onCoverage)
    Button(onClick=onExport,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload,null,Modifier.size(18.dp));Text(if(state.data.demo)"Export sample records" else "Export health records",Modifier.padding(start=8.dp)) }
    DetailDisclosure("File import","Advanced") {
        Text("Forma JSON · up to 5 MB. Import pauses Samsung refresh.",fontSize=11.sp,lineHeight=18.sp)
        OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text("Import Forma JSON") }
    }
    ActionRow("Privacy",icon=Icons.Outlined.Shield,onClick=onPrivacy)
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick={confirm=true}) { Text("Clear local data",color=MaterialTheme.colorScheme.error) }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Clear Forma data?")},text={Text("Removes Forma records, notes and preferences. Samsung Health is unchanged.")},confirmButton={TextButton(onClick={onClear();confirm=false}) { Text("Clear data") }},dismissButton={TextButton(onClick={confirm=false}) { Text("Keep my data") }})
}
@Composable fun DataCoveragePanel(state:FormaState) {
    Text(if(state.data.demo)"Sample records" else state.data.source,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    val end=state.data.days.last().date
    Text("Past 28 days · ending $end",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    val days=calendarDays(state.data.days,end,28)
    listOf("Heart","Vitals","Movement","Sleep","Body","Nutrition").forEach { group->
        Text(group,fontSize=16.sp,fontWeight=FontWeight.Bold)
        healthCatalog.filter { it.group==group }.forEach { metric->
            val count=days.count { it[metric.key]!=null };ValueRow(metric.label,if(count==0)"Not shared" else "$count days")
        }
    }
    state.data.report?.issues?.takeIf { it.isNotEmpty() }?.let { issues->DetailDisclosure("Sync issues","${issues.size} reported") { issues.forEach { Text(it,fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.error) } } }
}
@Composable fun PrivacyPanel(onSettings:()->Unit) {
    ValueRow("Storage","Private on this phone");ValueRow("Android backups","Disabled");ValueRow("Ads & analytics","None")
    Text("You choose which records Forma can read.",fontSize=12.sp,lineHeight=19.sp)
    OutlinedButton(onClick=onSettings,modifier=Modifier.fillMaxWidth()) { Text("Manage health permissions") }
    DetailDisclosure("Full privacy details") {
        Text("Forma reads Samsung Health through Health Connect after permission. Records, goals and personal logs stay in private app storage. Exports go to the location you choose; personal notes, manual activity and water logs are not included in health-record exports. GitHub update requests contain no health records. The optional watch companion uses the encrypted Wear OS Data Layer, which may use Google's encrypted relay when connectivity changes. Forma accepts nearby matching companion messages. Clear Forma data or uninstall to remove local storage; revoke read permissions in Health Connect to stop access.",fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
