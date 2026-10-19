package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The end of the day's allowance. Deliberately dull: nothing here entertains, and the
 * only way off it is the parent's button, which still needs the password.
 */
@Composable
fun LockScreen(onOpenAdmin: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "נגמר זמן הצפייה להיום",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "נתראה מחר",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // A parent who wants to grant extra minutes needs a way in from here, and the
        // password is what stops the child using it.
        KidTopBar(
            onOpenAdmin = onOpenAdmin,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
