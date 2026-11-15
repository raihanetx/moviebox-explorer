package com.example.movieboxexplorer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shown once, before the catalogue is browsed.
 *
 * The catalogue carries adult titles inside ordinary rows and search results. The
 * only reliable marker — `isPornographic` — is reported on a title's *detail* record
 * and never in a listing, so the app cannot tell in advance what a row will open.
 * The gate therefore informs once, and the per-title decision is made afterwards.
 */
@Composable
fun AdultGateDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* the user has to make a choice */ },
        title = { Text("Adult content") },
        text = {
            Column {
                Text(
                    text = "This catalogue contains adult titles. They are mixed into the " +
                        "ordinary rows and search results, and some of them are listed " +
                        "with a rating you cannot see until the title is opened.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "You can turn adult titles off again in Settings at any time.",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("I am 18+ — continue") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Leave") }
        },
    )
}

/**
 * Shown when an adult title is opened while adult content is switched off.
 *
 * The title is not blocked outright — the user may switch the setting on and retry —
 * because the flag is only known once the detail record has been read.
 */
@Composable
fun AdultTitleBlockedDialog(
    title: String,
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adult title") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "“$title” is flagged as adult content.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "It stays hidden unless adult content is enabled in Settings.",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text("Enable adult content") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Go back") }
        },
    )
}
