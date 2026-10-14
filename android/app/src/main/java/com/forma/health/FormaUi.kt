@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
package com.forma.health

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

// Color has a job: lime is capacity, blue is sleep, orange is movement.
private val Background = Color(0xFF101211)
private val Panel = Color(0xFF1C201D)
private val Ink = Color(0xFFF4F5EC)
private val Muted = Color(0xFF9AABA0)
private val Border = Color(0xFF343D36)
private val Olive = Color(0xFFD8FC70)
private val Sage = Panel
private val Purple = Color(0xFF96A7FF)
private val Orange = Color(0xFFFF895F)
private val stageColors = mapOf("Awake" to Orange, "REM" to Color(0xFFB9C8FF), "Light" to Color(0xFF6D8AFF), "Deep" to Color(0xFF465ACD))
private val FormaFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings=FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings=FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings=FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings=FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings=FontVariation.Settings(FontVariation.weight(800)))
)
private fun moodName(m:Int) = listOf("Low","A little off","Okay","Good","Wonderful")[(m-1).coerceIn(0,4)]
private fun stateText(v:Int?) = when { v==null -> "Building your picture"; v>=80 -> "Ready for your day"; v>=60 -> "Steady. In reserve."; else -> "Room to recharge" }
private fun hourLabel(hour:Double) = "%02d:%02d".format(hour.toInt(), ((hour%1)*60).roundToInt().coerceIn(0,59))

@Composable internal fun FormaTheme(content:@Composable ()->Unit) {
    val typography = Typography().let { base -> base.copy(
        bodyLarge=base.bodyLarge.copy(fontFamily=FormaFont), bodyMedium=base.bodyMedium.copy(fontFamily=FormaFont),
        bodySmall=base.bodySmall.copy(fontFamily=FormaFont), labelLarge=base.labelLarge.copy(fontFamily=FormaFont),
        labelMedium=base.labelMedium.copy(fontFamily=FormaFont), labelSmall=base.labelSmall.copy(fontFamily=FormaFont),
        titleLarge=base.titleLarge.copy(fontFamily=FormaFont), titleMedium=base.titleMedium.copy(fontFamily=FormaFont),
        headlineSmall=base.headlineSmall.copy(fontFamily=FormaFont)
    ) }
    MaterialTheme(
        colorScheme=darkColorScheme(primary=Olive,onPrimary=Background,secondary=Purple,onSecondary=Background,
            background=Background,surface=Panel,onSurface=Ink,onBackground=Ink,surfaceContainer=Panel,
            surfaceContainerHigh=Color(0xFF252C26),outline=Border,onSurfaceVariant=Muted),
        typography=typography
    ) {
        content()
    }
}

