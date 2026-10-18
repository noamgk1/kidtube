package com.noam.kidtube.app.kid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noam.kidtube.core.DenyReason

/**
 * What a child sees when something breaks: no jargon, no error code, and exactly one way
 * out - back to the grid. The parent gets the real exception on the status screen. A
 * child who sees a broken screen goes looking for a way around it.
 */
@Composable
fun CalmErrorCard(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBack, modifier = Modifier.padding(top = 24.dp)) {
            Text("חזרה", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Child-facing wording for every refusal. Never shows a reason code. */
fun calmMessageFor(outcome: PlaybackOutcome): String = when (outcome) {
    is PlaybackOutcome.Started -> ""
    is PlaybackOutcome.Unavailable -> "הסרטון הזה כבר לא קיים. אפשר לבחור אחר."
    is PlaybackOutcome.Failed -> "לא הצלחנו להפעיל את הסרטון. אפשר לנסות אחר."
    is PlaybackOutcome.Refused -> when (outcome.reason) {
        DenyReason.BUDGET_EXHAUSTED -> "נגמר זמן הצפייה להיום."
        else -> "הסרטון הזה לא זמין כרגע."
    }
}
