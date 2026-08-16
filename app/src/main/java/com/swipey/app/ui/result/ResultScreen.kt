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

        // Spec §9 rule 6 — honest per-item reporting, never blanket success.
        if (report.declined.isNotEmpty()) {
            Text(
                Copy.cancelled(report.confirmedTrashed.size, report.confirmedTrashed.size + report.declined.size),
                Modifier.padding(top = 8.dp),
            )
        }
        if (report.vanished.isNotEmpty()) {
            Text(Copy.vanishedNotice(report.vanished.size), Modifier.padding(top = 8.dp))
        }

        Button(onClick = onBin, modifier = Modifier.padding(top = 24.dp)) { Text(Copy.BIN_TITLE) }
        TextButton(onClick = onHome) { Text("Done") }
    }
}