@Composable fun FormaApp(
    model:FormaViewModel, onConnect:()->Unit, onBackground:()->Unit, onImport:()->Unit, onExport:()->Unit,
    showPrivacy:Boolean=false, initialPage:String="Today", initialSheet:String="",
    updateState:AppUpdateState=AppUpdateState(), onCheckUpdates:()->Unit={}, onInstallUpdate:()->Unit={},
    onCancelUpdate:()->Unit={}, onAutomaticUpdates:(Boolean)->Unit={},
    onHistory:()->Unit={},onSamsungHealth:()->Unit={},onWatchApp:()->Unit={},onHealthSettings:()->Unit={},onInstallationSettings:()->Unit={},onWatchDownload:()->Unit={}
) {
    val state by model.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(if(showPrivacy) "You" else initialPage) }
    var sheet by rememberSaveable { mutableStateOf(if(showPrivacy) "Privacy" else initialSheet) }
    var sheetTrail by rememberSaveable { mutableStateOf(listOf<String>()) }
    fun showSheet(target:String) {
        if(target.isBlank()) { sheet="";sheetTrail=emptyList() }
        else { if(sheet.isNotBlank() && sheet!=target)sheetTrail=sheetTrail+sheet;sheet=target }
    }
    fun backSheet() { if(sheetTrail.isEmpty())showSheet("") else { sheet=sheetTrail.last();sheetTrail=sheetTrail.dropLast(1) } }
    var datePicker by remember { mutableStateOf(false) }
    val day = state.data.days.find { it.date==state.selected }
    val sleep = Scores.sleep(day,state.prefs.sleepGoal)
    val recovery = Scores.recovery(day,state.data.days,state.prefs.sleepGoal)
    val energy = Scores.energy(day,state.data.days,state.prefs.sleepGoal)
    val snackbar = remember { SnackbarHostState() }
    val listState = remember(page) { LazyListState() }
    LaunchedEffect(page) { listState.scrollToItem(0) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    FormaTheme {
        Scaffold(containerColor=Background, snackbarHost={SnackbarHost(snackbar)}, bottomBar={
            FormaNavigation(page) { page=it }
        }) { insets ->
            LazyColumn(state=listState, modifier=Modifier.fillMaxSize().padding(insets),
                contentPadding=PaddingValues(start=22.dp,end=22.dp,top=8.dp,bottom=32.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)) {
                item { AppHeader(state.data) { showSheet("Connections") } }
                item {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column {
                            Text(when(page) { "Today"->"Your day."; "Sleep"->"The night shift."; "Move"->"Built to move."; "Trends"->"The long view."; "Recovery"->"Ready, or reset."; else->"My health." },
                                fontSize=32.sp,lineHeight=38.sp,letterSpacing=(-1.4).sp,fontWeight=FontWeight.Bold)
                            Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable { datePicker=true }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(state.selected.format(DateTimeFormatter.ofPattern("EEE, d MMM")).uppercase(),fontSize=10.sp,letterSpacing=1.4.sp,color=Muted,fontWeight=FontWeight.SemiBold)
                                Icon(Icons.Outlined.ExpandMore,"Choose day",Modifier.padding(start=5.dp).size(16.dp),tint=Muted)
                            }
                        }
                        IconButton(onClick={showSheet("Settings")},modifier=Modifier.size(46.dp).border(1.dp,Border,CircleShape)) {
                            Icon(Icons.Outlined.Tune,"Health settings",Modifier.size(22.dp),tint=Ink)
                        }
                    }
                }
                if(updateState.release!=null && page=="Today") item { AppUpdateBanner(updateState.release) { showSheet("App updates") } }
                when(page) {
                    "Today" -> {
                        item {
                            var clock by remember { mutableStateOf(Instant.now()) }
                            LaunchedEffect(state.watch.frame) { while(true) { clock=Instant.now();delay(1000) } }
                            val direct=if(state.selected==LocalDate.now() && state.watch.enabled && !state.data.demo)state.watch.frame?.currentSteps(clock,ZoneId.systemDefault())else null
                            TodayActivity(if(direct!=null)(day ?: HealthDay(state.selected)).copy(values=day?.values.orEmpty()+mapOf("steps" to direct))else day,state.prefs,{showSheet("Preferences")},{page="Move"})
                            if(direct!=null)Text("Step ring · direct watch count. Other rings · Samsung Health.",fontSize=9.sp,color=SignalCyan,modifier=Modifier.padding(top=7.dp))
                        }
                        item { ScoreDashboard(sleep,recovery,energy) { showSheet(it) } }
                        item { TodayHeart(day,state.data.demo,if(state.selected==LocalDate.now() && state.watch.enabled && !state.data.demo)state.watch.frame else null) { showSheet("Metric:heartRateLatest") } }
                        item { HealthSection("Vitals at a glance","Six signals · seven-day trends",SignalCyan,"All metrics ↗",{page="You"}) {
                            SignalGrid(state,listOf("restingHeartRate","hrv","spo2","respiratoryRate","skinTemperatureDelta","vo2Max"),SignalCyan) { showSheet("Metric:$it") }
                        } }
                        item { DailyActivityTimeline(day) }
                        item { HealthSection("Last night","Sleep & overnight physiology",SignalBlue,"Sleep ↗",{page="Sleep"}) {
                            SignalGrid(state,listOf("sleepMinutes","sleepEfficiency","sleepHeartRate","sleepSpo2","sleepRespiratoryRate","napMinutes"),SignalBlue) { showSheet("Metric:$it") }
                        } }
                        item { RecordedWorkouts(state) { showSheet("Workout:$it") } }
                        if(state.watch.enabled) item { DirectWatchCard(state.watch) { showSheet("Direct watch") } }
                        item { EnergyCard(energy,state.data.demo) { showSheet("Body energy") } }
                        item { HealthSection("Body & metabolic health","Recorded measurements",SignalPink,"Health ↗",{page="You"}) {
                            SignalGrid(state,listOf("weightKg","bodyFat","leanMassKg","systolic","diastolic","bloodGlucose"),SignalPink) { showSheet("Metric:$it") }
                        } }
                        item { HealthSection("Nutrition & hydration","Samsung Health records",SignalOrange) {
                            SignalGrid(state,listOf("dietaryCalories","proteinGrams","carbsGrams","fatGrams","fiberGrams","waterMl"),SignalOrange) { showSheet("Metric:$it") }
                        } }
                        item { DataArrivalCard(state) { showSheet("Your data") } }
                    }
                    "Sleep" -> {
                        item { SleepHero(day,sleep,state.prefs.sleepGoal) { showSheet("Sleep score") } }
                        item { SleepCard(day) }
                        item { SleepPatterns(state) }
                        item { WeeklyBars("Seven nights. One rhythm.","Sleep duration · goal ${duration(state.prefs.sleepGoal.toDouble())}",calendarDays(state.data.days,state.selected,7).map { it.date.dayOfWeek.toString().take(3) to it["sleepMinutes"] },Purple,state.prefs.sleepGoal.toDouble(),"min") }
                        item { HealthSection("All sleep signals","Details & trends",SignalBlue) {
                            SignalGrid(state,listOf("sleepMinutes","sleepEfficiency","sleepCoverage","sleepHeartRate","sleepSpo2","sleepRespiratoryRate","skinTemperatureDelta","skinTemperature","napMinutes"),SignalBlue) { showSheet("Metric:$it") }
                        } }
                    }
                    "Recovery" -> {
                        item { RecoveryHero(recovery) { showSheet("Recovery score") } }
                        item { PersonalVitals(state) { showSheet("Metric:$it") } }
                        item { EnergyCard(energy,state.data.demo) { showSheet("Body energy") } }
                    }
                    "Move" -> {
                        item { TodayActivity(day,state.prefs,{showSheet("Preferences")},{showSheet("Metric:steps")},movementAction="Step history ↗") }
                        item { MovementMomentum(state) }
                        item { DailyActivityTimeline(day) }
                        item { WeeklyBars("Momentum, day by day","Your goal · ${measurement(state.prefs.stepGoal.toDouble())} steps",calendarDays(state.data.days,state.selected,7).map { it.date.dayOfWeek.toString().take(3) to it["steps"] },Orange,state.prefs.stepGoal.toDouble(),"steps") }
                        item { HealthSection("Movement metrics","Distance · performance · energy",SignalOrange) {
                            SignalGrid(state,listOf("distanceKm","floors","activeMinutes","speedKmh","maxSpeedKmh","powerWatts","maxPowerWatts","vo2Max","totalCalories"),SignalOrange) { showSheet("Metric:$it") }
                        } }
                        item { WorkoutHistory(state) { showSheet("Workout:$it") } }
                    }
                    "Trends" -> item { TrendsScreen(state) }
                    "You" -> {
                        item { HealthSection("Health library","${healthCatalog.size} measurements",SignalCyan,"Search",{showSheet("All metrics")}) {} }
                        listOf("Heart","Vitals","Body","Movement","Sleep","Nutrition").forEach { group->
                            item { HealthSection(group,when(group){"Heart"->"Rate · rhythm · fitness";"Vitals"->"Oxygen · breathing · temperature";"Body"->"Composition · metabolic markers";"Movement"->"Daily activity & performance";"Sleep"->"Duration · stages · overnight signals";else->"Shared food & fluid intake"},when(group){"Heart","Body"->SignalPink;"Sleep"->SignalBlue;"Movement","Nutrition"->SignalOrange;else->SignalCyan}) {
                                SignalGrid(state,healthCatalog.filter { it.group==group }.map { it.key },when(group){"Heart","Body"->SignalPink;"Sleep"->SignalBlue;"Movement","Nutrition"->SignalOrange;else->SignalCyan}) { showSheet("Metric:$it") }
                            } }
                        }
                    }
                }
            }
        }
        if(datePicker){val picker=rememberDatePickerState(initialSelectedDateMillis=state.selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates{override fun isSelectableDate(utcTimeMillis:Long)=Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().let{it<=LocalDate.now()&&it>=state.data.days.first().date}});DatePickerDialog(onDismissRequest={datePicker=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let{model.select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())};datePicker=false}){Text("View day")}},dismissButton={TextButton(onClick={datePicker=false}){Text("Cancel")}}){DatePicker(picker)}}
        if(sheet.isNotBlank())ModalBottomSheet(onDismissRequest={showSheet("")},containerColor=Panel,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
            Column(Modifier.fillMaxWidth().verticalScroll(remember(sheet) { ScrollState(0) }).padding(horizontal=24.dp).padding(bottom=34.dp),verticalArrangement=Arrangement.spacedBy(17.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){if(sheetTrail.isNotEmpty())IconButton(onClick={backSheet()}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")};Text(if(sheet.startsWith("Metric:"))healthCatalog.first { it.key==sheet.removePrefix("Metric:") }.label else if(sheet.startsWith("Workout:"))"Workout details" else when(sheet){"Connections"->"Devices";"Preferences"->"Targets";else->sheet},fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick={showSheet("")}){Icon(Icons.Outlined.Close,"Dismiss panel",Modifier.size(21.dp))}}
                when(sheet){
                    "Recovery score","Sleep score","Body energy"->{val score=when(sheet){"Recovery score"->recovery;"Sleep score"->sleep;else->energy.score};ScoreExplanation(sheet,score);DetailDisclosure("Calculation details"){ScoreDiagnosticsPanel(sheet,day,sleep,recovery,energy,state.prefs.sleepGoal,state.data.source)};if(sheet=="Recovery score")Button(onClick={showSheet("");page="Recovery"},modifier=Modifier.fillMaxWidth()){Text("Explore your recovery")}}
                    "About scores"->ScoreExplanation(sheet,Score())
                    "Settings"->{
                        ActionRow("Devices","Samsung Health & watch companion",Icons.Outlined.Watch) { showSheet("Connections") }
                        ActionRow("Targets","Activity rings & sleep",Icons.Outlined.Tune) { showSheet("Preferences") }
                        ActionRow("Your data","Records, export & privacy",Icons.Outlined.Storage) { showSheet("Your data") }
                        ActionRow("App updates",updateState.release?.let { "${it.versionName} available" } ?: "Forma ${BuildConfig.VERSION_NAME}",Icons.Outlined.SystemUpdate) { showSheet("App updates") }
                        DetailDisclosure("Personal tools","Optional") {
                            ActionRow("Journal",icon=Icons.Outlined.EditNote) { showSheet("Check in") }
                            ActionRow("Manual activity log",icon=Icons.Outlined.FitnessCenter) { showSheet("Manual activity") }
                            ActionRow("Water log",icon=Icons.Outlined.WaterDrop) { showSheet("Water log") }
                            ActionRow("Breathing timer",icon=Icons.Outlined.Air) { showSheet("Breathe") }
                        }
                        DetailDisclosure("About Forma") { Text("Experimental wellness estimates · ${healthCatalog.size} shared measurements",fontSize=11.sp,color=Muted);TextButton(onClick={showSheet("About scores")}){Text("How scores work")} }
                    }
                    "Water log"->HydrationCard(state.water,state.prefs.waterGoal) { model.addWater(it) }
                    "Your data"->DataManagementPanel(state,onImport,onExport,{showSheet("Data availability")},{showSheet("Privacy")},{model.clearAll();showSheet("")})
                    "Data availability"->DataCoveragePanel(state)
                    "Manual activity"->{state.workouts.filter { it.date==state.selected }.forEach { w->Row(verticalAlignment=Alignment.CenterVertically) { Text("${w.type} · ${w.minutes} min · manual",Modifier.weight(1f),fontSize=13.sp);IconButton(onClick={model.deleteWorkout(w.id)}) { Icon(Icons.Outlined.DeleteOutline,"Delete activity") } } };Button(onClick={showSheet("Log activity")}) { Text("Add manual activity") }}
                    "Check in"->CheckIn(state.journals.find{it.date==state.selected}){m,h,n->model.saveJournal(m,h,n);showSheet("")}
                    "Log activity"->WorkoutForm{t,m,i->model.saveWorkout(t,m,i);showSheet("")}
                    "Direct watch"->DirectWatchSetup(state.watch,model::enableWatch,model::findWatch,onWatchDownload)
                    "Connections"->WatchConnectionPanel(state,onConnect,{model.sync()},{model.cancelSync()},onHistory,onSamsungHealth,onWatchApp,onHealthSettings,{if(it)onBackground()else model.enableBackground(false)},{model.updatePrefs(state.prefs.copy(automaticSync=it))},{showSheet("Direct watch")})
                    "App updates"->AppUpdatePanel(updateState,onCheckUpdates,onInstallUpdate,onCancelUpdate,onAutomaticUpdates,onInstallationSettings)
                    "All metrics"->MetricExplorer(state) { showSheet("Metric:$it") }
                    "Preferences"->PreferencesForm(state.prefs,model::updatePrefs)
                    "Privacy"->PrivacyPanel(onHealthSettings)
                    "Breathe"->BreathingPause()
                    else->if(sheet.startsWith("Metric:"))MetricDetail(state,sheet.removePrefix("Metric:")) else if(sheet.startsWith("Workout:"))WorkoutDetail(state,sheet.removePrefix("Workout:"))

                }
            }
        }
    }
}


