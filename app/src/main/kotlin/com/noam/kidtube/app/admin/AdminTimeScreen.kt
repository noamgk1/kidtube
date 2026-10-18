package com.noam.kidtube.app.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdminTimeScreen(
    view: TimeView,
    onLimitChange: (Int) -> Unit,
    onGrantBonus: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "מגבלה יומית: " + view.dailyLimitMinutes + " דקות",
            style = MaterialTheme.typography.titleLarge,
        )
        Slider(
            value = view.dailyLimitMinutes.toFloat(),
            onValueChange = { onLimitChange(it.toInt()) },
            valueRange = 0f..180f,
            steps = 35,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        Text("נצרך היום: " + (view.consumedSeconds / 60) + " דקות")
        Text(
            text = "נותרו: " + (view.remainingSeconds / 60) + " דקות",
            style = MaterialTheme.typography.titleMedium,
        )

        Row(modifier = Modifier.padding(top = 24.dp)) {
            listOf(10, 20, 30).forEach { minutes ->
                Button(
                    onClick = { onGrantBonus(minutes) },
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Text("+" + minutes + " דקות להיום")
                }
            }
        }
    }
}
