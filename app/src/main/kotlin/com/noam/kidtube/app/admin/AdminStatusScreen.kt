package com.noam.kidtube.app.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noam.kidtube.engine.EngineHealth

@Composable
fun AdminStatusScreen(
    view: StatusView,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetEverything: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("מצב המנוע", style = MaterialTheme.typography.titleLarge)
        when (val health = view.engineHealth) {
            is EngineHealth.Healthy -> Text("תקין")

            is EngineHealth.Degraded -> Text(
                text = "מוגבל — " + health.reason +
                    "\nהצפייה עובדת, אך תוכן לילדים עשוי לרדת ל-360p.",
                color = MaterialTheme.colorScheme.error,
            )

            is EngineHealth.Unavailable -> Text(
                text = "לא זמין — " + health.reason,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (view.incompleteSyncs.isNotEmpty()) {
            Text(
                text = "סנכרונים פתוחים",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            view.incompleteSyncs.forEach { state ->
                Card(modifier = Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(state.channelId)
                        Text(
                            state.pagesDone.toString() + " עמודים · " +
                                state.videosStored + " סרטונים",
                        )
                        val error = state.lastError
                        if (error != null) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (view.corruptions.isNotEmpty()) {
            Text(
                text = "קבצים פגומים",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            view.corruptions.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        Text(
            text = "גיבוי",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text("הגיבוי אינו כולל את הסיסמה, כדי שאיפוס ושחזור יחזירו אותך פנימה.")
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = onExport) { Text("ייצא גיבוי") }
            Button(onClick = onImport, modifier = Modifier.padding(start = 12.dp)) {
                Text("ייבא גיבוי")
            }
        }

        Button(onClick = onResetEverything, modifier = Modifier.padding(top = 32.dp)) {
            Text("אפס סיסמה — מוחק גם את הרשימה הלבנה ואת ההיסטוריה")
        }
    }
}
