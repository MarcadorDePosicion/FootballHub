package pl.stasgroch.footballhub

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.FileInputStream
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import pl.stasgroch.footballhub.data.remote.DemoData
import pl.stasgroch.footballhub.ui.screens.StandingsScreen
import pl.stasgroch.footballhub.ui.theme.FootballHubTheme

class StandingsLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Test fun narrowTableWithLargeTextKeepsTeamLinks() {
        var selected = -1
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                FootballHubTheme {
                    Surface(Modifier.width(320.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
                        StandingsScreen(3, DemoData.create(), setOf(12)) { selected = it }
                    }
                }
            }
        }
        rule.onNodeWithText("Pkt").assertExists()
        rule.onNodeWithText("Jagiellonia Białystok").performClick()
        assertEquals(12, selected)
        rule.onNodeWithTag("standings-scroll").performTouchInput { swipeLeft() }
        rule.onNodeWithText("Pkt").assertIsDisplayed()
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "standings-large-text.png")
        output.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        automation.executeShellCommand("mkdir -p /sdcard/Download/footballhub-ui").use { FileInputStream(it.fileDescriptor).readBytes() }
        automation.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/footballhub-ui/standings-large-text.png").use { FileInputStream(it.fileDescriptor).readBytes() }
    }
}

