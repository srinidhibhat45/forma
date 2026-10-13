package com.forma.health

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val MoveOrange=Color(0xFFFF895F)
private val StepsLime=Color(0xFFD8FC70)
private val ExerciseBlue=Color(0xFF96A7FF)
private val HeartRed=Color(0xFFFF718B)

data class RingProgress(val value:Double?,val target:Int) {
    val fraction get()=value?.takeIf { it.isFinite() && it>=0 && target>0 }?.div(target)
    val closed get()=fraction?.let { it>=1 }==true
    val remaining get()=fraction?.let { (target-value!!).coerceAtLeast(0.0) }
}

@Composable fun TodayActivity(day:HealthDay?,prefs:Preferences,onGoals:()->Unit,onMove:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val hasOnlyTotal=day?.get("activeCalories")==null && day?.get("totalCalories")!=null
    val rings=listOf(RingProgress(day?.get("steps"),prefs.stepGoal),RingProgress(day?.get(if(hasOnlyTotal)"totalCalories" else "activeCalories"),prefs.calorieGoal),RingProgress(day?.get("activeMinutes"),prefs.exerciseGoal))
    val accents=listOf(StepsLime,MoveOrange,ExerciseBlue)
    val labels=listOf("Steps",if(hasOnlyTotal)"Shared calories" else "Active calories","Exercise")
    val units=listOf("steps","kcal","min")
    Surface(color=colors.surface,shape=RoundedCornerShape(23.dp)) {
        Column(Modifier.padding(19.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Make today count.",fontSize=23.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.8).sp)
                    Text("YOUR ACTIVITY / YOUR TARGETS",fontSize=9.sp,letterSpacing=1.5.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=7.dp))
                }
                IconButton(onClick=onGoals,modifier=Modifier.size(35.dp)) { Icon(Icons.Outlined.Tune,"Edit ring targets",tint=colors.onSurfaceVariant,modifier=Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(top=22.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Box(Modifier.weight(1f).aspectRatio(1f).clickable(onClick=onMove).semantics {
                    contentDescription="Activity rings. "+rings.mapIndexed { i,r->"${labels[i]}: ${r.value?.let { measurement(it) } ?: "not shared"} of ${r.target} ${units[i]}" }.joinToString(". ")
                },contentAlignment=Alignment.Center) {
                    val progress=rings.map { animateFloatAsState((it.fraction ?: 0.0).coerceIn(0.0,1.0).toFloat(),tween(850),label="Ring progress").value }
                    Canvas(Modifier.fillMaxSize()) {
                        val width=size.minDimension*.076f
                        rings.forEachIndexed { i,r->
                            val inset=width/2+i*width*1.55f
                            val area=Size(size.width-2*inset,size.height-2*inset)
                            drawArc(accents[i].copy(alpha=if(r.value==null).08f else .14f),-90f,360f,false,Offset(inset,inset),area,style=Stroke(width,cap=StrokeCap.Round))
                            if(progress[i]>0)drawArc(accents[i],-90f,progress[i]*360f,false,Offset(inset,inset),area,style=Stroke(width,cap=StrokeCap.Round))
                        }
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("${rings.count { it.closed }}",fontSize=32.sp,fontWeight=FontWeight.Bold,lineHeight=35.sp)
                        Text("CLOSED",fontSize=8.sp,lineHeight=11.sp,letterSpacing=.7.sp,color=colors.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1.02f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    rings.forEachIndexed { i,r->
                        Column {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Surface(color=accents[i],shape=CircleShape,modifier=Modifier.size(5.dp)) {}
                                Text(labels[i],fontSize=11.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(start=6.dp))
                            }
                            Row(verticalAlignment=Alignment.Bottom,modifier=Modifier.padding(top=3.dp)) {
                                Text(measurement(r.value),fontSize=23.sp,fontWeight=FontWeight.Bold,color=accents[i],letterSpacing=(-.7).sp)
                                Text(" / ${measurement(r.target.toDouble())}",fontSize=10.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(bottom=4.dp))
                            }
                            Text(when { r.value==null->"Not shared";r.closed->"Closed · ${(r.fraction!!*100).roundToInt()}%";else->"${measurement(r.remaining)} ${units[i]} to go" },fontSize=9.sp,lineHeight=14.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
                        }
                    }
                }
            }
            if(hasOnlyTotal)Text("Calorie ring uses shared total calories; Samsung may publish exercise calories only.",fontSize=10.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(bottom=12.dp))
            HorizontalDivider(color=colors.outline)
            Row(Modifier.fillMaxWidth().padding(top=13.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${measurement(day?.get("distanceKm"),1)} km",fontSize=11.sp,color=colors.onSurfaceVariant)
                Text("${measurement(day?.get("floors"))} floors",fontSize=11.sp,color=colors.onSurfaceVariant)
                Text("View movement ↗",fontSize=11.sp,color=StepsLime,modifier=Modifier.clickable(onClick=onMove))
            }
        }
    }
}

@Composable fun TodayHeart(day:HealthDay?,demo:Boolean,onClick:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(onClick=onClick,color=colors.surface,shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.FavoriteBorder,null,tint=HeartRed,modifier=Modifier.size(20.dp))
                Text("Heart rate",fontSize=14.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).padding(start=9.dp))
                Text(if(demo)"SAMPLE" else "LATEST RECORDED",fontSize=8.sp,letterSpacing=1.sp,color=colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.Bottom) {
                Text(measurement(day?.get("heartRateLatest")),fontSize=44.sp,lineHeight=48.sp,fontWeight=FontWeight.Bold,letterSpacing=(-2).sp)
                Text(" bpm",fontSize=13.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment=Alignment.End,modifier=Modifier.padding(bottom=6.dp)) {
                    Text(if(demo)"Sample reading" else day?.heartRecordedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM · HH:mm")) ?: "No timestamp shared",fontSize=10.sp,lineHeight=16.sp,color=colors.onSurfaceVariant)
                    Text("Resting · ${measurement(day?.get("restingHeartRate"))} bpm",fontSize=10.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                }
            }
            if(day?.heartTimeline?.isNotEmpty()==true) {
                val points=day.heartTimeline
                val low=(points.minOf { it.bpm }-5);val high=points.maxOf { it.bpm }+5
                Canvas(Modifier.fillMaxWidth().height(44.dp).padding(top=8.dp).semantics { contentDescription="Recorded hourly heart-rate averages. Tap card for history." }) {
                    val first=day.date.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond
                    val daySeconds=Duration.between(day.date.atStartOfDay(ZoneId.systemDefault()),day.date.plusDays(1).atStartOfDay(ZoneId.systemDefault())).seconds
                    fun position(p:HeartPoint)=Offset(((p.at.epochSecond-first).toDouble()/daySeconds*size.width).toFloat(),((high-p.bpm)/(high-low)*size.height).toFloat())
                    points.zipWithNext().forEach { (a,b)->if(Duration.between(a.at,b.at)<=Duration.ofMinutes(90))drawLine(HeartRed,position(a),position(b),2.dp.toPx(),cap=StrokeCap.Round) }
                    points.forEach { drawCircle(HeartRed,2.dp.toPx(),position(it)) }
                }
            }
            Text("${measurement(day?.get("heartRateMin"))}–${measurement(day?.get("heartRateMax"))} bpm recorded range · ${measurement(day?.get("heartRateAverage"))} avg",fontSize=10.sp,lineHeight=16.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=9.dp))
        }
    }
}

data class MetricDefinition(val key:String,val label:String,val unit:String,val group:String,val decimals:Int=0)
val healthCatalog=listOf(
    MetricDefinition("totalCalories","Shared total calories","kcal","Movement"),MetricDefinition("speedKmh","Average recorded speed","km/h","Movement",1),MetricDefinition("maxSpeedKmh","Peak recorded speed","km/h","Movement",1),MetricDefinition("powerWatts","Average recorded power","W","Movement"),MetricDefinition("maxPowerWatts","Peak recorded power","W","Movement"),MetricDefinition("vo2Max","Recorded VO₂ max","ml/kg/min","Heart",1),MetricDefinition("basalMetabolicRate","Basal metabolic rate","kcal/day","Body"),MetricDefinition("heightCm","Height","cm","Body",1),
    MetricDefinition("steps","Steps","steps","Movement"),MetricDefinition("activeCalories","Active calories","kcal","Movement"),MetricDefinition("activeMinutes","Recorded exercise","min","Movement"),MetricDefinition("distanceKm","Distance","km","Movement",1),MetricDefinition("floors","Floors climbed","floors","Movement"),
    MetricDefinition("heartRateLatest","Latest heart rate","bpm","Heart"),MetricDefinition("heartRateAverage","Average heart rate","bpm","Heart"),MetricDefinition("heartRateMin","Lowest recorded HR","bpm","Heart"),MetricDefinition("heartRateMax","Highest recorded HR","bpm","Heart"),MetricDefinition("restingHeartRate","Resting heart rate","bpm","Heart"),MetricDefinition("sleepHeartRate","Sleeping heart rate","bpm","Heart"),MetricDefinition("hrv","Heart rate variability","ms","Heart"),
    MetricDefinition("sleepMinutes","Recorded sleep","min","Sleep"),MetricDefinition("sleepEfficiency","Sleep efficiency","%","Sleep"),MetricDefinition("sleepCoverage","Known-stage coverage","%","Sleep"),MetricDefinition("napMinutes","Recorded naps","min","Sleep"),MetricDefinition("sleepRegularity","Imported regularity","%","Sleep"),MetricDefinition("sleepSpo2","Sleeping oxygen","%","Sleep",1),MetricDefinition("sleepRespiratoryRate","Sleeping breathing","/min","Sleep",1),
    MetricDefinition("spo2","Blood oxygen","%","Vitals",1),MetricDefinition("respiratoryRate","Breathing rate","/min","Vitals",1),MetricDefinition("skinTemperature","Skin temperature","°C","Vitals",1),MetricDefinition("skinTemperatureDelta","Skin temperature change","°C","Vitals",2),MetricDefinition("systolic","Systolic pressure","mmHg","Vitals"),MetricDefinition("diastolic","Diastolic pressure","mmHg","Vitals"),MetricDefinition("bloodGlucose","Blood glucose","mmol/L","Vitals",1),
    MetricDefinition("weightKg","Weight","kg","Body",1),MetricDefinition("bodyFat","Body fat","%","Body",1),MetricDefinition("leanMassKg","Lean body mass","kg","Body",1),MetricDefinition("samsungEnergy","Imported Samsung energy","/100","Body")
)

@Composable fun TodayMetrics(day:HealthDay?,onMetric:(String)->Unit,onAll:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom=5.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Your signals",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            TextButton(onClick=onAll) { Text("All metrics ↗",fontSize=11.sp,color=colors.primary) }
        }
        val fields=listOf("hrv","spo2","sleepMinutes","respiratoryRate","weightKg","skinTemperatureDelta")
        fields.chunked(2).forEach { row->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { row.forEach { key->MetricTile(healthCatalog.first { it.key==key },day,Modifier.weight(1f)) { onMetric(key) } } }
        }
    }
}