@Composable private fun AppHeader(data:HealthData,onConnect:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Canvas(Modifier.size(24.dp,22.dp)) {
            drawLine(Olive,Offset(2.dp.toPx(),size.height),Offset(10.dp.toPx(),0f),4.dp.toPx(),StrokeCap.Square)
            drawLine(Olive,Offset(12.dp.toPx(),size.height),Offset(20.dp.toPx(),0f),4.dp.toPx(),StrokeCap.Square)
        }
        Text("FORMA",fontWeight=FontWeight.ExtraBold,fontSize=18.sp,letterSpacing=3.sp,modifier=Modifier.weight(1f).padding(start=10.dp))
        Row(Modifier.clip(CircleShape).background(Panel).padding(horizontal=11.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(if(data.demo)Orange else Olive,CircleShape))
            Text(if(data.demo) "Demo mode" else if(data.source=="Samsung Health · Health Connect")"Samsung" else "Imported",fontSize=10.sp,color=Ink,modifier=Modifier.padding(start=7.dp))
        }
        IconButton(onClick=onConnect,modifier=Modifier.padding(start=5.dp).size(42.dp)) { Icon(Icons.Outlined.Watch,"Watch connection",Modifier.size(21.dp),tint=Muted) }
    }
}

@Composable private fun FormaNavigation(page:String,onPage:(String)->Unit) {
    Surface(color=Background,shadowElevation=0.dp) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color=Border.copy(alpha=.65f))
            Row(Modifier.fillMaxWidth().height(72.dp)) {
                listOf(Triple("Today",Icons.Outlined.Bolt,"Today"),Triple("Sleep",Icons.Outlined.Bedtime,"Sleep"),Triple("Move",Icons.AutoMirrored.Outlined.DirectionsWalk,"Move"),Triple("Trends",Icons.Outlined.BarChart,"Trends"),Triple("You",Icons.Outlined.HealthAndSafety,"Health")).forEach { (name,image,label) ->
                    val selected=page==name || (page=="Recovery" && name=="Today")
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Tab) { onPage(name) }.semantics { this.selected=selected },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Icon(image,label,Modifier.size(24.dp),tint=if(selected)Olive else Muted)
                        Text(label,fontSize=10.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=if(selected)Olive else Muted,modifier=Modifier.padding(top=5.dp))
                        Box(Modifier.padding(top=6.dp).size(14.dp,2.dp).background(if(selected)Olive else Color.Transparent,CircleShape))
                    }
                }
            }
        }
    }
}

