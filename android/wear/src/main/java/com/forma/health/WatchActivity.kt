package com.forma.health

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.health.services.client.*
import androidx.health.services.client.data.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import java.time.Instant

class WatchActivity:ComponentActivity() {
    private val updates:AppUpdateViewModel by viewModels()
    private val installAccess=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(packageManager.canRequestPackageInstalls())updates.install()else updates.installError("Allow update installation, then try again.")
    }
    private var showUpdates by mutableStateOf(false)
    private var message by mutableStateOf("Paired with your phone through Galaxy Wearable")
    private var heart by mutableStateOf<Double?>(null)
    private var measuring by mutableStateOf(false)
    private var activityEnabled by mutableStateOf(false)
    private var measurementJob:Job?=null
    private var lastSent=Instant.EPOCH
    private var pendingAction=""
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(pendingAction=="heart" && WatchSensors.heartGranted(this))measure()
        else if(pendingAction=="activity" && WatchSensors.activityGranted(this))daily(true)
        else message="Permission wasn’t granted. You can try again."
        pendingAction=""
    }
    private val callback=object:MeasureCallback {
        override fun onAvailabilityChanged(dataType:DeltaDataType<*,*>,availability:Availability) {
            if(availability is DataTypeAvailability && availability!=DataTypeAvailability.AVAILABLE)message="Adjust your watch fit. Waiting for a reliable reading."
        }
        override fun onRegistrationFailed(throwable:Throwable) { measuring=false;message="Heart-rate measurement unavailable. Check permissions and try again." }
        override fun onDataReceived(data:DataPointContainer) {
            if(!measuring || !WatchSensors.heartGranted(this@WatchActivity))return
            val point=data.getData(DataType.HEART_RATE_BPM).maxByOrNull { it.timeDurationFromBoot } ?: return
            if(!point.value.isFinite() || point.value !in 20.0..240.0)return
            val now=Instant.now();val at=point.getTimeInstant(now.minusMillis(SystemClock.elapsedRealtime()))
            heart=point.value;message="Measuring · sends to nearby Forma phone"
            if(java.time.Duration.between(lastSent,now).seconds>=2) {
                lastSent=now;WatchSensors.saveAndSend(this@WatchActivity,WatchFrame(Build.MODEL,now,heart=point.value,heartAt=at))
            }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        activityEnabled=WatchSensors.enabled(this)
        setContent {
            val updateState by updates.state.collectAsStateWithLifecycle()
            if(showUpdates)WatchUpdateScreen(updateState,updates::check,updates::install,updates::cancelDownload,{showUpdates=false})
            else WatchScreen(heart,measuring,activityEnabled,message,onMeasure={if(measuring)stopMeasurement()else if(WatchSensors.heartGranted(this))measure()else { pendingAction="heart";permissions.launch(arrayOf(WatchSensors.heartPermission())) }},onDaily={if(activityEnabled)daily(false)else if(WatchSensors.activityGranted(this))daily(true)else { pendingAction="activity";permissions.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)) }},onUpdates={stopMeasurement();showUpdates=true;updates.check(force=true)}) }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { updates.installation.collect { file->
            try {
                if(!packageManager.canRequestPackageInstalls())installAccess.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
                else {
                    val uri=FileProvider.getUriForFile(this@WatchActivity,"$packageName.updates",file)
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=android.content.ClipData.newRawUri("Forma update",uri) })
                }
            } catch(e:Exception) { updates.installError("This watch couldn’t open its installer. Install the signed companion update through wireless debugging instead.") }
        } } }
        if(activityEnabled && WatchSensors.activityGranted(this))daily(true)
    }
    private fun measure() {
        if(measuring)return
        measurementJob?.cancel()
        measurementJob=lifecycleScope.launch {
            try {
                val client=HealthServices.getClient(this@WatchActivity).measureClient
                check(DataType.HEART_RATE_BPM in client.getCapabilitiesAsync().await().supportedDataTypesMeasure) { "Heart rate is unavailable on this watch." }
                measuring=true;heart=null;message="Hold still while your watch measures"
                client.registerMeasureCallback(DataType.HEART_RATE_BPM,callback)
                delay(60000)
            }catch(e:CancellationException) { throw e }
            catch(e:Exception) { message=e.message ?: "Couldn’t begin measurement" }
            finally { measuring=false;runCatching { HealthServices.getClient(this@WatchActivity).measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM,callback) };if(heart!=null)message="Check complete. Daily activity continues if enabled." }
        }
    }
    private fun stopMeasurement() { measuring=false;measurementJob?.cancel();runCatching { HealthServices.getClient(this).measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM,callback) } }
    private fun daily(enable:Boolean) { lifecycleScope.launch { try { WatchSensors.setDaily(this@WatchActivity,enable);activityEnabled=enable;message=if(enable)"Daily activity enabled · updates can be batched" else "Daily activity stopped" }catch(e:CancellationException){throw e}catch(e:Exception){message=e.message ?: "Activity sync unavailable"} } }
    override fun onPause() { stopMeasurement();super.onPause() }
}
@Composable fun WatchScreen(heart:Double?,measuring:Boolean,daily:Boolean,message:String,onMeasure:()->Unit,onDaily:()->Unit,onUpdates:()->Unit={}) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFD8FC70),onPrimary=Color(0xFF101211),surface=Color(0xFF101211),onSurface=Color(0xFFF4F5EC))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101211)).verticalScroll(rememberScrollState()).padding(horizontal=32.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("FORMA",fontSize=11.sp,letterSpacing=3.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
            Text(heart?.toInt()?.toString() ?: "—",fontSize=58.sp,lineHeight=63.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFF647F))
            Text(if(measuring)"HEART RATE · BPM" else "WATCH LINK",fontSize=8.sp,letterSpacing=1.sp)
            Button(onClick=onMeasure,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)) { Text(if(measuring)"Stop measurement" else "Measure heart rate",fontSize=11.sp) }
            OutlinedButton(onClick=onDaily,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)) { Text(if(daily)"Stop daily activity" else "Enable daily activity",fontSize=10.sp) }
            TextButton(onClick=onUpdates) { Text("App updates",fontSize=10.sp) }
            Text(message,fontSize=10.sp,lineHeight=16.sp,textAlign=TextAlign.Center,color=Color(0xFF9AABA0))
            Text("Live checks last 60 seconds.\nSteps, calories, distance and floors use Wear OS daily activity.",fontSize=9.sp,lineHeight=14.sp,textAlign=TextAlign.Center,color=Color(0xFF9AABA0))
        }
    }
}

