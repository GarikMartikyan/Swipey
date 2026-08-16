package com.swipey.app.ui.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.data.RecoveryReport
import com.swipey.app.ui.common.Copy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ResultScreen(
    report: RecoveryReport,
    earliestExpirySec: Long?,
    onHome: () -> Unit,
    onBin: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(Copy.resultTitle(report.confirmedTrashed.size), style = MaterialTheme.typography.headlineSmall)

        earliestExpirySec?.let {
            val date = Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM"))
            Text(Copy.expiresAtLeast(date), Modifier.padding(top = 8.dp))
        }

        Text(Copy.TRASH_SIZE_NOTE, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)
        // F8(a): rule 7 was present on Review and Bin but absent here — exactly the
        // screen where a user who just binned things is likeliest to ask "for good?".
        Text(Copy.NO_PERMANENT_DELETE_NOTE, style = MaterialTheme.typography.bodySmall)

        // Spec §9 rule 6 — honest per-item reporting, never blanket success.
        //
        // Whole-branch review, I2: `awaiting` counts here alongside `declined`. Both mean
        // "asked for, not in the trash"; the grace window only changes whether the row was
        // deleted yet, not what the user needs to be told. Without this, the one case that
        // most needs the line — a chunked commit where the user approved the first dialog
        // and cancelled the second, so the un-approved chunk is still inside its window —
        // would silently lose "Stopped after 500 of 600" and read as a clean success.
        val notTrashed = report.declined.size + report.awaiting.size
        if (notTrashed > 0) {
            // F6: the denominator must be every id this commit attempted, not just
            // confirmed + declined — otherwise items that vanished mid-commit quietly
            // shrink "of M" below the actual request size.
            val attempted = report.confirmedTrashed.size + notTrashed + report.vanished.size
            Text(
                Copy.cancelled(report.confirmedTrashed.size, attempted),
                Modifier.padding(top = 8.dp),
            )
        }
        if (report.vanished.isNotEmpty()) {
            Text(Copy.vanishedNotice(report.vanished.size), Modifier.padding(top = 8.dp))
        }

        // F8(c): RESULT_VIEW_BIN, not BIN_TITLE — "Bin" alone reads as the verb here.
        Button(onClick = onBin, modifier = Modifier.padding(top = 24.dp)) { Text(Copy.RESULT_VIEW_BIN) }
        // F8(b): was a hardcoded "Done" string; now lives in Copy.kt like everything else.
        TextButton(onClick = onHome) { Text(Copy.RESULT_DONE) }
    }
}
