package com.example.digitaldelta.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.digitaldelta.R
import com.example.digitaldelta.domain.observer.ObserverSettings
import com.example.digitaldelta.domain.observer.ObserverConfiguration
import com.example.digitaldelta.service.ObserverPublication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ObserverSettingsCard(language: String, localNodeId: String) {
    val context = LocalContext.current
    val localized = remember(context, language) { context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }) }
    val settings = remember(context) { ObserverSettings(context) }
    var configured by remember { mutableStateOf(settings.configured()) }
    // Never save the credential to instance-state bundles or expose it as status text.
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth().testTag("observer-settings")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localized.getString(R.string.observer_optional_title), style = MaterialTheme.typography.titleLarge)
            Text(localized.getString(R.string.observer_privacy_help), style = MaterialTheme.typography.bodyLarge)
            Text(localized.getString(if (configured) R.string.observer_enabled else R.string.observer_disabled))
            OutlinedTextField(code, { code = it; error = false }, label = { Text(localized.getString(R.string.observer_configuration)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
                isError = error, modifier = Modifier.fillMaxWidth().testTag("observer-configuration"))
            if (error) Text(localized.getString(R.string.observer_configuration_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                busy = true
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { runCatching {
                        // Reject a different node's token before storing; source authority is rechecked at publication.
                        val allowDebug = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
                        require(ObserverConfiguration.parse(code, allowDebug).sourceNodeId == localNodeId)
                        settings.save(code)
                        ObserverPublication.schedule(context)
                    }.isSuccess }
                    error = !saved; configured = settings.configured(); busy = false
                    if (saved) code = ""
                }
            }, enabled = code.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(localized.getString(R.string.observer_enable))
            }
            if (configured) OutlinedButton(onClick = {
                busy = true
                scope.launch {
                    val disabled = withContext(Dispatchers.IO) { runCatching { settings.disable() }.isSuccess }
                    configured = settings.configured(); error = !disabled; busy = false
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(localized.getString(R.string.observer_disable)) }
        }
    }
}