@Composable fun WatchUpdateScreen(state:AppUpdateState,onCheck:(Boolean)->Unit,onInstall:()->Unit,onCancel:()->Unit,onBack:()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFD8FC70),onPrimary=Color(0xFF101211))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101211)).verticalScroll(rememberScrollState()).padding(horizontal=32.dp,vertical=30.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("FORMA UPDATES",fontSize=10.sp,letterSpacing=1.sp,color=MaterialTheme.colorScheme.primary)
            Text("${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",fontSize=10.sp)
            Text(when { state.checking->"Checking…";state.downloading->"Downloading ${(state.progress*100).toInt()}%";state.release!=null->"${state.release.versionName} available";state.hasChecked->"No newer companion release";else->"Check for a companion update" },fontSize=13.sp,textAlign=TextAlign.Center)
            if(state.downloading)TextButton(onClick=onCancel) { Text("Cancel download",fontSize=11.sp) }
            else if(state.release!=null)Button(onClick=onInstall,enabled=!state.checking) { Text(if(state.ready)"Install update" else "Download update",fontSize=11.sp) }
            OutlinedButton(onClick={onCheck(true)},enabled=!state.checking && !state.downloading) { Text("Check updates",fontSize=11.sp) }
            state.error?.let { Text(it,fontSize=10.sp,lineHeight=16.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.error) }
            Text("Signed updates from Forma’s GitHub releases. Confirm installation on your watch. If its installer is unavailable, use wireless debugging to update in place.",fontSize=9.sp,lineHeight=14.sp,textAlign=TextAlign.Center,color=Color(0xFF9AABA0))
            TextButton(onClick=onBack) { Text("Back to watch",fontSize=11.sp) }
        }
    }
}
