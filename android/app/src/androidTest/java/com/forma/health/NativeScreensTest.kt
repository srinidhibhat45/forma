package com.forma.health

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Render native screens directly without driving the emulator's operating-system UI. */
class NativeScreensTest {
    @get:Rule val compose=createAndroidComposeRule<NativePreviewActivity>()
    private fun render(page:String,heading:String,fileName:String,sheet:String="") {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model=FormaViewModel(app)
        compose.setContent { FormaApp(model,{},{},{},{},initialPage=page,initialSheet=sheet) }
        compose.waitForIdle()
        compose.onNodeWithText(heading).assertIsDisplayed()
        compose.waitForIdle()
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: File(app.getExternalFilesDir(null),"screenshots")
        folder.mkdirs()
        File(folder,fileName).outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun rangeAndPointInspectionWork() {
        render("Trends","The long view.","forma-trends-interaction.png")
        compose.onNodeWithText("28D").performClick()
        compose.onNodeWithText("DAILY AVERAGE / 28 DAYS").assertIsDisplayed()
        compose.onNode(hasContentDescription("Trend chart.",substring=true)).performTouchInput { click(center) }
        compose.onNode(SemanticsMatcher("selected chart value and date") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any {
                Regex("""^\d+(?:\.\d+)?h? / \d{1,2} [A-Z][a-z]{2}$""").matches(it.text)
            } == true
        }).assertIsDisplayed()
    }
    @Test fun energyCalculationOpensFromInstrument() {
        render("Today","Your day.","forma-energy-preview.png")
        compose.onNodeWithContentDescription("How body energy is estimated").performClick()
        compose.onNodeWithText("Body energy").assertIsDisplayed()
        compose.onNodeWithText("Starting energy: 88 points").assertIsDisplayed()
    }
    @Test fun sleepRenders(){render("Sleep","The night shift.","forma-sleep.png")}
    @Test fun activityRenders(){render("Move","Built to move.","forma-move.png")}
    @Test fun trendsRender(){render("Trends","The long view.","forma-trends.png")}
    @Test fun personalSpaceRenders(){render("You","Make it yours.","forma-you.png")}
    @Test fun recoveryRenders(){render("Recovery","Ready, or reset.","forma-recovery.png")}
    @Test fun updateActionsReflectDownloadAndCancellation() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model=FormaViewModel(app)
        val release=AppRelease(4,"1.2.0","https://github.com/srinidhibhat45/forma/releases/download/build-4/forma.apk","ab".repeat(32),6_000_000,"Fresh improvements.","https://github.com/srinidhibhat45/forma/releases/tag/build-4")
        val state=mutableStateOf(AppUpdateState(release=release))
        compose.setContent { FormaApp(model,{},{},{},{},initialPage="You",initialSheet="App updates",
            updateState=state.value,onInstallUpdate={state.value=state.value.copy(downloading=true,progress=.5f)},
            onCancelUpdate={state.value=state.value.copy(downloading=false,progress=0f)},
            onCheckUpdates={state.value=state.value.copy(checking=true)}) }
        compose.onNodeWithText("Download & update").performClick()
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithText("Check for updates").assertIsNotEnabled()
        compose.onNodeWithText("Cancel download").performClick()
        compose.onNodeWithText("Download & update").assertIsDisplayed()
        compose.onNodeWithText("Check for updates").performClick()
        compose.onNodeWithText("Checking GitHub for a new release…").assertIsDisplayed()
    }
}
