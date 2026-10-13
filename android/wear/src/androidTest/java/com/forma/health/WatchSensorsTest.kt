package com.forma.health

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.health.services.client.*
import androidx.health.services.client.data.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*

class WatchSensorsTest {
    @get:Rule val compose=createAndroidComposeRule<WatchActivity>()
    @Test fun roundWatchHasExplicitMeasurementAndDailyActivityControls() {
        compose.onNodeWithText("FORMA").assertIsDisplayed()
        compose.onNodeWithText("Measure heart rate").assertIsDisplayed()
        compose.onNodeWithText("Enable daily activity").performScrollTo().assertIsDisplayed()
    }
    @Test fun servicePacketsKeepSensorMeasurementTimeAndNewestDailyCounters() {
        val bootAge=android.os.SystemClock.elapsedRealtime()
        val before=Instant.now()
        val data=DataPointContainer(listOf(
            IntervalDataPoint(DataType.STEPS_DAILY,123L,Duration.ZERO,Duration.ofMillis(bootAge-10000)),
            IntervalDataPoint(DataType.STEPS_DAILY,456L,Duration.ZERO,Duration.ofMillis(bootAge-5000)),
            IntervalDataPoint(DataType.DISTANCE_DAILY,320.0,Duration.ZERO,Duration.ofMillis(bootAge-5000))))
        val frame=WatchSensors.dailyFrame(data)
        assertEquals(456.0,frame.steps!!,.001);assertEquals(.32,frame.distanceKm!!,.001)
        assertTrue(Duration.between(frame.stepsAt,before).seconds in 3..7)
        assertNull(frame.heart);assertNull(frame.calories)
    }
    @Test fun healthServicesDeliversSyntheticHeartSamplesWithGrantedPermission()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,WatchSensors.heartPermission())
        val client=HealthServices.getClient(context).measureClient;val measured=CompletableDeferred<Double>()
        val callback=object:MeasureCallback {
            override fun onAvailabilityChanged(dataType:DeltaDataType<*,*>,availability:Availability) {}
            override fun onRegistrationFailed(throwable:Throwable) { measured.completeExceptionally(throwable) }
            override fun onDataReceived(data:DataPointContainer) { data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { measured.complete(it.value) } }
        }
        client.registerMeasureCallback(DataType.HEART_RATE_BPM,callback)
        try { val value=withTimeout(20000) { measured.await() };assertTrue(value in 20.0..240.0) }
        finally { client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM,callback).await() }
    }
    @Test fun healthServicesProvidesHeartCapabilityOnTheWearTestDevice()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val capability=HealthServices.getClient(context).measureClient.getCapabilitiesAsync().await()
        assertTrue(DataType.HEART_RATE_BPM in capability.supportedDataTypesMeasure)
    }
}
