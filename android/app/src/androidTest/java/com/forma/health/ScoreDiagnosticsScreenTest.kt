package com.forma.health

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*
import kotlin.math.exp

class ScoreDiagnosticsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<NativePreviewActivity>()
    @Test fun matching86sHaveVisibleRoundingReasonAndCopyableActualInputs() {
        val date=LocalDate.now();val prior=(1..14).map { HealthDay(date.minusDays(it.toLong()),mapOf("hrv" to 60.0,"restingHeartRate" to 60.0)) }
        val day=HealthDay(date,mapOf("sleepMinutes" to 412.8,"sleepEfficiency" to 86.0,"hrv" to 60*exp(.1*11/12),"restingHeartRate" to 57.25),samples=listOf(EnergySample(10.0,steps=100.0,heartRate=57.25,recordedAt=Instant.now().minusSeconds(10800))))
        val sleep=Scores.sleep(day,480);val recovery=Scores.recovery(day,prior,480);val energy=Scores.energy(day,prior,480)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ScoreDashboard(sleep,recovery,energy) {}
            ScoreDiagnosticsPanel("Body energy",day,sleep,recovery,energy,480,"Samsung Health · Health Connect")
        } } }
        compose.onAllNodesWithText("86").assertCountEquals(3)
        compose.onNodeWithText("Before rounding").assertIsDisplayed()
        compose.onNodeWithText("85.85").assertIsDisplayed()
        compose.onNodeWithText("Why the scores match").assertIsDisplayed()
        compose.onNodeWithText("-0.15 points",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Waiting for newer Samsung-shared activity",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Copy score details").performScrollTo().performClick()
        compose.onNodeWithText("Score details copied").assertIsDisplayed()
        val clipboard=compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text=clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.contains("Unrounded energy: 85.85"));assertTrue(text.contains("Steps used by energy: 100.00"))
        clipboard.clearPrimaryClip()
    }
}
