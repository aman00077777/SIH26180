package com.farmassist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.farmassist.app.ui.components.pressScale

@Composable
fun AcreageInputScreen(onBack: () -> Unit, onSubmit: (Double) -> Unit, onSkip: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val acres = text.toDoubleOrNull()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                .pressScale(onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        Spacer(Modifier.weight(1f))

        Text("How much land is this for?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "We'll use this to size the treatment dose to your field.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Land size (acres)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { acres?.let(onSubmit) },
            enabled = acres != null && acres > 0.0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan leaf")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }

        Spacer(Modifier.weight(1f))
    }
}