@Composable private fun Eyebrow(text:String,color:Color=Muted) { Text(text,fontSize=10.sp,lineHeight=16.sp,letterSpacing=1.7.sp,fontWeight=FontWeight.SemiBold,color=color) }
@Composable private fun Arrow(color:Color=Ink) { Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.padding(start=8.dp).size(18.dp),tint=color) }
@Composable private fun SurfaceCard(modifier:Modifier=Modifier,color:Color=Panel,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=color) { Column(Modifier.padding(22.dp),content=content) }
}
@Composable private fun Heading(title:String,icon:ImageVector,action:String?=null,onClick:()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,fontSize=18.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.5).sp,modifier=Modifier.weight(1f))
        if(action!=null) IconButton(onClick=onClick,modifier=Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowForward,action,Modifier.size(20.dp),tint=Muted) }
        else Icon(icon,null,Modifier.size(20.dp),tint=Muted)
    }
}

/** The instrument's lit segments represent the score; the arc never implies a live sensor feed. */
@Composable private fun EnergyCard(energy:EnergyResult,demo:Boolean,onClick:()->Unit) {
    SurfaceCard {
        Heading("The rise. The spend.",Icons.Outlined.Bolt,"Energy calculation",onClick)
        Text("BODY ENERGY THROUGH THE DAY",fontSize=9.sp,letterSpacing=1.3.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
        if(energy.points.isEmpty()) EmptyState("Your energy trace starts here","Sleep, baseline vitals and recorded activity unlock this view.")
        else NativeLineChart(energy.points.map { it.first to it.second },0.0,100.0,Olive,0.0,max(24.0,energy.points.last().first),listOf(0.0 to "00:00",6.0 to "06",12.0 to "12",18.0 to "18",24.0 to "24:00"))
        if(!demo && energy.points.isNotEmpty())InfoText(energy.recordedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("'Activity window ends' d MMM · HH:mm")) ?: "Activity timestamp unavailable · energy cannot be labeled current")
        TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)) { Text(if(demo)"Sample trace · See the model" else "Recorded samples · See the model",fontSize=11.sp,color=Muted); Icon(Icons.Outlined.Info,null,Modifier.padding(start=6.dp).size(14.dp),tint=Muted) }
    }
}

@Composable private fun MetricRow(label:String,value:Double?,unit:String,image:ImageVector,baseline:Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(image,null,Modifier.size(21.dp),tint=Muted)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(label,fontSize=12.sp,fontWeight=FontWeight.Medium)
            Text(if(value==null)"Not shared" else baseline?.let { "Baseline ${measurement(it,1)} $unit" } ?: "Recorded measurement",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
        }
        Text(measurement(value,if(unit in listOf("°C","/min","kg","mmol/L"))1 else 0),fontSize=24.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.8).sp)
        Text(" $unit",fontSize=9.sp,color=Muted)
    }
    HorizontalDivider(color=Border.copy(alpha=.65f))
}

@Composable private fun SleepHero(day:HealthDay?,score:Score,goal:Int,onClick:()->Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF24329A))) {
        Canvas(Modifier.matchParentSize()) {
            val center=Offset(size.width*.86f,size.height*.13f)
            for(i in 0..5) drawCircle(Color(0xFF667AFF).copy(alpha=.35f),size.width*(.16f+i*.10f),center,style=Stroke(.8.dp.toPx()))
            drawCircle(Color(0xFFBDCDFF),6.dp.toPx(),Offset(size.width*.73f,size.height*.08f))
        }
        Column(Modifier.padding(23.dp)) {
            Eyebrow(if((day?.get("sleepCoverage") ?: 100.0)<95)"RECORDED SLEEP / PARTIAL" else "RECORDED SLEEP",Color(0xFFC8D2FF))
            Text(duration(day?.get("sleepMinutes")),fontSize=57.sp,lineHeight=66.sp,letterSpacing=(-3).sp,fontWeight=FontWeight.SemiBold,color=Color.White,modifier=Modifier.padding(top=20.dp))
            Text("of your ${duration(goal.toDouble())} goal",fontSize=12.sp,color=Color(0xFFC8D2FF),modifier=Modifier.padding(top=7.dp))
            Spacer(Modifier.height(31.dp))
            HorizontalDivider(color=Color.White.copy(alpha=.2f))
            Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(top=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${score.value ?: "—"}",fontSize=36.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp,color=Color.White)
                Column(Modifier.weight(1f).padding(start=13.dp)) { Text("Sleep score",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Color.White); Text("${score.confidence} coverage",fontSize=10.sp,color=Color(0xFFC8D2FF),modifier=Modifier.padding(top=3.dp)) }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Sleep score calculation",Modifier.size(22.dp),tint=Color.White)
            }
        }
    }
}

