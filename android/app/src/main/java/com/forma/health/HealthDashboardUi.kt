@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.forma.health

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable private fun signalColumns()=if(LocalConfiguration.current.screenWidthDp<360 || LocalDensity.current.fontScale>1.2f)2 else 3

internal val SignalPink=Color(0xFFFF647F)
internal val SignalBlue=Color(0xFF879AFF)
internal val SignalCyan=Color(0xFF59E3D1)
internal val SignalOrange=Color(0xFFFF9465)
internal val SignalLime=Color(0xFFD8FC70)
@Composable internal fun HealthSection(title:String,kicker:String,accent:Color=SignalCyan,action:String?=null,onAction:()->Unit={},content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(kicker.uppercase(),fontSize=9.sp,lineHeight=14.sp,letterSpacing=1.6.sp,color=accent,fontWeight=FontWeight.Bold);Text(title,fontSize=21.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp,modifier=Modifier.padding(top=3.dp)) }
            if(action!=null)TextButton(onClick=onAction) { Text(action,fontSize=11.sp,lineHeight=17.sp,color=accent) }
        }
        content()
    }
}
@Composable internal fun HealthPanel(accent:Color=SignalCyan,content:@Composable ColumnScope.()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(20.dp),border=BorderStroke(.5.dp,accent.copy(alpha=.23f))) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
}
@Composable fun ScoreDashboard(sleep:Score,recovery:Score,energy:EnergyResult,onScore:(String)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Triple("Sleep",sleep,SignalBlue),Triple("Recovery",recovery,SignalCyan),Triple("Energy",energy.score,SignalLime)).forEach { (label,score,color)->
            Surface(onClick={onScore(when(label){"Sleep"->"Sleep score";"Recovery"->"Recovery score";else->"Body energy"})},modifier=Modifier.weight(1f),color=color.copy(alpha=.14f),shape=RoundedCornerShape(17.dp),border=BorderStroke(.5.dp,color.copy(alpha=.4f))) {
                Column(Modifier.padding(13.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,fontSize=11.sp,lineHeight=17.sp,fontWeight=FontWeight.SemiBold,color=color);Icon(Icons.Outlined.NorthEast,null,Modifier.size(12.dp),tint=color) }
                    Text(score.value?.toString() ?: "—",fontSize=33.sp,lineHeight=42.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.3).sp)
                    Text(if(label=="Energy")"ESTIMATE" else if(score.value==null)"NEEDS DATA" else "${score.confidence.uppercase()}",fontSize=7.sp,lineHeight=11.sp,letterSpacing=.4.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress={(score.value ?: 0)/100f},color=color,trackColor=color.copy(alpha=.12f),modifier=Modifier.fillMaxWidth().padding(top=10.dp).height(3.dp),drawStopIndicator={})
                }
            }
        }
    }
}
@Composable fun SignalGrid(state:FormaState,keys:List<String>,accent:Color,onMetric:(String)->Unit) {
    val day=state.data.days.find { it.date==state.selected }
    val columns=signalColumns()
    keys.chunked(columns).forEach { row->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            row.forEach { key->
                val metric=healthCatalog.first { it.key==key }
                val retained=if(key in listOf("weightKg","bodyFat","leanMassKg","heightCm","basalMetabolicRate","systolic","diastolic","bloodGlucose","vo2Max"))HealthAnalytics.latest(state.data.days,state.selected,key)else null
                val value=day?.get(key) ?: retained?.second
                val measurementDate=if(day?.get(key)!=null)state.selected else retained?.first
                val baseline=PersonalSignals.baseline(state.data.days,state.selected,key)
                val series=calendarDays(state.data.days,state.selected,7).map { it[key] }
                Surface(onClick={onMetric(key)},color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(shortLabel(key,metric.label),fontSize=10.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,modifier=Modifier.heightIn(min=28.dp))
                        Text(if(key=="sleepMinutes")duration(value) else measurement(value,metric.decimals),fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.9).sp,color=if(value==null)MaterialTheme.colorScheme.onSurfaceVariant else accent)
                        Text(if(value==null)"Not shared" else if(measurementDate!=null && measurementDate!=state.selected)"${measurementDate.format(DateTimeFormatter.ofPattern("d MMM"))} · ${metric.unit}" else metric.unit,fontSize=9.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Sparkline(series,accent,Modifier.fillMaxWidth().padding(top=8.dp).height(23.dp))
                        val change=if(value!=null && baseline!=null && (measurementDate==null || measurementDate==state.selected))value-baseline.typical else null
                        Text(if(change!=null)"${if(change>=0)"+" else ""}${measurement(change,metric.decimals)} vs usual" else "7-day history",fontSize=8.sp,lineHeight=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                    }
                }
            }
            repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
private fun shortLabel(key:String,fallback:String)=when(key) { "restingHeartRate"->"Resting HR";"sleepHeartRate"->"Sleeping HR";"hrv"->"HRV · RMSSD";"respiratoryRate"->"Breathing rate";"skinTemperatureDelta"->"Skin temp Δ";"sleepMinutes"->"Time asleep";"heartRateMin"->"Lowest HR";"heartRateMax"->"Highest HR";"heartRateAverage"->"Average HR";"leanMassKg"->"Lean mass";"basalMetabolicRate"->"Basal energy";"dietaryCalories"->"Food energy";"proteinGrams"->"Protein";"carbsGrams"->"Carbs";"fatGrams"->"Fat";else->fallback }
@Composable private fun Sparkline(values:List<Double?>,color:Color,modifier:Modifier) {
    Canvas(modifier.semantics { contentDescription="Seven-day recorded trend; gaps indicate missing data" }) {
        val valid=values.filterNotNull();if(valid.isEmpty()) { drawLine(color.copy(alpha=.12f),Offset(0f,size.height/2),Offset(size.width,size.height/2),1.dp.toPx());return@Canvas }
        val low=valid.min()-1;val high=valid.max()+1
        fun p(i:Int,v:Double)=Offset(i.toFloat()/(values.size-1).coerceAtLeast(1)*size.width,((high-v)/(high-low)*size.height).toFloat())
        values.forEachIndexed { i,v->if(v!=null) { if(i>0 && values[i-1]!=null)drawLine(color,p(i-1,values[i-1]!!),p(i,v),2.dp.toPx(),StrokeCap.Round);drawCircle(color,1.5.dp.toPx(),p(i,v)) } }
    }
}
@Composable fun DailyActivityTimeline(day:HealthDay?) {
    HealthSection("Your day in motion","Hour by hour",SignalOrange) {
        HealthPanel(SignalOrange) {
            if(day?.activity.isNullOrEmpty())Text("Hourly movement hasn’t been shared for this day.",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                var selected by remember(day?.date) { mutableIntStateOf(-1) }
                val data=day!!.activity;val max=data.maxOf { it.steps ?: 0.0 }.coerceAtLeast(1.0)
                Row(Modifier.fillMaxWidth().height(75.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom) {
                    val start=day.date.atStartOfDay(ZoneId.systemDefault()).toInstant();val count=Duration.between(start,day.date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()).toHours().toInt()
                    repeat(count) { i->val h=data.firstOrNull { Duration.between(start,it.at).toHours().toInt()==i };Box(Modifier.weight(1f).fillMaxHeight().clickable { selected=i }.semantics { contentDescription="Activity hour $i, ${measurement(h?.steps)} steps" },contentAlignment=Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().height(((h?.steps ?: 0.0)/max*65+3).dp).background(if((h?.exerciseMinutes ?: 0.0)>0)SignalLime else if(h==null)MaterialTheme.colorScheme.outline else SignalOrange,RoundedCornerShape(topStart=3.dp,topEnd=3.dp))) } }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { listOf("00","06","12","18","24").forEach { Text(it,fontSize=9.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                val hour=data.firstOrNull { Duration.between(day.date.atStartOfDay(ZoneId.systemDefault()).toInstant(),it.at).toHours().toInt()==selected }
                Text(if(selected<0)"Tap an hour · lime marks recorded exercise" else "${hour?.at?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "%02d:00".format(selected)} · ${measurement(hour?.steps)} steps · ${measurement(hour?.calories)} active kcal · ${measurement(hour?.exerciseMinutes)} exercise min",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable fun SleepPatterns(state:FormaState) {
    val p=HealthAnalytics.sleepPattern(state.data.days,state.selected,state.prefs.sleepGoal)
    HealthSection("Sleep, in perspective","Seven nights",SignalBlue) {
        HealthPanel(SignalBlue) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OverviewFact("Average sleep",duration(p.average),"${p.nights}/7 nights recorded",SignalBlue,Modifier.weight(1f))
                OverviewFact("Below your goal",duration(p.shortfall),"Total shortfall · recorded nights",SignalBlue,Modifier.weight(1f))
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OverviewFact("Usual bedtime",sleepClockLabel(p.bedtime),"Median recorded start",SignalBlue,Modifier.weight(1f))
                OverviewFact("Usual wake-up",sleepClockLabel(p.wakeTime),"Median recorded end",SignalBlue,Modifier.weight(1f))
            }
            Text("${p.goalNights}/${p.nights} nights reached your sleep goal · bedtime variation ${p.bedtimeSpread?.let { "${measurement(it)} min" } ?: "needs 3 nights"}",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            DetailDisclosure("About sleep patterns") { Text("Shortfall compares recorded sleep with your target. Variation is the median distance from your usual bedtime.",fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable internal fun OverviewFact(label:String,value:String,detail:String,accent:Color,modifier:Modifier=Modifier) {
    Column(modifier) { Text(label,fontSize=10.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontSize=25.sp,lineHeight=35.sp,fontWeight=FontWeight.Bold,color=accent,letterSpacing=(-.7).sp);Text(detail,fontSize=9.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable fun MovementMomentum(state:FormaState) {
    val week=HealthAnalytics.week(state.data.days,state.selected,state.prefs)
    val workouts=HealthAnalytics.workouts(state.data.days,state.selected,7)
    HealthSection("A week of momentum","Activity overview",SignalOrange) {
        HealthPanel(SignalOrange) {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OverviewFact("Weekly steps",measurement(week.steps),"${week.days}/7 days shared",SignalLime,Modifier.weight(1f))
                OverviewFact("Exercise",duration(week.exercise),"Recorded sessions",SignalOrange,Modifier.weight(1f))
                OverviewFact("Step streak","${week.streak} days","${week.closedDays}/7 goals met",SignalCyan,Modifier.weight(1f))
            }
            Text("${workouts.size} workouts · ${week.closedDays}/7 step targets reached",fontSize=10.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
fun workoutName(w:RecordedWorkout):String=w.title.ifBlank { when(w.type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING->"Run";ExerciseSessionRecord.EXERCISE_TYPE_WALKING->"Walk";ExerciseSessionRecord.EXERCISE_TYPE_BIKING->"Ride";ExerciseSessionRecord.EXERCISE_TYPE_HIKING->"Hike";ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL->"Pool swim";ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER->"Open-water swim";ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING->"Strength training";ExerciseSessionRecord.EXERCISE_TYPE_YOGA->"Yoga";ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE->"Rowing";ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL->"Treadmill run";ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY->"Indoor ride";ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING->"Weight training";ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING->"Intervals";ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL->"Elliptical";ExerciseSessionRecord.EXERCISE_TYPE_PILATES->"Pilates";ExerciseSessionRecord.EXERCISE_TYPE_DANCING->"Dance";ExerciseSessionRecord.EXERCISE_TYPE_TENNIS->"Tennis";ExerciseSessionRecord.EXERCISE_TYPE_GOLF->"Golf";ExerciseSessionRecord.EXERCISE_TYPE_SOCCER->"Football";else->"Workout" } }
@Composable fun RecordedWorkouts(state:FormaState,count:Int=7,onWorkout:(String)->Unit) {
    val workouts=HealthAnalytics.workouts(state.data.days,state.selected,count)
    HealthSection("Recorded workouts","Samsung Health · last $count days",SignalOrange) {
        if(workouts.isEmpty())HealthPanel(SignalOrange) { Text("No shared workout sessions in this range.",fontSize=14.sp);Text("Record on your watch, then sync Samsung Health.",fontSize=11.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        workouts.take(30).forEach { w->
            Surface(onClick={onWorkout(w.id)},color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
                    Surface(color=SignalOrange.copy(alpha=.14f),shape=RoundedCornerShape(13.dp)) { Icon(Icons.Outlined.FitnessCenter,null,tint=SignalOrange,modifier=Modifier.padding(12.dp).size(23.dp)) }
                    Column(Modifier.weight(1f).padding(start=13.dp)) {
                        Text(workoutName(w),fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)
                        Text(w.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm")),fontSize=9.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
                        Text("${duration(w.minutes)} · ${measurement(w.values["distanceKm"],2)} km · ${measurement(w.values["heartRateAverage"])} bpm",fontSize=10.sp,lineHeight=15.sp,color=SignalOrange,modifier=Modifier.padding(top=7.dp))
                    }
                    Icon(Icons.Outlined.NorthEast,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(16.dp))
                }
            }
        }
        if(workouts.size>30)Text("Showing the 30 latest sessions in this range.",fontSize=10.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun WorkoutDetail(state:FormaState,id:String) {
    val w=state.data.days.flatMap { it.recordedWorkouts }.find { it.id==id }
    if(w==null) { Text("This session is no longer in the shared records.");return }
    Text(workoutName(w),fontSize=31.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
    Text(w.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, d MMM · HH:mm"))+" – "+w.end.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        OverviewFact("Duration",duration(w.minutes),"Elapsed session time",SignalOrange,Modifier.weight(1f))
        OverviewFact("Distance",measurement(w.values["distanceKm"],2),"kilometers",SignalLime,Modifier.weight(1f))
        OverviewFact("Calories",measurement(w.values["activeCalories"] ?: w.values["totalCalories"]),if(w.values["activeCalories"]!=null)"Active kcal" else "Shared total kcal",SignalOrange,Modifier.weight(1f))
    }
    if(w.heart.isNotEmpty()) {
        Text("Heart rate through your session",fontWeight=FontWeight.Bold,fontSize=18.sp)
        val points=buildList<Pair<Double,Double?>> { w.heart.forEachIndexed { i,p->val x=Duration.between(w.start,p.at).toMillis()/60000.0;if(i>0 && Duration.between(w.heart[i-1].at,p.at).seconds>90)add((x-.01) to null);add(x to p.bpm) } }
        NativeLineChart(points,w.heart.minOf { it.bpm }-5,w.heart.maxOf { it.bpm }+5,SignalPink,xMin=0.0,xMax=w.minutes,pointLabel={"${measurement(it)} min"},valueLabel={"${measurement(it)} bpm"},ticks=listOf(0.0 to "Start",w.minutes to "${measurement(w.minutes)} min"))
        Text("${measurement(w.values["heartRateAverage"])} bpm avg · ${measurement(w.values["heartRateMin"])}–${measurement(w.values["heartRateMax"])} bpm minute-average range",fontSize=11.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Observed heart-rate distribution",fontWeight=FontWeight.SemiBold,fontSize=15.sp)
        val bands=HealthAnalytics.heartBands(w);val labels=listOf("<100","100–119","120–139","140–159","160+")
        bands.forEachIndexed { i,minutes->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text(labels[i],fontSize=10.sp,modifier=Modifier.width(58.dp));LinearProgressIndicator(progress={minutes.toFloat()/bands.sum().coerceAtLeast(1)},color=SignalPink.copy(alpha=.45f+i*.13f),trackColor=MaterialTheme.colorScheme.outline,modifier=Modifier.weight(1f).height(7.dp),drawStopIndicator={});Text("$minutes min",fontSize=10.sp,modifier=Modifier.width(45.dp)) } }
        ValueRow("Heart-rate coverage","${bands.sum()} / ${measurement(w.minutes)} min")
    } else Text("Heart-rate samples weren’t shared for this workout.",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    listOf("steps","speedKmh","maxSpeedKmh","powerWatts","maxPowerWatts").forEach { key->val m=healthCatalog.first { it.key==key };Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(m.label,fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${measurement(w.values[key],m.decimals)} ${m.unit}",fontSize=13.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold) } }
    HealthAnalytics.pace(w)?.let { Text("Elapsed pace · ${it.toInt()}:${((it%1)*60).toInt().toString().padStart(2,'0')} /km",fontSize=12.sp,lineHeight=19.sp,color=SignalOrange) }
    DetailDisclosure("About this workout") { Text("These are BPM bands, not prescribed training zones. Missing minutes stay missing. Session totals use overlapping Samsung records; simultaneous sessions can share readings. Elapsed pace includes pauses.",fontSize=11.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable fun PersonalVitals(state:FormaState,onMetric:(String)->Unit) {
    HealthSection("Your personal range","Context before conclusions",SignalCyan) {
        listOf("restingHeartRate","hrv","sleepHeartRate","respiratoryRate","spo2","skinTemperatureDelta").chunked(signalColumns()).forEach { keys->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { keys.forEach { key->
                val m=healthCatalog.first { it.key==key };val today=state.data.days.find { it.date==state.selected }?.get(key);val b=PersonalSignals.baseline(state.data.days,state.selected,key)
                val changed=today!=null && b!=null && abs(b.deviation(today))>1.5
                Surface(onClick={onMetric(key)},modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(14.dp),border=BorderStroke(.5.dp,if(changed)SignalOrange.copy(alpha=.6f) else SignalCyan.copy(alpha=.2f))) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(shortLabel(key,m.label),fontSize=10.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(measurement(today,m.decimals),fontSize=25.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold,color=if(changed)SignalOrange else SignalCyan)
                        Text(m.unit,fontSize=9.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if(b==null)"Needs 7 prior days" else "Usual ${measurement(b.typical,m.decimals)}",fontSize=9.sp,lineHeight=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if(b==null)"Building baseline" else if(changed)"Changed pattern" else "Recent pattern",fontSize=8.sp,lineHeight=12.sp,color=if(changed)SignalOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
        }
        Text("Orange · change from your usual pattern",fontSize=10.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun WorkoutHistory(state:FormaState,onWorkout:(String)->Unit) {
    var range by remember { mutableIntStateOf(28) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(7,28,90,365).forEach { n->FilterChip(selected=range==n,onClick={range=n},label={Text("${n}D",fontSize=11.sp)}) } }
        RecordedWorkouts(state,range,onWorkout)
    }
}
