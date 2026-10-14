package com.forma.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable fun DetailDisclosure(title:String,summary:String?=null,content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded=!expanded }.heightIn(min=48.dp).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title,fontSize=13.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold)
                summary?.let { Text(it,fontSize=10.sp,lineHeight=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded)"Collapse $title" else "Expand $title",Modifier.size(22.dp))
        }
        if(expanded)Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun ActionRow(title:String,subtitle:String?=null,icon:ImageVector,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=52.dp).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal=13.dp)) {
            Text(title,fontSize=13.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold)
            subtitle?.let { Text(it,fontSize=10.sp,lineHeight=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(19.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun ValueRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
        Text(value,fontSize=11.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
    }
}
