package com.example.digitaldelta.domain.pod

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** Local navigation preference only; it never grants authority over a mission. */
@Singleton
class MissionSelection @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("mission-selection", Context.MODE_PRIVATE)
    val missionId = MutableStateFlow(preferences.getString("mission-id", null))
    fun select(id: String) {
        require(id.isNotBlank())
        preferences.edit().putString("mission-id", id).apply()
        missionId.value = id
    }
}