@Composable private fun SleepCard(day:HealthDay?) {
    Column {
        Heading("Your sleep architecture",Icons.Outlined.Bedtime)
        if(day?.stages.isNullOrEmpty()) EmptyState("Stage details unavailable","Your source hasn’t shared stage-by-stage sleep.")
        else {
            val stages=day!!.stages
            val total=stages.sumOf { it.minutes }
            val lanes=listOf("Awake","REM","Light","Deep")+listOf("Asleep","Unknown").filter { name->stages.any { it.stage==name } }
            Row(Modifier.fillMaxWidth().padding(top=21.dp)) {
                Column(Modifier.height(150.dp).width(43.dp),verticalArrangement=Arrangement.SpaceEvenly) { lanes.forEach { Text(it,fontSize=9.sp,color=Muted) } }
                Canvas(Modifier.weight(1f).height(150.dp).semantics { contentDescription="Sleep stage timeline: "+stages.joinToString { "${it.stage} ${it.minutes.roundToInt()} minutes" } }) {
                    val lane=size.height/lanes.size
                    for(i in lanes.indices) drawLine(Border.copy(alpha=.75f),Offset(0f,lane*(i+.5f)),Offset(size.width,lane*(i+.5f)),.5.dp.toPx())
                    var x=0f
                    var previousY:Float?=null
                    stages.forEach { stage ->
                        val index=lanes.indexOf(stage.stage).coerceAtLeast(0)
                        val y=lane*(index+.5f)
                        val width=(stage.minutes/total*size.width).toFloat()
                        previousY?.let { drawLine(Purple.copy(alpha=.4f),Offset(x,it),Offset(x,y),1.dp.toPx()) }
                        drawRoundRect(stageColors[stage.stage]?:if(stage.stage=="Unknown")Muted else Purple,Offset(x,y-5.dp.toPx()),Size(width.coerceAtLeast(1f),10.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        x+=width; previousY=y
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start=43.dp,top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(day.sleepStart?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Bedtime",fontSize=10.sp,color=Muted)
                Text(day.sleepEnd?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Wake up",fontSize=10.sp,color=Muted)
            }
            Row(Modifier.fillMaxWidth().padding(top=23.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                lanes.forEach { stage -> Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background((stageColors[stage] ?: if(stage=="Unknown")Muted else Purple),CircleShape))
                    Text(stage,fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=11.dp))
                    Text(if(stages.none { it.stage==stage } && stages.any { it.stage in listOf("Asleep","Unknown") })"—" else duration(stages.filter { it.stage==stage }.sumOf { it.minutes }),fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=5.dp))
                } }
            }
        }
        day?.get("sleepCoverage")?.let { Text("${measurement(it)}% stage coverage",fontSize=10.sp,lineHeight=17.sp,color=Muted,modifier=Modifier.padding(top=12.dp)) }
        day?.get("sleepEfficiency")?.let { MetricRow("Sleep efficiency",it,"%",Icons.Outlined.Bedtime,null) }
    }
}

@Composable private fun RecoveryHero(score:Score,onClick:()->Unit) {
    SurfaceCard {
        Eyebrow("YOUR DAILY READINESS",Olive)
        Row(Modifier.fillMaxWidth().padding(vertical=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${score.value ?: "—"}",fontSize=107.sp,lineHeight=114.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-6).sp,modifier=Modifier.weight(1f))
            ScoreRing(score.value,Olive,Icons.Outlined.MonitorHeart,104.dp)
        }
        Text(stateText(score.value),fontSize=25.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.7).sp)
        Text("${score.confidence} coverage · personal estimate",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=9.dp))
        TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp),modifier=Modifier.padding(top=15.dp)) { Text("See what shaped it",fontSize=12.sp); Arrow(Olive) }
    }
}

@Composable private fun ScoreRing(value:Int?,color:Color,image:ImageVector,size:Dp,modifier:Modifier=Modifier) {
    Box(modifier.size(size),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            drawArc(Border,-90f,360f,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Butt))
            drawArc(color,-90f,(value?:0)*3.6f,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Butt))
        }
        Icon(image,null,tint=color,modifier=Modifier.size(size*.3f))
    }
}

