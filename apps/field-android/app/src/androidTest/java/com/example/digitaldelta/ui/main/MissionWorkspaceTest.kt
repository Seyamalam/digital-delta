package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.digitaldelta.domain.fleet.*
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.domain.sync.MissionField
import com.example.digitaldelta.domain.triage.CargoPriority
import com.example.digitaldelta.theme.DigitalDeltaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Presentation fixture only. IndependentFieldWorkflowTest proves real authority and persistence. */
@OptIn(ExperimentalTestApi::class)
class MissionWorkspaceTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val language = mutableStateOf("bn")
    private val missions = mutableStateOf(listOf(fixture()))
    private val commands = mutableListOf<DispatchCommand>()
    private val edits = mutableListOf<Triple<String, MissionField, String>>()
    private val reviews = mutableListOf<Triple<String, String, Set<String>>>()
    private fun fixture() = FieldMission("ui-urgent", "N1", "N6", CargoPriority.P0, "5", "", true, null, null,
        emptyList(), true, true, false, false, false, listOf("N1", "N6"), "N1", true,
        emptyList(), emptySet(), dispatchReviewIds = setOf("creation", "quantity-edit"), eligibleOperators = listOf("RLY-01"),
        readerNodes = setOf("N1", "RLY-01", "N6"))
    private fun launch() {
        ui.setContent {
            val args = InstrumentationRegistry.getArguments()
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(args.getString("qaFontScale")?.toFloat() ?: 1f)) {
                DigitalDeltaTheme(darkTheme = args.getString("qaDarkMode") == "true") {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        MissionWorkspaceContent(language.value, missions.value, false, false, null, null,
                            MissionWorkspaceActions({}, {}, { id, field, value -> edits += Triple(id, field, value) },
                                { _, _ -> }, { id, reason, ids -> reviews += Triple(id, reason, ids) }, { commands += it }, {}))
                    }
                }
            }
        }
    }
    private fun show(tag: String) {
        ui.onNodeWithTag("field-missions").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
    }
    private fun capture(name: String) {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("captureMissionEvidence") != "true") return
        ui.waitForIdle()
        val started = android.os.SystemClock.uptimeMillis()
        ui.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() - started >= 400 }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val theme = if (args.getString("qaDarkMode") == "true") "dark" else "light"
        val output = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "mission-$theme-$name.png")
        output.outputStream().use { check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun bilingualDispatchRequiresReviewAndKeepsTheReviewedRevision() {
        launch()
        show("dispatch-plan-ui-urgent")
        ui.onNodeWithTag("dispatch-confirm").assertIsNotEnabled()
        capture("bn-dispatch")
        ui.onNodeWithTag("dispatch-reviewed").performScrollTo().performClick()
        // A live arrival while the dialog is open must not silently replace reviewed IDs.
        ui.runOnIdle { missions.value = listOf(fixture().copy(dispatchReviewIds = setOf("new-unreviewed-event"))) }
        ui.onNodeWithTag("dispatch-confirm").performClick()
        ui.runOnIdle {
            assertEquals(setOf("creation", "quantity-edit"), commands.single().reviewedEventIds)
            assertEquals("RLY-01", commands.single().operatorNodeId)
            assertEquals(VehicleType.TRUCK, commands.single().vehicle)
            language.value = "en"
        }
        show("dispatch-plan-ui-urgent")
        ui.onNodeWithTag("dispatch-confirm").assertIsNotEnabled()
        capture("en-dispatch")
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(1, commands.size)
        show("dispatch-plan-ui-urgent")
        ui.onNodeWithTag("dispatch-confirm").assertIsNotEnabled()
    }

    @Test fun bilingualAssignmentAndReconciliationKeepCustodyReviewExplicit() {
        launch()
        show("edit-ui-urgent")
        ui.onNodeWithTag("mission-field-menu").performClick()
        ui.onNodeWithTag("mission-field-DISPATCH").assertDoesNotExist()
        ui.onNodeWithTag("mission-field-CUSTODY_PATH").performClick()
        ui.onNodeWithTag("mission-field-value").performTextInput("N1>RLY-01>N6")
        capture("bn-assignment")
        ui.onNodeWithTag("mission-field-save").performClick()
        ui.runOnIdle {
            assertEquals(Triple("ui-urgent", MissionField.CUSTODY_PATH, "N1>RLY-01>N6"), edits.single())
            missions.value = listOf(fixture().copy(custodyNeedsReconciliation = true,
                pendingCustodyChanges = listOf(MissionField.MEDICAL_QUANTITY to "12"), pendingCustodyChangeIds = setOf("crossing-edit")))
        }
        show("reconcile-ui-urgent")
        ui.onNodeWithTag("reconcile-confirm").assertIsNotEnabled()
        capture("bn-reconcile")
        ui.runOnIdle { language.value = "en" }
        capture("en-reconcile")
        ui.onNodeWithTag("reconcile-reason").performTextInput("Keep the signed five packs; request seven separately.")
        ui.onNodeWithTag("reconcile-confirm").performClick()
        ui.runOnIdle { assertEquals(setOf("crossing-edit"), reviews.single().third) }
        show("edit-ui-urgent")
        ui.onNodeWithTag("mission-field-menu").performClick()
        ui.onNodeWithTag("mission-field-CUSTODY_PATH").performClick()
        ui.onNodeWithTag("mission-field-value").performTextInput("N1>RLY-01>N6")
        capture("en-assignment")
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(1, edits.size)
    }
}
