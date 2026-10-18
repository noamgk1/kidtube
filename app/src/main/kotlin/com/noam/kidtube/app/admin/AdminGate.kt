package com.noam.kidtube.app.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AdminGate(
    authenticator: AdminAuthenticator,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val firstRun = remember { !authenticator.isPasswordSet() }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (firstRun) "בחר סיסמה לממשק ההורים" else "ממשק הורים",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("סיסמה") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.width(320.dp).padding(top = 24.dp).testTag("admin-password"),
        )

        val message = error
        if (message != null) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = {
                if (firstRun) {
                    if (password.length < 4) {
                        error = "סיסמה קצרה מדי"
                    } else {
                        authenticator.setPassword(password.toCharArray())
                        onUnlocked()
                    }
                } else if (authenticator.authenticate(password.toCharArray())) {
                    onUnlocked()
                } else {
                    error = "סיסמה שגויה"
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(if (firstRun) "שמור" else "כניסה")
        }

        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text("ביטול")
        }
    }
}