@Composable private fun HydrationCard(water:Int,goal:Int,onAdd:(Int)->Unit) {
    SurfaceCard {
        Heading("Water log",Icons.Outlined.WaterDrop)
        Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(measurement(water.toDouble()),fontSize=38.sp,lineHeight=44.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1.8).sp); Text("of ${measurement(goal.toDouble())} ml · manual",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=5.dp)) }
            FilledTonalButton(onClick={onAdd(250)},shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=Border,contentColor=Ink),contentPadding=PaddingValues(13.dp)) { Text("+ 250 ml",fontSize=12.sp) }
        }
        LinearProgressIndicator(progress={(water.toFloat()/goal).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),color=Purple,trackColor=Border,drawStopIndicator={})
        if(water>0) TextButton(onClick={onAdd(-250)},contentPadding=PaddingValues(0.dp)) { Text("Undo 250 ml",fontSize=10.sp,color=Muted) }
    }
}
@Composable private fun InfoText(text:String) { Text(text,fontSize=12.sp,lineHeight=21.sp,color=Muted) }
@Composable private fun EmptyState(title:String,body:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=30.dp)) { Text(title,fontSize=19.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold); Text(body,fontSize=12.sp,lineHeight=21.sp,color=Muted,modifier=Modifier.padding(top=9.dp)) }
}
@Composable private fun FactorRow(f:Factor) {
    Column(Modifier.padding(top=12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(f.label,fontSize=13.sp,fontWeight=FontWeight.SemiBold); Text("${f.value.roundToInt()}/100",fontSize=12.sp,color=Olive) }

        LinearProgressIndicator(progress={(f.value/100).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=16.dp).height(3.dp).clip(CircleShape),color=Olive,trackColor=Border,drawStopIndicator={})
    }
}
@Composable internal fun NativeLineChart(
    points:List<Pair<Double,Double?>>,yMin:Double,yMax:Double,color:Color,
    xMin:Double=points.firstOrNull()?.first?:0.0,xMax:Double=points.lastOrNull()?.first?:1.0,
    ticks:List<Pair<Double,String>> = emptyList(),pointLabel:(Double)->String={hourLabel(it)},valueLabel:(Double)->String={measurement(it)}
) {
    var selected by remember(points) { mutableStateOf<Pair<Double,Double?>?>(null) }
    Column(Modifier.padding(top=20.dp)) {
        if(selected!=null) Text("${valueLabel(selected!!.second!!)} / ${pointLabel(selected!!.first)}",fontSize=11.sp,color=color,modifier=Modifier.padding(bottom=8.dp))
        Canvas(Modifier.fillMaxWidth().height(184.dp)
            .semantics { contentDescription="Trend chart. "+points.joinToString { "${pointLabel(it.first)}: ${it.second ?: "no record"}" } }
            .pointerInput(points,xMin,xMax) { detectTapGestures { tap ->
                val plotWidth=size.width-34.dp.toPx()
                val target=xMin+(tap.x/plotWidth)*(xMax-xMin)
                selected=points.filter { it.second!=null }.minByOrNull { abs(it.first-target) }
            } }) {
            val right=34.dp.toPx(); val top=12.dp.toPx(); val bottom=size.height-8.dp.toPx()
            val width=size.width-right; val height=bottom-top
            fun xy(p:Pair<Double,Double?>) = Offset(((p.first-xMin)/(xMax-xMin).coerceAtLeast(.001)).toFloat()*width,
                top+(1-((p.second!!-yMin)/(yMax-yMin).coerceAtLeast(.001))).toFloat()*height)
            val paint=android.graphics.Paint().apply { this.color=Muted.toArgb(); textSize=9.sp.toPx(); isAntiAlias=true }
            for(i in 0..3) {
                val y=top+height*i/3
                drawLine(Border,Offset(0f,y),Offset(width,y),.5.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(valueLabel(yMax-(yMax-yMin)*i/3),width+8.dp.toPx(),y+3.sp.toPx(),paint)
            }
            val segments=mutableListOf<List<Pair<Double,Double?>>>()
            var part=mutableListOf<Pair<Double,Double?>>()
            for(point in points) {
                if(point.second==null) { if(part.isNotEmpty()) { segments.add(part); part=mutableListOf() } }
                else if(point.first in xMin..xMax) part.add(point)
            }
            if(part.isNotEmpty()) segments.add(part)
            segments.forEach { segment ->
                val path=Path()
                segment.forEachIndexed { i,p -> val pos=xy(p); if(i==0) path.moveTo(pos.x,pos.y) else path.lineTo(pos.x,pos.y) }
                val fill=Path().apply { addPath(path); val last=xy(segment.last()); val first=xy(segment.first()); lineTo(last.x,bottom); lineTo(first.x,bottom); close() }
                drawPath(fill,Brush.verticalGradient(listOf(color.copy(alpha=.19f),color.copy(alpha=0f)),top,bottom))
                drawPath(path,color,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
                segment.forEach { drawCircle(Panel,3.8.dp.toPx(),xy(it)); drawCircle(color,2.4.dp.toPx(),xy(it)) }
            }
            selected?.takeIf { it.second!=null }?.let {
                val p=xy(it)
                drawLine(color.copy(alpha=.6f),Offset(p.x,top),Offset(p.x,bottom),.7.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4f,7f)))
                drawCircle(Ink,6.dp.toPx(),p); drawCircle(color,3.dp.toPx(),p)
            }
        }
        if(ticks.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(end=34.dp,top=12.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            ticks.forEach { (_,label) -> Text(label,fontSize=9.sp,color=Muted) }
        }
    }
}

@Composable private fun WeeklyBars(title:String,subtitle:String,values:List<Pair<String,Double?>>,color:Color,goal:Double,unit:String) {
    SurfaceCard {
        Text(title,fontSize=20.sp,lineHeight=27.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.6).sp)
        Text(subtitle,fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
        val high=max(goal*1.2,values.mapNotNull { it.second }.maxOrNull() ?: goal)
        Row(Modifier.fillMaxWidth().height(180.dp).padding(top=24.dp),horizontalArrangement=Arrangement.spacedBy(11.dp),verticalAlignment=Alignment.Bottom) {
            values.forEachIndexed { index,(label,value) ->
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(value?.let { if(unit=="min") "${(it/60*10).roundToInt()/10.0}h" else if(it>=1000) "${(it/100).roundToInt()/10.0}k" else measurement(it) } ?: "—",fontSize=9.sp,color=Muted,modifier=Modifier.padding(bottom=8.dp))
                    Box(Modifier.fillMaxWidth().height(value?.let { (it/high*112).coerceIn(3.0,112.0).dp } ?: 3.dp).clip(RoundedCornerShape(topStart=3.dp,topEnd=3.dp)).background(if(value==null) Border else if(index==values.lastIndex) color else color.copy(alpha=.35f)))
                    Text(label.take(1),fontSize=10.sp,color=if(index==values.lastIndex)Ink else Muted,modifier=Modifier.padding(top=12.dp))
                }
            }
        }
        Text("${values.count { it.second!=null }} / ${values.size} days recorded",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=17.dp))
    }
}

