package com.example.digitaldelta.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitaldelta.R
import com.example.digitaldelta.domain.sync.*
import java.util.Locale

/** Room-backed missions are separate from the labelled scenario controls. */
@Composable
fun MissionWorkspace(language: String, model: MissionWorkspaceViewModel = viewModel()) {
    val context = LocalContext.current
    val localized = remember(context, language) { context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }) }
    val missions by model.missions.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val failed by model.failed.collectAsStateWithLifecycle()
    val selectedMission by model.selectedMission.collectAsStateWithLifecycle()
    val recordedPlan by model.recordedPlan.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FieldMission?>(null) }
    var field by remember { mutableStateOf(MissionField.DESTINATION) }
    var value by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reconciling by remember { mutableStateOf<FieldMission?>(null) }
    var reconciliationReason by remember { mutableStateOf("") }
    fun label(field: MissionField): String = localized.getString(when (field) {
        MissionField.DESTINATION -> R.string.mission_destination
        MissionField.PRIORITY -> R.string.mission_priority
        MissionField.MEDICAL_QUANTITY -> R.string.mission_quantity
        MissionField.DESCRIPTION -> R.string.mission_note
        MissionField.CUSTODY_PATH -> R.string.mission_custody_path
    })
    LazyColumn(Modifier.fillMaxSize().testTag("field-missions"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(localized.getString(R.string.nav_missions), style = MaterialTheme.typography.headlineMedium)
            Text(localized.getString(R.string.mission_workspace_help), style = MaterialTheme.typography.bodyLarge)
        }
        if (failed) item { Text(localized.getString(R.string.mission_action_error), color = MaterialTheme.colorScheme.error) }
        if (missions.isEmpty()) item { Text(localized.getString(R.string.mission_empty), style = MaterialTheme.typography.titleLarge) }
        items(missions, key = { it.id }) { mission ->
            Card(Modifier.fillMaxWidth().testTag("mission-${mission.id}")) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${mission.priority.name} · ${mission.origin} → ${mission.destination}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(mission.id, style = MaterialTheme.typography.bodyMedium)
                    Text(localized.getString(if (mission.simulated) R.string.mission_simulated else R.string.mission_field_record))
                    if (mission.delivered) Text(localized.getString(R.string.mission_delivered), style = MaterialTheme.typography.titleMedium)
                    Text(localized.getString(R.string.mission_current_custodian, mission.custodian), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("mission-custodian-${mission.id}"))
                    Text("${localized.getString(R.string.mission_custody_path)}: ${mission.custodyPath.joinToString(" → ")}")
                    if (mission.custodyNeedsReconciliation) Text(localized.getString(R.string.mission_custody_reconcile), color = MaterialTheme.colorScheme.error)
                    if (mission.custodyNeedsReconciliation && mission.canResolve) {
                        OutlinedButton(onClick = { reconciling = mission; reconciliationReason = "" }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.mission_reconcile_action)) }
                    }
                    Text("${localized.getString(R.string.mission_quantity)}: ${mission.medicalQuantity}")
                    Text(localized.getString(R.string.mission_route_assumptions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mission.route?.let { "${it.edgeIds.joinToString(" → ")} · ${it.totalMinutes} ${localized.getString(R.string.mission_minutes)}" }
                        ?: localized.getString(R.string.mission_no_route), style = MaterialTheme.typography.titleMedium)
                    mission.triage?.let { Text(localized.getString(if (it.willBreachSla) R.string.mission_sla_warning else R.string.mission_sla_within)) }
                    if (mission.hash.isNotBlank()) Text(localized.getString(R.string.mission_hash, mission.hash.take(16)), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { model.recordPlan(mission.id) }, enabled = !busy && mission.canRecordPlan,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.mission_record_plan)) }
                    if (recordedPlan == mission.id) Text(localized.getString(R.string.mission_plan_recorded))
                    OutlinedButton(onClick = { model.selectMission(mission.id) }, enabled = !busy && selectedMission != mission.id,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(if (selectedMission == mission.id) R.string.mission_selected_custody else R.string.mission_select_custody)) }
                    OutlinedButton(onClick = { editing = mission; field = MissionField.DESTINATION; value = mission.destination }, enabled = !busy && mission.canEdit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.mission_edit)) }
                    mission.conflicts.forEach { conflict ->
                        Text(localized.getString(R.string.mission_conflict_help), color = MaterialTheme.colorScheme.error)
                        for ((side, selected) in listOf(ConflictSide.LEFT to conflict.leftValue, ConflictSide.RIGHT to conflict.rightValue)) {
                            OutlinedButton(onClick = { model.resolve(conflict.conflictId, side) }, enabled = !busy && mission.canResolve,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("${localized.getString(R.string.mission_resolve)}: $selected") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { mission ->
        AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text(localized.getString(R.string.mission_edit)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text(label(field)) }
                    DropdownMenu(menu, { menu = false }) { MissionField.entries.filter { it != MissionField.CUSTODY_PATH || mission.canAssign }.forEach { option ->
                        DropdownMenuItem(text = { Text(label(option)) }, onClick = { field = option; value = ""; menu = false })
                    } }
                }
                OutlinedTextField(value, { value = it }, label = { Text(label(field)) }, modifier = Modifier.fillMaxWidth())
                if (field == MissionField.CUSTODY_PATH) Text(localized.getString(R.string.mission_custody_path_help))
                Text(localized.getString(R.string.mission_edit_help))
            } }, confirmButton = { TextButton(onClick = { model.edit(mission.id, field, value); editing = null }, enabled = value.isNotBlank() && !busy && missions.any { it.id == mission.id && it.canEdit }) { Text(localized.getString(R.string.mission_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(localized.getString(android.R.string.cancel)) } })
    }
    reconciling?.let { mission ->
        AlertDialog(onDismissRequest = { if (!busy) reconciling = null },
            title = { Text(localized.getString(R.string.mission_reconcile_action)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localized.getString(R.string.mission_reconcile_help))
                mission.pendingCustodyChanges.forEach { (changedField, changedValue) ->
                    Text("${label(changedField)}: $changedValue", style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedTextField(reconciliationReason, { reconciliationReason = it.take(1000) },
                    label = { Text(localized.getString(R.string.mission_reconcile_reason)) }, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { model.reconcile(mission.id, reconciliationReason, mission.pendingCustodyChangeIds); reconciling = null },
                enabled = !busy && reconciliationReason.trim().length >= 8 && missions.any { it.id == mission.id && it.canResolve && it.custodyNeedsReconciliation }) {
                Text(localized.getString(R.string.mission_reconcile_confirm))
            } },
            dismissButton = { TextButton(onClick = { reconciling = null }) { Text(localized.getString(android.R.string.cancel)) } })
    }
}
