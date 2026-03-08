package com.saferelay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saferelay.android.model.EmergencyContact
import com.saferelay.android.model.UserProfile
import com.saferelay.android.model.UserProfileManager




/**
 * UserProfileSheet – mirrors iOS SafeRelay UserProfileView.
 * Fields: Personal, Health, Emergency Contacts.
 */
@Composable
fun UserProfileSheet(
    profileManager: UserProfileManager,
    onDismiss: () -> Unit
) {
    val currentProfile by profileManager.profile.collectAsState()
    var draft by remember { mutableStateOf(currentProfile) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F5F5)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = SOSRed)
                    }
                    Text(
                        "Edit Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        profileManager.update(draft)
                        onDismiss()
                    }) {
                        Text("Save", color = SOSRed, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = SOSRed.copy(alpha = 0.3f), thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── Personal ──────────────────────────────────────────
                    ProfileSection(title = "👤 Personal Information") {
                        ProfileField("Full Name", draft.fullName) { draft = draft.copy(fullName = it) }
                        ProfileField("Age", draft.age, KeyboardType.Number) { draft = draft.copy(age = it) }
                        ProfileField("Gender", draft.gender) { draft = draft.copy(gender = it) }
                        ProfileField("Phone", draft.phone, KeyboardType.Phone) { draft = draft.copy(phone = it) }
                        ProfileField("Address", draft.address) { draft = draft.copy(address = it) }
                        ProfileField("City", draft.city) { draft = draft.copy(city = it) }
                        ProfileField("State", draft.state) { draft = draft.copy(state = it) }
                        ProfileField("Pincode", draft.pincode, KeyboardType.Number) { draft = draft.copy(pincode = it) }
                    }

                    // ── Health ────────────────────────────────────────────
                    ProfileSection(title = "🏥 Health Information") {
                        ProfileField("Blood Group", draft.bloodGroup) { draft = draft.copy(bloodGroup = it) }
                        ProfileField("Allergies", draft.allergies) { draft = draft.copy(allergies = it) }
                        ProfileField("Medications", draft.medications) { draft = draft.copy(medications = it) }
                        ProfileField("Medical Conditions", draft.medicalConditions) { draft = draft.copy(medicalConditions = it) }
                        ProfileField("Disabilities", draft.disabilities) { draft = draft.copy(disabilities = it) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Organ Donor", fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                                color = Color(0xFFCCCCCC), modifier = Modifier.weight(1f))
                            Switch(
                                checked = draft.organDonor,
                                onCheckedChange = { draft = draft.copy(organDonor = it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SOSRed,
                                    checkedTrackColor = SOSRed.copy(alpha = 0.4f))
                            )
                        }
                    }

                    // ── Emergency Contacts ────────────────────────────────
                    ProfileSection(title = "🚨 Emergency Contacts") {
                        val contacts = draft.emergencyContacts.toMutableList()
                        contacts.forEachIndexed { idx, contact ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProfileField("Name", contact.name) {
                                        contacts[idx] = contacts[idx].copy(name = it)
                                        draft = draft.copy(emergencyContacts = contacts.toList())
                                    }
                                    ProfileField("Relation", contact.relation) {
                                        contacts[idx] = contacts[idx].copy(relation = it)
                                        draft = draft.copy(emergencyContacts = contacts.toList())
                                    }
                                    ProfileField("Phone", contact.phone, KeyboardType.Phone) {
                                        contacts[idx] = contacts[idx].copy(phone = it)
                                        draft = draft.copy(emergencyContacts = contacts.toList())
                                    }
                                    TextButton(
                                        onClick = {
                                            val updated = contacts.toMutableList().apply { removeAt(idx) }
                                            draft = draft.copy(emergencyContacts = updated)
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = SOSRed)
                                    ) {
                                        Text("Remove", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Add contact button
                        OutlinedButton(
                            onClick = {
                                val updated = draft.emergencyContacts + EmergencyContact()
                                draft = draft.copy(emergencyContacts = updated)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SOSRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SOSRed.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = SOSRed,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Emergency Contact", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }

                    // ── Doctor ────────────────────────────────────────────
                    ProfileSection(title = "👨‍⚕️ Doctor Info") {
                        ProfileField("Doctor Name", draft.doctorName) { draft = draft.copy(doctorName = it) }
                        ProfileField("Doctor Phone", draft.doctorPhone, KeyboardType.Phone) { draft = draft.copy(doctorPhone = it) }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp, color = Color.Black
        ),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedBorderColor = SOSRed,
            unfocusedLabelColor = Color.Gray,
            focusedLabelColor = SOSRed,
            cursorColor = SOSRed,
            unfocusedContainerColor = Color(0xFFFAFAFA),
            focusedContainerColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = label != "Address" && label != "Medical Conditions"
    )
}
