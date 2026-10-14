package com.forma.health

import android.app.Application
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File

class UxAuditTest {
    @get:Rule val compose=createAndroidComposeRule<NativePreviewActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    @Before fun cleanFixture() { HealthStore(app).clearAll();WatchLink.enable(app,false) }
    @After fun removeFixture() { HealthStore(app).clearAll();WatchLink.enable(app,false) }
    private fun render(sheet:String="",onImport:()->Unit={},onExport:()->Unit={}):FormaViewModel {
        val model=FormaViewModel(app);compose.setContent { FormaApp(model,{},{},onImport,onExport,initialSheet=sheet) };return model
    }
    @Test fun statusIsNotADuplicateButtonAndDataHasItsOwnDestinationAndBackPath() {
        render()
        compose.onNodeWithText("Demo mode").assert(hasClickAction().not())
        compose.onNodeWithContentDescription("Watch connection").performClick()
        compose.onNodeWithText("Devices").assertIsDisplayed();compose.onNodeWithText("Export sample records").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dismiss panel").performClick()
        compose.onNodeWithContentDescription("Health settings").performClick()
        compose.onNodeWithText("Your data").performClick()
        compose.onNodeWithText("Export sample records").assertIsDisplayed();compose.onNodeWithText("Allow access & connect").assertDoesNotExist()
        compose.onNodeWithText("Data availability").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Export sample records").assertIsDisplayed()
    }
    @Test fun fileToolsDispatchOnlyFromDataAndClearRequiresConfirmation() {
        var imported=0;var exported=0;val model=render("Your data",{imported++},{exported++})
        compose.onNodeWithText("Import Forma JSON").assertDoesNotExist()
        compose.onNodeWithText("Export sample records").performClick();compose.waitForIdle();assertEquals(1,exported)
        compose.onNodeWithText("File import").performScrollTo().performClick()
        compose.onNodeWithText("Import Forma JSON").performScrollTo().performClick();compose.waitForIdle();assertEquals(1,imported)
        compose.onNodeWithText("Clear local data").performScrollTo().performClick()
        compose.onNodeWithText("Keep my data").performClick();compose.waitForIdle();assertTrue(model.state.value.data.demo)
        compose.onNodeWithText("Clear local data").performClick();compose.onNodeWithText("Clear data").performClick()
        assertNull(model.store.health());assertFalse(model.store.prefs().automaticSync)
    }
    @Test fun targetsPersistAndUnusedNameAndBedtimeFieldsAreGone() {
        val model=render("Preferences")
        compose.onNodeWithText("Your name").assertDoesNotExist();compose.onNodeWithText("Bedtime target (HH:mm)").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress)).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(540f) }
        assertEquals(540,model.store.prefs().sleepGoal)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))[1].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(12000f) }
        assertEquals(12000,model.store.prefs().stepGoal)
    }
    @Test fun scoreShowsValuesBeforeExpandableReportAndCanStillCopyDetails() {
        render("Body energy")
        compose.onNodeWithText("Starting energy").assertIsDisplayed()
        compose.onNodeWithText("Copy score details").assertDoesNotExist()
        compose.onNodeWithText("Calculation details").performScrollTo().performClick()
        compose.onNodeWithText("Copy score details").performScrollTo().performClick()
        compose.onNodeWithText("Score details copied").assertIsDisplayed()
    }
    @Test fun missingBaselineDoesNotPresentSleepAsAvailableEnergyPoints() {
        val model=FormaViewModel(app)
        model.store.saveHealth(HealthData("Samsung Health · Health Connect",listOf(HealthDay(LocalDate.now(),mapOf("sleepMinutes" to 480.0)))))
        compose.runOnUiThread { model.refreshLocal() }
        compose.setContent { FormaApp(model,{},{},{},{},initialSheet="Body energy") }
        compose.onNodeWithText("Needs 7 baseline days").assertIsDisplayed()
        compose.onNodeWithText("Last night’s sleep").assertDoesNotExist()
    }
    @Test fun manualActivityRejectsEmptyDurationThenSavesAndDeletes() {
        val model=render("Log activity")
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Save activity").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("25")
        compose.onNodeWithText("Save activity").performClick();compose.waitForIdle();assertEquals(25,model.store.workouts().single().minutes)
        compose.onNodeWithContentDescription("Health settings").performClick();compose.onNodeWithText("Personal tools").performClick()
        compose.onNodeWithText("Manual activity log").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delete activity").performClick();compose.waitForIdle();assertTrue(model.store.workouts().isEmpty())
    }
    @Test fun journalAndWaterControlsPersistWithoutChangingRecordedScores() {
        val model=render("Check in");val before=Scores.recovery(model.state.value.data.days.last(),model.state.value.data.days,480)
        compose.onNode(hasSetTextAction()).performTextInput("Audit fixture")
        compose.onNodeWithText("Save check-in").performScrollTo().performClick();compose.waitForIdle();assertEquals("Audit fixture",model.store.journals().single().note)
        compose.onNodeWithContentDescription("Health settings").performClick();compose.onNodeWithText("Personal tools").performClick()
        compose.onNodeWithText("Water log").performScrollTo().performClick();compose.onNodeWithText("+ 250 ml").performClick()
        assertEquals(250,model.store.water(model.state.value.selected));compose.onNodeWithText("Undo 250 ml").performClick();compose.waitForIdle();assertEquals(0,model.store.water(model.state.value.selected))
        assertEquals(before,Scores.recovery(model.state.value.data.days.last(),model.state.value.data.days,480))
    }
    @Test fun breathingTimerRunsAndPausesAndDatePickerCanConfirm() {
        render("Breathe");compose.mainClock.autoAdvance=false
        compose.onNodeWithText("Begin").performClick();compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithText("0:01 / 2:00").assertIsDisplayed()
        compose.onNodeWithText("Pause").performClick();compose.mainClock.advanceTimeByFrame();compose.onNodeWithText("Continue").assertIsDisplayed();compose.mainClock.autoAdvance=true
        compose.onNodeWithContentDescription("Dismiss panel").performClick()
        compose.onNodeWithContentDescription("Choose day").performClick();compose.onNodeWithText("View day").assertIsEnabled().performClick()
        compose.onNodeWithText("Your day.").assertIsDisplayed()
    }
    @Test fun phonePermissionRationaleHasAccessControlAndAnOptionalFullPolicy() {
        var opened=0;val model=FormaViewModel(app)
        compose.setContent { FormaApp(model,{},{},{},{},showPrivacy=true,onHealthSettings={opened++}) }
        compose.onNodeWithText("Full privacy details").assertIsDisplayed()
        compose.onNodeWithText("Google's encrypted relay",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Manage health permissions").performClick();compose.waitForIdle();assertEquals(1,opened)
        compose.onNodeWithText("Full privacy details").performClick();compose.onNodeWithText("Google's encrypted relay",substring=true).performScrollTo().assertIsDisplayed()
    }
    @Test fun syncControlsRespectBusyAndPermissionStatesAndDispatchActions() {
        var synced=0;var paused=0;var history=0;var allowed=0;var background=false
        var state by mutableStateOf(FormaState(HealthStore(app).demo(),Preferences(),LocalDate.now(),connection=ConnectionState(available=true,granted=setOf("android.permission.health.READ_STEPS"),historyAvailable=true,backgroundAvailable=true)))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { WatchConnectionPanel(state,{allowed++},{synced++;state=state.copy(busy=true)},{paused++;state=state.copy(busy=false)},{history++},{},{},{},{background=it}) } } }
        compose.onNodeWithText("Sync now").performClick();compose.waitForIdle();assertEquals(1,synced);compose.onNodeWithText("Syncing…").assertIsNotEnabled()
        compose.onNodeWithText("Pause history import").performClick();compose.waitForIdle();assertEquals(1,paused)
        compose.onNodeWithText("Sync settings").performScrollTo().performClick()
        compose.onNodeWithText("Change read access").performScrollTo().performClick();compose.waitForIdle();assertEquals(1,allowed)
        compose.onNodeWithText("Allow older history").performScrollTo().performClick();compose.waitForIdle();assertEquals(1,history)
        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick();compose.waitForIdle();assertTrue(background)
    }
    @Test fun recordImportRoundTripsAndPausesLiveReadsAndRejectsInvalidFiles() {
        val model=FormaViewModel(app);val sample=model.store.demo();val json=HealthJson.encode(sample)
        compose.runOnUiThread { model.import(json) }
        compose.waitUntil(5000) { model.store.health()!=null }
        assertEquals(sample.days,model.store.health()!!.days);assertFalse(model.store.prefs().automaticSync)
        val saved=model.store.health()
        compose.runOnUiThread { model.clearMessage();model.import("invalid file") }
        compose.waitUntil(5000) { model.state.value.message!=null }
        assertEquals(saved,model.store.health())
    }
    @Test fun compactDataPanelRendersAsAnAppRatherThanAReport() {
        val model=FormaViewModel(app);val snapshot=model.state.value
        compose.setContent { FormaTheme { androidx.compose.material3.Surface(color=androidx.compose.ui.graphics.Color(0xFF101211),contentColor=androidx.compose.material3.MaterialTheme.colorScheme.onSurface) { Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101211)).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { androidx.compose.material3.Text("Your data",fontSize=26.sp);DataManagementPanel(snapshot,{},{},{},{},{}) } } } }
        compose.onNodeWithText("Sample data").assertIsDisplayed();compose.onNodeWithText("Import Forma JSON").assertDoesNotExist()
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: app.getExternalFilesDir(null)!!;folder.mkdirs()
        File(folder,"forma-data-panel.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
