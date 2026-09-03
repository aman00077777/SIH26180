package com.farmassist.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmassist.app.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProfileScreen(
    currentNumber: String,
    onNumberSaved: (String) -> Unit,
    // Virtual field mode props
    virtualFieldModeEnabled: Boolean = false,
    fieldNodeIp: String = "",
    onVirtualFieldModeToggled: (Boolean) -> Unit = {},
    onFieldNodeIpSaved: (String) -> Unit = {}
) {
    var phoneNumber by remember { mutableStateOf(currentNumber) }
    var saved by remember { mutableStateOf(currentNumber.isNotBlank()) }
    val smsPermission = rememberPermissionState(android.Manifest.permission.SEND_SMS)

    var ipAddress by remember { mutableStateOf(fieldNodeIp) }
    var ipSaved by remember { mutableStateOf(fieldNodeIp.isNotBlank()) }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_wheat_field),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).background(Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2E7D32))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Farmer Profile", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (saved) "Registered for SMS alerts" else "Not registered yet",
                        color = if (saved) Color(0xFFA5D6A7) else Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Phone / SMS card (unchanged) ─────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC142016)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = Color(0xFFF2A93B))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Phone number",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Used for SMS alerts — works even with zero data connection.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))

                    if (!smsPermission.status.isGranted) {
                        Button(onClick = { smsPermission.launchPermissionRequest() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Grant SMS permission")
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it; saved = false },
                        label = { Text("Your phone number") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF66BB6A),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedLabelColor = Color(0xFF66BB6A),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (smsPermission.status.isGranted) {
                                onNumberSaved(phoneNumber)
                                saved = true
                            } else {
                                smsPermission.launchPermissionRequest()
                            }
                        },
                        enabled = phoneNumber.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saved) "Saved ✓" else "Save")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Virtual field mode card (NEW) ────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC142016)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Router, contentDescription = null, tint = Color(0xFF64B5F6))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Virtual field mode",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Connect to the Virtual Hardware Agent running on your laptop. "
                                + "The phone and laptop must be on the same WiFi network for the "
                                + "camera image endpoint to work. BLE sensor data is separate — it "
                                + "works over Bluetooth independently.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable virtual field mode", color = Color.White)
                        Switch(
                            checked = virtualFieldModeEnabled,
                            onCheckedChange = onVirtualFieldModeToggled,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Color(0xFF66BB6A),
                                checkedThumbColor = Color.White
                            )
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it; ipSaved = false },
                        label = { Text("Laptop IP address") },
                        placeholder = { Text("e.g. 192.168.1.42", color = Color.White.copy(alpha = 0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF64B5F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedLabelColor = Color(0xFF64B5F6),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Find this by running 'ipconfig' on your laptop.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onFieldNodeIpSaved(ipAddress)
                            ipSaved = true
                        },
                        enabled = ipAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (ipSaved) "Saved ✓" else "Save IP")
                    }
                }
            }

            Spacer(Modifier.height(80.dp)) // bottom padding for nav bar
        }
    }
}