package com.bitchat.android.model

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Emergency contact – mirrors iOS SafeRelay EmergencyContact struct.
 */
data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var relation: String = "",
    var phone: String = ""
)

/**
 * User profile with personal, health and emergency contact data.
 * Mirrors iOS SafeRelay UserProfile struct field-for-field.
 */
data class UserProfile(
    // Personal
    var fullName: String = "",
    var age: String = "",
    var gender: String = "",
    var phone: String = "",
    var address: String = "",
    var city: String = "",
    var state: String = "",
    var pincode: String = "",

    // Health
    var bloodGroup: String = "",
    var allergies: String = "",
    var medications: String = "",
    var medicalConditions: String = "",
    var disabilities: String = "",
    var organDonor: Boolean = false,

    // Family / Emergency Contacts
    var emergencyContacts: List<EmergencyContact> = emptyList(),
    var doctorName: String = "",
    var doctorPhone: String = ""
) {
    /** True if the minimum required fields are filled */
    val isComplete: Boolean
        get() = fullName.isNotBlank() && bloodGroup.isNotBlank() && emergencyContacts.isNotEmpty()

    /** Display initials (up to 2 chars) for the avatar */
    val initials: String
        get() {
            if (fullName.isBlank()) return "?"
            val parts = fullName.trim().split(" ")
            return parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("")
        }
}

/**
 * Singleton profile manager – mirrors iOS SafeRelay UserProfileManager.
 * Persists via SharedPreferences (JSON).
 */
class UserProfileManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("saferelay_profile", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val saveKey = "saferelay.userProfile"

    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    init {
        load()
    }

    fun update(newProfile: UserProfile) {
        _profile.value = newProfile
        save()
    }

    private fun save() {
        prefs.edit().putString(saveKey, gson.toJson(_profile.value)).apply()
    }

    private fun load() {
        val json = prefs.getString(saveKey, null) ?: return
        try {
            _profile.value = gson.fromJson(json, UserProfile::class.java)
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile private var INSTANCE: UserProfileManager? = null

        fun getInstance(context: Context): UserProfileManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserProfileManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
