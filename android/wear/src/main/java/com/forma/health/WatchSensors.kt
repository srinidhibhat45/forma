package com.forma.health

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.core.content.ContextCompat
import androidx.health.services.client.*
import androidx.health.services.client.data.*
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import java.time.Instant

object WatchSensors {
    private fun prefs(c:Context)=c.getSharedPreferences("forma_watch",Context.MODE_PRIVATE)
    fun enabled(c:Context)=prefs(c).getBoolean("activityEnabled",false)
    fun heartPermission()=if(Build.VERSION.SDK_INT>=36)"android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS
    fun activityGranted(c:Context)=ContextCompat.checkSelfPermission(c,Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED
    fun heartGranted(c:Context)=ContextCompat.checkSelfPermission(c,heartPermission())==PackageManager.PERMISSION_GRANTED
    suspend fun setDaily(c:Context,enabled:Boolean) {
        val client=HealthServices.getClient(c).passiveMonitoringClient
        if(!enabled) { prefs(c).edit().putBoolean("activityEnabled",false).remove("frame").apply();client.clearPassiveListenerServiceAsync().await();return }
        check(activityGranted(c)) { "Allow physical activity access on your watch." }
        val cap=client.getCapabilitiesAsync().await()
        val wanted=setOf(DataType.STEPS_DAILY,DataType.CALORIES_DAILY,DataType.DISTANCE_DAILY,DataType.FLOORS_DAILY).intersect(cap.supportedDataTypesPassiveMonitoring)
        check(wanted.isNotEmpty()) { "Daily activity is unavailable on this watch." }
        prefs(c).edit().putBoolean("activityEnabled",true).apply()
        try { client.setPassiveListenerServiceAsync(WatchActivityDataService::class.java,PassiveListenerConfig.builder().setDataTypes(wanted).build()).await() }
        catch(e:Exception) { prefs(c).edit().putBoolean("activityEnabled",false).apply();throw e }
    }
    @Synchronized fun saveAndSend(c:Context,next:WatchFrame) {
        val old=prefs(c).getString("frame",null)?.let { runCatching { WatchFrame.decode(it.toByteArray()) }.getOrNull() }
        val frame=WatchFrame.merge(old,next)
        prefs(c).edit().putString("frame",frame.json()).apply()
        Wearable.getCapabilityClient(c).getCapability("forma_phone_v1",CapabilityClient.FILTER_REACHABLE).addOnSuccessListener { cap->
            cap.nodes.filter { it.isNearby }.forEach { node->Wearable.getMessageClient(c).sendMessage(node.id,WatchFrame.PATH,frame.json().toByteArray()).addOnFailureListener { /* Next reading retries; no health payload is logged. */ } }
        }
    }
    fun dailyFrame(points:DataPointContainer):WatchFrame {
        val now=Instant.now();val boot=now.minusMillis(SystemClock.elapsedRealtime())
        val steps=points.getData(DataType.STEPS_DAILY).maxByOrNull { it.endDurationFromBoot }
        val cal=points.getData(DataType.CALORIES_DAILY).maxByOrNull { it.endDurationFromBoot }
        val distance=points.getData(DataType.DISTANCE_DAILY).maxByOrNull { it.endDurationFromBoot }
        val floors=points.getData(DataType.FLOORS_DAILY).maxByOrNull { it.endDurationFromBoot }
        return WatchFrame(Build.MODEL,now,steps=steps?.value?.toDouble(),stepsAt=steps?.getEndInstant(boot),calories=cal?.value,caloriesAt=cal?.getEndInstant(boot),distanceKm=distance?.value?.div(1000),distanceAt=distance?.getEndInstant(boot),floors=floors?.value,floorsAt=floors?.getEndInstant(boot))
    }
}
class WatchActivityDataService:PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints:DataPointContainer) {
        if(WatchSensors.enabled(this) && WatchSensors.activityGranted(this))WatchSensors.saveAndSend(this,WatchSensors.dailyFrame(dataPoints))
    }
    override fun onPermissionLost() { getSharedPreferences("forma_watch",MODE_PRIVATE).edit().putBoolean("activityEnabled",false).remove("frame").apply() }
}
class WatchBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED || !WatchSensors.enabled(context) || !WatchSensors.activityGranted(context))return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { withTimeout(8000) { WatchSensors.setDaily(context,true) } }catch(e:Exception) { /* Retry when the watch app opens. */ }finally { pending.finish() } }
    }
}
