package com.noam.kidtube.app.kid

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.noam.kidtube.R
import com.noam.kidtube.core.BidiText

/**
 * The app's own bar: the mark on one side, the way into the parent screen on the other.
 *
 * The parent's door used to be an unmarked three-second hold in a corner, on the theory
 * that what a child cannot find, a child cannot press. It also turned out to be what a
 * parent cannot find. The password is the thing that actually guards this - a child who
 * presses the button meets it and gets no further - so the button is plainly visible and
 * the hold is gone.
 */
@Composable
fun KidTopBar(onOpenAdmin: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
            Text(
                // Latin inside a right-to-left bar: isolated so the layout places it as a
                // whole instead of reordering its edges.
                text = BidiText.isolate("KidTube"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        FilledTonalButton(
            onClick = onOpenAdmin,
            modifier = Modifier.testTag("parent-button"),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("הורים", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
