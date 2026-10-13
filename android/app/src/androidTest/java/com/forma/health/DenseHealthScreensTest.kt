package com.forma.health

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class DenseHealthScreensTest {
    @get:Rule val compose=createAndroidComposeRule<NativePreviewActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    @Test fun dashboardPrioritizesHealthAndWorkoutsOpenWithRecordedHeartContext() {
        val model=FormaViewModel(app)
        compose.setContent { FormaApp(model,{},{},{},{}) }
        compose.onNodeWithContentDescription("Check in").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Vitals at a glance"))
        compose.onNodeWithText("HRV · RMSSD").assertIsDisplayed()
        compose.waitForIdle()
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: app.getExternalFilesDir(null)!!
        folder.mkdirs()
        File(folder,"forma-vitals-overview.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Morning run"))
        compose.onAllNodesWithText("Morning run").onFirst().performClick()
        compose.onNodeWithText("Workout details").assertIsDisplayed()
        compose.onNodeWithText("Observed heart-rate distribution").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("These are BPM bands",substring=true).performScrollTo().assertIsDisplayed()
    }
    @Test fun healthTabShowsAllCategoriesAndNutritionIsDiscoverable() {
        val model=FormaViewModel(app)
        compose.setContent { FormaApp(model,{},{},{},{},initialPage="You") }
        compose.onNodeWithText("46 supported measurements",ignoreCase=true).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Nutrition"))
        compose.onNodeWithText("Protein").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("28D").assertIsDisplayed()
        compose.onAllNodesWithText("Protein").onLast().assertIsDisplayed()
    }
    @Test fun disablingOrClearingRejectsLateWatchFrames() {
        val now=Instant.now();val frame=WatchFrame("Watch6",now,75.0,now,1000.0,now)
        WatchLink.enable(app,true);assertTrue(WatchLink.receive(app,frame.json().toByteArray()))
        WatchLink.enable(app,false);assertFalse(WatchLink.receive(app,frame.json().toByteArray()));assertNull(WatchLink.state.value.frame)
        val model=FormaViewModel(app);compose.runOnUiThread { model.clearAll() }
        assertFalse(WatchLink.receive(app,frame.json().toByteArray()))
    }
    @Test fun directWatchOptInDoesNotChangeSamsungPermissions() {
        val model=FormaViewModel(app)
        compose.setContent { FormaApp(model,{},{},{},{},initialSheet="Direct watch") }
        compose.onNodeWithText("Receive watch measurements").assertIsDisplayed()
        compose.onNodeWithText("Get the watch companion").assertIsDisplayed()
        compose.onNodeWithText("This does not take over your Samsung workouts",substring=true).performScrollTo().assertIsDisplayed()
    }
}
