package com.example.digitaldelta.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var editing by remember { mutableStateOf<FieldMission?>(null) }
    var field by remember { mutableStateOf(MissionField.DESTINATION) }
    var value by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    fun label(field: MissionField): String = localized.getString(when (field) {
        MissionField.DESTINATION -> R.string.mission_destination
        MissionField.PRIORITY -> R.string.mission_priority
        MissionField.MEDICAL_QUANTITY -> R.string.mission_quantity
        MissionField.DESCRIPTION -> R.string.mission_note
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
                    Text("${localized.getString(R.string.mission_quantity)}: ${mission.medicalQuantity}")
                    Text(localized.getString(R.string.mission_route_assumptions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mission.route?.let { "${it.edgeIds.joinToString(" → ")} · ${it.totalMinutes} ${localized.getString(R.string.mission_minutes)}" }
                        ?: localized.getString(R.string.mission_no_route), style = MaterialTheme.typography.titleMedium)
                    mission.triage?.let { Text(localized.getString(if (it.willBreachSla) R.string.mission_sla_warning else R.string.mission_sla_within)) }
                    if (mission.hash.isNotBlank()) Text(localized.getString(R.string.mission_hash, mission.hash.take(16)), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { editing = mission; field = MissionField.DESTINATION; value = mission.destination }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.mission_edit)) }
                    mission.conflicts.forEach { conflict ->
                        Text(localized.getString(R.string.mission_conflict_help), color = MaterialTheme.colorScheme.error)
                        for ((side, selected) in listOf(ConflictSide.LEFT to conflict.leftValue, ConflictSide.RIGHT to conflict.rightValue)) {
                            OutlinedButton(onClick = { model.resolve(conflict.conflictId, side) }, enabled = !busy,
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
                    DropdownMenu(menu, { menu = false }) { MissionField.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(label(option)) }, onClick = { field = option; value = ""; menu = false })
                    } }
                }
                OutlinedTextField(value, { value = it }, label = { Text(label(field)) }, modifier = Modifier.fillMaxWidth())
                Text(localized.getString(R.string.mission_edit_help))
            } }, confirmButton = { TextButton(onClick = { model.edit(mission.id, field, value); editing = null }, enabled = value.isNotBlank() && !busy) { Text(localized.getString(R.string.mission_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(localized.getString(android.R.string.cancel)) } })
    }
}
