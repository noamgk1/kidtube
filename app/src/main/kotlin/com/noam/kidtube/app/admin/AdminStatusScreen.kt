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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.noam.kidtube.engine.EngineHealth

@Composable
fun AdminStatusScreen(
    view: StatusView,
    notice: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onResetEverything: () -> Unit,
    onChangePassword: (String) -> Unit,
    usingDefaultPassword: Boolean,
    translateTitles: Boolean,
    onTranslateTitlesChange: (Boolean) -> Unit,
    translatedCount: Int,
    /** False in the open-source build, which ships no translator. */
    translatorInstalled: Boolean,
    embeddedPlayer: Boolean,
    onEmbeddedPlayerChange: (Boolean) -> Unit,
    /** What the embedded player last settled on, e.g. "hd1080". Empty until it plays. */
    lastEmbeddedQuality: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        PasswordSection(
            usingDefaultPassword = usingDefaultPassword,
            onChangePassword = onChangePassword,
        )

        Text(
            text = "כותרות בעברית",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            "יוטיוב מחזיר כותרת בעברית רק אם יוצר הערוץ פרסם אחת. האפליקציה מבקשת " +
                "עברית, ולכן כל כותרת שיש לה תרגום מקורי תוצג בעברית.",
        )
        if (translatorInstalled) {
            Text(
                "לשאר הכותרות האפליקציה מתרגמת על המכשיר עצמו — שום דבר לא נשלח " +
                    "החוצה. התרגום אוטומטי, ולפעמים מגושם.",
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Switch(
                    checked = translateTitles,
                    onCheckedChange = onTranslateTitlesChange,
                    modifier = Modifier.testTag("translate-switch"),
                )
                Text(
                    text = if (translateTitles) {
                        // The count is the only honest progress signal: the model
                        // downloads over Wi-Fi only, and until it lands nothing is
                        // translated.
                        translatedCount.toString() + " כותרות תורגמו"
                    } else {
                        "כבוי — הכותרות יוצגו בשפת המקור"
                    },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else {
            // No switch rather than a dead one. The alternative was a toggle that
            // reports "0 translated" for ever, which reads as a broken sync.
            Text(
                "בגרסה הזו אין מתרגם מותקן, ולכן כותרת שהיוצר לא פרסם בעברית תוצג " +
                    "בשפת המקור.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            text = "איכות הווידאו",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            "יוטיוב מגביל תוכן המסומן \"לילדים\" ל-360p כשמחלצים ממנו את הווידאו — " +
                "נמדד, ואין איכות אחרת להציע. הנגן המוטמע של יוטיוב אינו כפוף לזה. " +
                "המחיר: הווידאו מצויר בנגן של יוטיוב בתוך האפליקציה. מגע בתוכו חסום, " +
                "אבל זו פחות בידוד מהנגן שלנו.",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Switch(
                checked = embeddedPlayer,
                onCheckedChange = onEmbeddedPlayerChange,
                modifier = Modifier.testTag("embedded-switch"),
            )
            Text(
                text = if (embeddedPlayer) {
                    "נגן יוטיוב מוטמע — ייכנס לתוקף ביציאה מהמסך הזה"
                } else {
                    "הנגן שלנו — תוכן לילדים יוגבל ל-360p"
                },
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (embeddedPlayer) {
            // The only honest answer to "did that help?". The whole reason this player
            // exists is a quality claim, and nothing else on screen can confirm it.
            Text(
                text = if (lastEmbeddedQuality.isBlank()) {
                    "האיכות שתנוגן תופיע כאן אחרי הסרטון הבא"
                } else {
                    "האיכות שנוגנה לאחרונה: " + lastEmbeddedQuality
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            text = "מצב המנוע",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 32.dp),
        )
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
        if (notice != null) {
            Text(notice, modifier = Modifier.padding(top = 8.dp))
        }
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = onExport) { Text("ייצא גיבוי") }
            Button(onClick = onImport, modifier = Modifier.padding(start = 12.dp)) {
                Text("ייבא גיבוי")
            }
        }

        Button(onClick = onResetEverything, modifier = Modifier.padding(top = 32.dp)) {
            Text("שכחתי סיסמה — אפס הכל, כולל הרשימה הלבנה וההיסטוריה")
        }
    }
}

/**
 * Changing the admin password.
 *
 * The app ships with a factory password rather than asking the first person who finds
 * the admin hold to choose one, so this is also the nudge to stop using it.
 */
@Composable
private fun PasswordSection(
    usingDefaultPassword: Boolean,
    onChangePassword: (String) -> Unit,
) {
    var chosen by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Text("סיסמת ההורים", style = MaterialTheme.typography.titleLarge)

    if (usingDefaultPassword) {
        Text(
            text = "הסיסמה היא עדיין סיסמת ברירת המחדל. כדאי לשנות אותה.",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Row(modifier = Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = chosen,
            onValueChange = { chosen = it; message = null },
            label = { Text("סיסמה חדשה") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.testTag("new-password"),
        )
        Button(
            onClick = {
                if (chosen.length < AdminAuthenticator.MIN_PASSWORD_LENGTH) {
                    message = "סיסמה קצרה מדי"
                } else {
                    onChangePassword(chosen)
                    chosen = ""
                    message = "הסיסמה שונתה"
                }
            },
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        ) {
            Text("שנה סיסמה")
        }
    }

    val note = message
    if (note != null) {
        Text(note, modifier = Modifier.padding(top = 8.dp))
    }
}
