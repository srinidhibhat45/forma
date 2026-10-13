package com.forma.health

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Rule
import org.junit.Test
import android.graphics.Bitmap
import java.io.File

class StartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun nativeDashboardStartsWithHonestDemoAndScores() {
        compose.onNodeWithText("Your day.").assertIsDisplayed()
        compose.onNodeWithText("Demo mode").assertIsDisplayed()
        compose.onNodeWithText("Make today count.").assertIsDisplayed()
        compose.onNodeWithText("Active calories").assertIsDisplayed()
        compose.onNodeWithText("Exercise").assertIsDisplayed()
        compose.onNodeWithText("Heart rate").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val outputDir=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val folder=outputDir?.let{File(it)}?:compose.activity.getExternalFilesDir(null)!!
        folder.mkdirs()
        val file=File(folder,"forma-today.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
