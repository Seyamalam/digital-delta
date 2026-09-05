package com.example.digitaldelta.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitaldelta.R
import com.example.digitaldelta.domain.sync.*
import com.example.digitaldelta.domain.fleet.*
import com.example.digitaldelta.domain.routing.VehicleType
import com.example.digitaldelta.domain.triage.CargoPriority
import java.util.Locale

/** Room-backed missions are separate from the labelled scenario controls. */
@Composable
fun MissionWorkspace(language: String, model: MissionWorkspaceViewModel = viewModel()) {
    val missions by model.missions.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val failed by model.failed.collectAsStateWithLifecycle()
    val selectedMission by model.selectedMission.collectAsStateWithLifecycle()
    val recordedPlan by model.recordedPlan.collectAsStateWithLifecycle()
    MissionWorkspaceContent(language, missions, busy, failed, selectedMission, recordedPlan,
        MissionWorkspaceActions(model::recordPlan, model::selectMission, model::edit, model::resolve,
            model::reconcile, model::dispatch, model::hold))
}

data class MissionWorkspaceActions(
    val recordPlan: (String) -> Unit,
    val selectMission: (String) -> Unit,
    val edit: (String, MissionField, String) -> Unit,
    val resolve: (String, ConflictSide) -> Unit,
    val reconcile: (String, String, Set<String>) -> Unit,
    val dispatch: (DispatchCommand) -> Unit,
    val hold: (FieldMission) -> Unit,
)