@Composable private fun TrendsScreen(state:FormaState) {
    var key by rememberSaveable { mutableStateOf("sleepMinutes") }
    var length by rememberSaveable { mutableIntStateOf(7) }
    var category by rememberSaveable { mutableStateOf("Sleep") }
    val options=healthCatalog.filter { it.group==category }.map { Triple(it.key,it.label,it.unit) }
    val definition=healthCatalog.first { it.key==key }
    val option=definition.let { Triple(it.key,it.label,it.unit) }
    val days=calendarDays(state.data.days,state.selected,length)
    val values=days.mapNotNull { it[key] }
    val average=values.takeIf { it.isNotEmpty() }?.average()
    val accent=when(key) { "sleepMinutes"->Purple; "steps"->Orange; else->Olive }
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("Sleep","Heart","Movement","Vitals","Body","Nutrition").forEach { group->FilterChip(selected=category==group,onClick={category=group;key=healthCatalog.first { it.group==group }.key},label={Text(group,fontSize=11.sp)}) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            options.forEach { (field,label,_) -> FilterChip(selected=key==field,onClick={key=field},shape=RoundedCornerShape(10.dp),
                label={Text(label,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Ink,selectedLabelColor=Background,containerColor=Panel,labelColor=Muted),border=null) }
        }
        SurfaceCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Eyebrow(option.second.uppercase(),accent)
                Spacer(Modifier.weight(1f))
                Row(Modifier.background(Background,RoundedCornerShape(8.dp)).padding(3.dp)) {
                    listOf(7,28,90).forEach { count -> Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(length==count)Border else Color.Transparent).clickable { length=count }.padding(horizontal=12.dp,vertical=8.dp)) { Text("${count}D",fontSize=10.sp,color=if(length==count)Ink else Muted,fontWeight=FontWeight.SemiBold) } }
                }
            }
            Row(Modifier.padding(top=25.dp),verticalAlignment=Alignment.Bottom) {
                Text(if(key=="sleepMinutes")duration(average) else measurement(average,definition.decimals),fontSize=47.sp,lineHeight=54.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-2.2).sp)
                if(key!="sleepMinutes") Text(" ${option.third}",fontSize=11.sp,color=Muted,modifier=Modifier.padding(bottom=7.dp))
            }
            Text("DAILY AVERAGE / $length DAYS",fontSize=9.sp,letterSpacing=1.2.sp,color=Muted,modifier=Modifier.padding(top=8.dp))
            val earlier=calendarDays(state.data.days,state.selected.minusDays(length.toLong()),length).mapNotNull { it[key] }
            val prior=earlier.takeIf { it.isNotEmpty() }?.average()
            val decimals=healthCatalog.first { it.key==key }.decimals
            Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OverviewFact("Lowest",measurement(values.minOrNull(),decimals),option.third,accent,Modifier.weight(1f))
                OverviewFact("Highest",measurement(values.maxOrNull(),decimals),option.third,accent,Modifier.weight(1f))
                OverviewFact("Recorded","${values.size}/$length","days",accent,Modifier.weight(1f))
            }
            if(average!=null && prior!=null)Text("${if(average>=prior)"+" else ""}${measurement(average-prior,decimals)} ${option.third} vs the preceding $length days · ${earlier.size} prior days recorded",fontSize=10.sp,lineHeight=18.sp,color=Muted,modifier=Modifier.padding(top=12.dp))
            if(values.isEmpty()) EmptyState("Not shared yet","Data availability depends on Samsung Health and your permissions.")
            else {
                val padding=max((values.maxOrNull()!!-values.minOrNull()!!)*.12,.1)
                val low=values.minOrNull()!!-padding
                val high=values.maxOrNull()!!+padding
                NativeLineChart(days.mapIndexed { i,d -> i.toDouble() to d[key] },low,high,accent,0.0,max(1,days.size-1).toDouble(),
                    listOf(0.0 to days.first().date.format(DateTimeFormatter.ofPattern("d MMM")),(days.size-1).toDouble() to days.last().date.format(DateTimeFormatter.ofPattern("d MMM"))),
                    pointLabel={index->days.getOrNull(index.toInt())?.date?.format(DateTimeFormatter.ofPattern("d MMM")) ?: ""},
                    valueLabel={value->if(key=="sleepMinutes") "${measurement(value/60,1)}h" else measurement(value,definition.decimals)})
                Text("Tap a point to inspect · ${values.size} days recorded",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=10.dp))
            }
        }
        Column {
            Heading("Day by day",Icons.Outlined.CalendarToday)
            days.reversed().forEach { day ->
                Row(Modifier.fillMaxWidth().padding(vertical=17.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),fontSize=12.sp,color=Muted)
                    Text(if(key=="sleepMinutes")duration(day[key]) else "${measurement(day[key],definition.decimals)} ${option.third}",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                }
                HorizontalDivider(color=Border.copy(alpha=.65f))
            }
        }
    }
}
@Composable private fun ScoreExplanation(title:String,score:Score) {
    if(title!="About scores") {
        Row(verticalAlignment=Alignment.Bottom) {
            Text("${score.value ?: "—"}",fontSize=64.sp,lineHeight=72.sp,fontWeight=FontWeight.Bold)
            Text(" / 100",fontSize=13.sp,color=Muted,modifier=Modifier.padding(bottom=12.dp))
        }
        Text(if(score.value==null)score.confidence else "Estimate · ${score.confidence} inputs",fontSize=11.sp,color=Muted)
        if(score.value!=null)score.factors.forEach { f->
            if(title=="Body energy")ValueRow(f.label,"${"%.2f".format(f.value)} points") else FactorRow(f)
        }
    } else {
        ValueRow("Sleep","Your recorded night")
        ValueRow("Recovery","Sleep + baseline vitals")
        ValueRow("Energy","Readiness − activity load")
    }
    DetailDisclosure("How this score works") {
        InfoText(when(title) {
            "Sleep score"->"Duration contributes 50%, efficiency 30%, and imported schedule consistency 20%. Available weights are normalized. Native Samsung records do not provide schedule consistency."
            "Recovery score"->"Sleep contributes 60%, HRV 25%, and resting or sleeping heart rate 15%. Vitals need seven prior days for a baseline. Missing inputs normalize the remaining weights. Baselines use medians and variability from up to 28 prior days."
            "Body energy"->"Starts at recovery. Each bucket uses the largest step, exercise or heart-rate load; overlapping activity is counted once. Explicit imported rest can add energy. Samsung-shared records update the estimate; direct watch readings update their own displays."
            else->"Recovery uses sleep; energy starts at recovery. Matching rounded values are possible. Missing readings remain unavailable. Manual notes and logs do not alter scores."
        })
        score.factors.forEach { InfoText("${it.label} · ${it.detail}") }
        InfoText("Forma estimates are experimental, not clinically validated or Samsung’s proprietary scores.")
    }
}
@Composable private fun CheckIn(entry:JournalEntry?,onSave:(Int,List<String>,String)->Unit){
    var mood by remember{mutableIntStateOf(entry?.mood?:4)};var habits by remember{mutableStateOf(entry?.habits?:emptyList())};var note by remember{mutableStateOf(entry?.note?:"")}
    Text("How are you feeling today?",fontSize=14.sp,color=Muted)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){(1..5).forEach{m->Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(mood==m)Sage else Background).border(1.dp,if(mood==m)Olive else Border,RoundedCornerShape(12.dp)).clickable{mood=m}.padding(vertical=13.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(when(m){1->Icons.Outlined.Cloud;2->Icons.Outlined.NightsStay;3->Icons.Outlined.WbTwilight;4->Icons.Outlined.WbSunny;else->Icons.Outlined.AutoAwesome},moodName(m),tint=Olive,modifier=Modifier.size(23.dp));Text(moodName(m),fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=8.dp))}}}
    Text("What was part of your day?",fontSize=14.sp,fontWeight=FontWeight.Medium)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){listOf("Morning light","Movement","Late caffeine","Alcohol","Screen-free evening","Stressful day","Time outside","Mindful moment").forEach{habit->FilterChip(selected=habit in habits,onClick={habits=if(habit in habits)habits-habit else habits+habit},label={Text(habit,fontSize=10.sp)})}}
    OutlinedTextField(value=note,onValueChange={note=it.take(2000)},label={Text("Anything you’d like to notice?",fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),minLines=3,supportingText={Text("Saved privately on this phone.",fontSize=10.sp)},shape=RoundedCornerShape(12.dp))
    Button(onClick={onSave(mood,habits,note)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text("Save check-in")}
}
@Composable private fun WorkoutForm(onSave:(String,Int,String)->Unit){
    var type by remember{mutableStateOf("Walk")};var minutes by remember{mutableStateOf("30")};var intensity by remember{mutableStateOf("Moderate")}
    InfoText("Manual activity logs stay separate from watch measurements and don’t change recovery or energy estimates.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("Walk","Run","Cycling","Strength","Yoga","Stretching","Swimming","Other").forEach{t->FilterChip(selected=type==t,onClick={type=t},label={Text(t,fontSize=11.sp)})}}
    OutlinedTextField(value=minutes,onValueChange={minutes=it.filter{c->c.isDigit()}.take(3)},label={Text("Duration in minutes")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),shape=RoundedCornerShape(12.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Easy","Moderate","Challenging").forEach{i->FilterChip(selected=intensity==i,onClick={intensity=i},label={Text(i,fontSize=11.sp)})}}
    Button(onClick={onSave(type,minutes.toInt(),intensity)},enabled=(minutes.toIntOrNull()?:0) in 1..600,modifier=Modifier.fillMaxWidth()){Text("Save activity")}
}
@Composable private fun PreferencesForm(prefs:Preferences,onChange:(Preferences)->Unit){
    PreferenceSlider("Sleep goal",duration(prefs.sleepGoal.toDouble()),prefs.sleepGoal.toFloat(),360f..600f,15){onChange(prefs.copy(sleepGoal=(it/15).roundToInt()*15))}
    PreferenceSlider("Daily step goal",measurement(prefs.stepGoal.toDouble()),prefs.stepGoal.toFloat(),2000f..20000f,17){onChange(prefs.copy(stepGoal=(it/1000).roundToInt()*1000))}
    PreferenceSlider("Active calorie goal",prefs.calorieGoal.toString()+" kcal",prefs.calorieGoal.toFloat(),100f..2000f,37){onChange(prefs.copy(calorieGoal=(it/50).roundToInt()*50))}
    PreferenceSlider("Recorded exercise goal",prefs.exerciseGoal.toString()+" min",prefs.exerciseGoal.toFloat(),5f..180f,34){onChange(prefs.copy(exerciseGoal=(it/5).roundToInt()*5))}
    PreferenceSlider("Manual water goal","${measurement(prefs.waterGoal.toDouble())} ml",prefs.waterGoal.toFloat(),1000f..4000f,11){onChange(prefs.copy(waterGoal=(it/250).roundToInt()*250))}
    InfoText("Customizable personal targets, not prescribed activity or fluid requirements.")
    InfoText("Saved automatically")
}
@Composable private fun PreferenceSlider(label:String,valueLabel:String,value:Float,range:ClosedFloatingPointRange<Float>,steps:Int,onChange:(Float)->Unit){Column{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,fontSize=13.sp);Text(valueLabel,fontSize=13.sp,color=Olive,fontWeight=FontWeight.Medium)};Slider(value=value,onValueChange=onChange,valueRange=range,steps=steps,colors=SliderDefaults.colors(thumbColor=Olive,activeTrackColor=Olive,inactiveTrackColor=Sage))}}
@Composable private fun BreathingPause(){
    var running by rememberSaveable{mutableStateOf(false)};var elapsed by rememberSaveable{mutableIntStateOf(0)}
    LaunchedEffect(running){if(running)while(elapsed<120){delay(1000);elapsed++;if(elapsed>=120)running=false}}
    val target=if(running&&elapsed%8<4)1.08f else .88f
    val scale by animateFloatAsState(targetValue=target,animationSpec=tween(3500),label="Breathing circle")
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){InfoText("Follow the circle at a comfortable pace. Pause whenever you need.");Box(Modifier.padding(vertical=37.dp).size(190.dp).scale(scale).background(Sage,CircleShape).border(12.dp,Background,CircleShape),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.Air,null,Modifier.size(36.dp),tint=Olive);Text(if(elapsed>=120)"Well done." else if(!running)"Find your rhythm" else if(elapsed%8<4)"Breathe in" else "Breathe out",fontSize=14.sp,color=Muted,modifier=Modifier.padding(top=13.dp))}};Text("${elapsed/60}:${(elapsed%60).toString().padStart(2,'0')} / 2:00",fontSize=12.sp,color=Muted);Button(onClick={if(elapsed>=120)elapsed=0;running=!running},modifier=Modifier.padding(top=20.dp).fillMaxWidth()){Text(if(running)"Pause" else if(elapsed>=120)"Start again" else if(elapsed>0)"Continue" else "Begin")}}
}