@Composable private fun MetricTile(metric:MetricDefinition,day:HealthDay?,modifier:Modifier=Modifier,onClick:()->Unit) {
    val colors=MaterialTheme.colorScheme;val value=day?.get(metric.key)
    Surface(onClick=onClick,color=colors.surface,shape=RoundedCornerShape(15.dp),modifier=modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(metric.label,fontSize=10.sp,lineHeight=16.sp,color=colors.onSurfaceVariant)
            Text(if(metric.key in listOf("sleepMinutes","napMinutes"))duration(value) else measurement(value,metric.decimals),fontSize=25.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.8).sp,modifier=Modifier.padding(top=8.dp))
            Text(if(value==null)"Not shared" else if(metric.key=="sleepMinutes")"Recorded sleep" else metric.unit,fontSize=9.sp,lineHeight=14.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
        }
    }
}

@Composable fun MetricExplorer(state:FormaState,onMetric:(String)->Unit) {
    var group by rememberSaveable { mutableStateOf("All") };var query by rememberSaveable { mutableStateOf("") }
    val day=state.data.days.find { it.date==state.selected }
    Text("Every shared signal.\nA little more context.",fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
    OutlinedTextField(value=query,onValueChange={query=it.take(80)},placeholder={Text("Find a metric",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        listOf("All","Movement","Heart","Sleep","Vitals","Body").forEach { label->FilterChip(selected=group==label,onClick={group=label},label={Text(label,fontSize=11.sp)}) }
    }
    val fields=healthCatalog.filter { (group=="All" || it.group==group) && it.label.contains(query,ignoreCase=true) }
    if(fields.isEmpty())Text("No matching metric",fontSize=13.sp)
    fields.chunked(2).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { row.forEach { metric->MetricTile(metric,day,Modifier.weight(1f)) { onMetric(metric.key) } };if(row.size==1)Spacer(Modifier.weight(1f)) } }
    Text("A dash means no shared measurement for this day. Select a metric for its history and recording coverage.",fontSize=11.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun MetricDetail(state:FormaState,key:String) {
    val metric=healthCatalog.first { it.key==key };var length by rememberSaveable(key) { mutableIntStateOf(28) }
    val day=state.data.days.find { it.date==state.selected };val value=day?.get(key)
    Text(if(key in listOf("sleepMinutes","napMinutes"))duration(value) else "${measurement(value,metric.decimals)} ${metric.unit}",fontSize=42.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.5).sp)
    Text(state.selected.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(7,28,90).forEach { count->FilterChip(selected=count==length,onClick={length=count},label={Text("${count}D")}) } }
    val days=calendarDays(state.data.days,state.selected,length);val values=days.mapNotNull { it[key] }
    if(values.isEmpty())Text("No shared records in this range. Allow this measurement in Health Connect and check Samsung Health sharing.",fontSize=13.sp,lineHeight=23.sp)
    else {
        val points=days.mapIndexed { i,d->i.toDouble() to d[key] }
        NativeLineChart(points,values.minOrNull()!!-1,values.maxOrNull()!!+1,MaterialTheme.colorScheme.primary,
            xMin=0.0,xMax=(days.size-1).toDouble(),pointLabel={days[it.roundToInt().coerceIn(days.indices)].date.format(DateTimeFormatter.ofPattern("d MMM"))},valueLabel={measurement(it,metric.decimals)},ticks=listOf(0.0 to days.first().date.format(DateTimeFormatter.ofPattern("d MMM")),(days.size-1).toDouble() to days.last().date.format(DateTimeFormatter.ofPattern("d MMM"))))
        Text("${values.size} / $length days recorded · ${measurement(values.average(),metric.decimals)} ${metric.unit} average",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(when(key) {
        "heartRateLatest"->"Latest recorded sample for each day; this is not a continuous live sensor feed. The Today card shows its timestamp."
        "totalCalories"->"Total calories as shared by Samsung Health. Samsung may publish exercise-tracker calories rather than its full-day activity total. These are not interchangeable with active calories. When active calories are absent, the ring explicitly switches to Shared calories."
        "vo2Max"->"The estimate recorded by the source, not a laboratory measurement inferred by Forma. Workout/device availability and measurement conditions affect this value."
        "activeMinutes"->"Duration of shared exercise sessions, with overlapping sessions counted once. This does not include every minute of ordinary movement."
        "hrv"->"Recorded RMSSD in milliseconds. Forma does not infer HRV from ordinary heart-rate readings. Sampling conditions affect comparisons."
        "skinTemperature","skinTemperatureDelta"->"Recorded skin temperature is not core body temperature. Changes refer to the reference supplied with the shared record."
        "sleepMinutes"->"Known asleep intervals from the primary sleep session. Unknown stages remain gaps; insufficient stage coverage prevents sleep scoring."
        "spo2","sleepSpo2"->"Shared oxygen readings. Consumer watch readings and this app do not diagnose a medical condition."
        "samsungEnergy","sleepRegularity"->"Available from compatible imported files only; the native Health Connect connection does not reconstruct Samsung’s proprietary score."
        else->"Values reflect the selected day’s shared measurements. Missing days remain gaps rather than zero readings."
    },fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