/** The production presentation, isolated from credential and Room ownership for UI checks. */
@Composable
fun MissionWorkspaceContent(language: String, missions: List<FieldMission>, busy: Boolean, failed: Boolean,
    selectedMission: String?, recordedPlan: String?, actions: MissionWorkspaceActions) {
    val context = LocalContext.current
    val localized = remember(context, language) { context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }) }
    var editing by remember { mutableStateOf<FieldMission?>(null) }
    var field by remember { mutableStateOf(MissionField.DESTINATION) }
    var value by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reconciling by remember { mutableStateOf<FieldMission?>(null) }
    var reconciliationReason by remember { mutableStateOf("") }
    var dispatching by remember { mutableStateOf<FieldMission?>(null) }
    var holding by remember { mutableStateOf<FieldMission?>(null) }
    var operator by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf(VehicleType.TRUCK) }
    var heldMission by remember { mutableStateOf<FieldMission?>(null) }
    var dispatchChecked by remember { mutableStateOf(false) }
    fun label(field: MissionField): String = localized.getString(when (field) {
        MissionField.DESTINATION -> R.string.mission_destination
        MissionField.PRIORITY -> R.string.mission_priority
        MissionField.MEDICAL_QUANTITY -> R.string.mission_quantity
        MissionField.DESCRIPTION -> R.string.mission_note
        MissionField.CUSTODY_PATH -> R.string.mission_custody_path
        MissionField.DISPATCH -> R.string.mission_dispatch
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
                    mission.dispatch?.let { reservation ->
                        Text(localized.getString(R.string.mission_dispatch_summary,
                            localized.getString(if (reservation.state == DispatchReservation.READY) R.string.mission_dispatch_ready else R.string.mission_dispatch_hold),
                            reservation.operatorNodeId, localized.getString(if (reservation.vehicle == VehicleType.TRUCK) R.string.mission_dispatch_truck else R.string.mission_dispatch_boat)), modifier = Modifier.testTag("mission-dispatch-${mission.id}"))
                        reservation.preemptedByMissionId?.let { Text(localized.getString(R.string.mission_dispatch_preempted_by, it)) }
                    }
                    if (mission.dispatchCollision) Text(localized.getString(R.string.mission_dispatch_collision), color = MaterialTheme.colorScheme.error)
                    if (mission.canAssign) {
                        OutlinedButton(onClick = {
                            dispatching = mission; operator = mission.dispatch?.operatorNodeId ?: mission.eligibleOperators.firstOrNull().orEmpty()
                            vehicle = mission.dispatch?.vehicle ?: VehicleType.TRUCK; heldMission = null; dispatchChecked = false
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("dispatch-plan-${mission.id}")) {
                            Text(localized.getString(R.string.mission_dispatch_action))
                        }
                        if (mission.dispatch?.state == DispatchReservation.READY) OutlinedButton(onClick = { holding = mission }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.mission_dispatch_hold_action)) }
                    }
                    if (mission.custodyNeedsReconciliation) Text(localized.getString(R.string.mission_custody_reconcile), color = MaterialTheme.colorScheme.error)
                    if (mission.custodyNeedsReconciliation && mission.canResolve) {
                        OutlinedButton(onClick = { reconciling = mission; reconciliationReason = "" }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("reconcile-${mission.id}")) { Text(localized.getString(R.string.mission_reconcile_action)) }
                    }
                    Text("${localized.getString(R.string.mission_quantity)}: ${mission.medicalQuantity}")
                    Text(localized.getString(R.string.mission_route_assumptions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mission.route?.let { "${it.edgeIds.joinToString(" → ")} · ${it.totalMinutes} ${localized.getString(R.string.mission_minutes)}" }
                        ?: localized.getString(R.string.mission_no_route), style = MaterialTheme.typography.titleMedium)
                    mission.triage?.let { Text(localized.getString(if (it.willBreachSla) R.string.mission_sla_warning else R.string.mission_sla_within)) }
                    if (mission.hash.isNotBlank()) Text(localized.getString(R.string.mission_hash, mission.hash.take(16)), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { actions.recordPlan(mission.id) }, enabled = !busy && mission.canRecordPlan,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.mission_record_plan)) }
                    if (recordedPlan == mission.id) Text(localized.getString(R.string.mission_plan_recorded))
                    OutlinedButton(onClick = { actions.selectMission(mission.id) }, enabled = !busy && selectedMission != mission.id,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(if (selectedMission == mission.id) R.string.mission_selected_custody else R.string.mission_select_custody)) }
                    OutlinedButton(onClick = { editing = mission; field = MissionField.DESTINATION; value = mission.destination }, enabled = !busy && mission.canEdit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("edit-${mission.id}")) { Text(localized.getString(R.string.mission_edit)) }
                    mission.conflicts.forEach { conflict ->
                        Text(localized.getString(R.string.mission_conflict_help), color = MaterialTheme.colorScheme.error)
                        for ((side, selected) in listOf(ConflictSide.LEFT to conflict.leftValue, ConflictSide.RIGHT to conflict.rightValue)) {
                            OutlinedButton(onClick = { actions.resolve(conflict.conflictId, side) }, enabled = !busy && mission.canResolve,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("${localized.getString(R.string.mission_resolve)}: $selected") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { mission ->
        AlertDialog(onDismissRequest = { if (!busy) editing = null }, title = { Text(localized.getString(R.string.mission_edit)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.testTag("mission-field-menu")) { Text(label(field)) }
                    DropdownMenu(menu, { menu = false }) { MissionField.entries.filter { it != MissionField.DISPATCH && (it != MissionField.CUSTODY_PATH || (mission.canAssign && mission.dispatch == null)) }.forEach { option ->
                        DropdownMenuItem(text = { Text(label(option)) }, onClick = { field = option; value = ""; menu = false }, modifier = Modifier.testTag("mission-field-${option.name}"))
                    } }
                }
                OutlinedTextField(value, { value = it }, label = { Text(label(field)) }, modifier = Modifier.fillMaxWidth().testTag("mission-field-value"))
                if (field == MissionField.CUSTODY_PATH) Text(localized.getString(R.string.mission_custody_path_help))
                Text(localized.getString(R.string.mission_edit_help))
            } }, confirmButton = { TextButton(onClick = { actions.edit(mission.id, field, value); editing = null }, modifier = Modifier.testTag("mission-field-save"), enabled = value.isNotBlank() && !busy && missions.any { it.id == mission.id && it.canEdit }) { Text(localized.getString(R.string.mission_save)) } },
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
                    label = { Text(localized.getString(R.string.mission_reconcile_reason)) }, modifier = Modifier.fillMaxWidth().testTag("reconcile-reason"))
            } },
            confirmButton = { TextButton(onClick = { actions.reconcile(mission.id, reconciliationReason, mission.pendingCustodyChangeIds); reconciling = null }, modifier = Modifier.testTag("reconcile-confirm"),
                enabled = !busy && reconciliationReason.trim().length >= 8 && missions.any { it.id == mission.id && it.canResolve && it.custodyNeedsReconciliation }) {
                Text(localized.getString(R.string.mission_reconcile_confirm))
            } },
            dismissButton = { TextButton(onClick = { reconciling = null }) { Text(localized.getString(android.R.string.cancel)) } })
    }
    dispatching?.let { mission ->
        AlertDialog(onDismissRequest = { if (!busy) dispatching = null }, title = { Text(localized.getString(R.string.mission_dispatch_action)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${mission.priority.name} · ${mission.origin} → ${mission.destination}")
                Text(localized.getString(R.string.mission_dispatch_help))
                if (mission.eligibleOperators.isEmpty()) Text(localized.getString(R.string.mission_dispatch_no_operator))
                mission.eligibleOperators.forEach { node ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(operator == node, role = Role.RadioButton,
                        onClick = { operator = node; heldMission = null; dispatchChecked = false }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = operator == node, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(node)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(VehicleType.TRUCK, VehicleType.BOAT).forEach { mode ->
                        FilterChip(selected = vehicle == mode, onClick = { vehicle = mode; heldMission = null; dispatchChecked = false },
                            label = { Text(localized.getString(if (mode == VehicleType.TRUCK) R.string.mission_dispatch_truck else R.string.mission_dispatch_boat)) })
                    }
                }
                if (mission.priority in setOf(CargoPriority.P0, CargoPriority.P1)) {
                    val candidates = missions.filter { other -> other.id != mission.id && other.canAssign && other.origin == mission.origin && other.readerNodes == mission.readerNodes && other.simulated == mission.simulated &&
                        other.priority in setOf(CargoPriority.P2, CargoPriority.P3) && other.dispatch?.state == DispatchReservation.READY &&
                        other.dispatch.operatorNodeId == operator && other.dispatch.vehicle == vehicle }
                    if (candidates.isNotEmpty()) Text(localized.getString(R.string.mission_dispatch_preempt_help))
                    candidates.forEach { candidate ->
                        OutlinedButton(onClick = { heldMission = if (heldMission?.id == candidate.id) null else candidate; dispatchChecked = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("${if (heldMission?.id == candidate.id) "✓ " else ""}${candidate.priority.name} · ${candidate.id}")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("dispatch-reviewed").toggleable(dispatchChecked, role = Role.Checkbox,
                    onValueChange = { dispatchChecked = it }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(dispatchChecked, null, modifier = Modifier.padding(12.dp))
                    Text(localized.getString(R.string.mission_dispatch_checked), modifier = Modifier.weight(1f))
                }
            } },
            confirmButton = { TextButton(onClick = {
                actions.dispatch(DispatchCommand(mission.id, operator, vehicle, mission.dispatchReviewIds, heldMission?.id, heldMission?.dispatchReviewIds.orEmpty()))
                dispatching = null
            }, enabled = !busy && dispatchChecked && operator in mission.eligibleOperators && missions.any { it.id == mission.id && it.canAssign },
                modifier = Modifier.testTag("dispatch-confirm")) { Text(localized.getString(R.string.mission_dispatch_confirm)) } },
            dismissButton = { TextButton(onClick = { dispatching = null }) { Text(localized.getString(android.R.string.cancel)) } })
    }
    holding?.let { mission ->
        AlertDialog(onDismissRequest = { holding = null }, title = { Text(localized.getString(R.string.mission_dispatch_hold_action)) },
            text = { Text(localized.getString(R.string.mission_dispatch_hold_help)) },
            confirmButton = { TextButton(onClick = { actions.hold(mission); holding = null }, enabled = !busy) { Text(localized.getString(R.string.mission_dispatch_confirm)) } },
            dismissButton = { TextButton(onClick = { holding = null }) { Text(localized.getString(android.R.string.cancel)) } })
    }
}
