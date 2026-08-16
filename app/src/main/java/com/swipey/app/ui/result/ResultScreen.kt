package com.swipey.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swipey.app.data.RecoveryReport
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.DisclosureNote
import com.swipey.app.ui.common.DisclosureSheet
import com.swipey.app.ui.common.resultDisclosures
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The measure the outcome is set in; wider than this and a centred line stops scanning. */
private val Measure = 420.dp

@Composable
fun ResultScreen(
    report: RecoveryReport,
    earliestExpirySec: Long?,
    onHome: () -> Unit,
    onBin: () -> Unit,
) {
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    val moved = report.confirmedTrashed.size

    SwipeyScreen(
        overlay = {
            DisclosureSheet(
                visible = notesOpen,
                onDismiss = { notesOpen = false },
                notes = resultDisclosures(),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = SwipeySpacing.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The count, then what happened to it. Spoken as one sentence, because
            // Copy.resultTitle is the audited wording of exactly this and hearing
            // "twelve — twelve items moved to trash" is worse than either half.
            Column(
                Modifier.clearAndSetSemantics { contentDescription = Copy.resultTitle(moved) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SwipeyText(
                    moved.toString(),
                    style = SwipeyTheme.typography.displayNumeric,
                    color = SwipeyTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                SwipeyText(
                    Copy.RESULT_OUTCOME_LINE,
                    modifier = Modifier.padding(top = SwipeySpacing.xs),
                    style = SwipeyTheme.typography.body,
                    color = SwipeyTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            earliestExpirySec?.let {
                val date = Instant.ofEpochSecond(it)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM"))
                Outcome(Copy.expiresAtLeast(date), top = SwipeySpacing.lg)
            }

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
                val attempted = moved + notTrashed + report.vanished.size
                Outcome(Copy.cancelled(moved, attempted), top = SwipeySpacing.sm)
            }
            if (report.vanished.isNotEmpty()) {
                Outcome(Copy.vanishedNotice(report.vanished.size), top = SwipeySpacing.sm)
            }

            Spacer(Modifier.height(SwipeySpacing.md))
            // Rules 2, 4 and 7 in one line, in full behind the ⓘ. F8(a): rule 7 belongs
            // here above all — this is the screen where someone who just binned things is
            // likeliest to ask "for good?".
            DisclosureNote(
                text = Copy.TRASH_SHORT_NOTE,
                onOpen = { notesOpen = true },
                modifier = Modifier.widthIn(max = Measure),
            )

            Spacer(Modifier.height(SwipeySpacing.xl))
            Row(
                Modifier.widthIn(max = Measure).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.md),
            ) {
                // F8(c): RESULT_VIEW_BIN, not BIN_TITLE — "Bin" alone reads as the verb here.
                SwipeyButton(
                    text = Copy.RESULT_VIEW_BIN,
                    onClick = onBin,
                    modifier = Modifier.weight(1f),
                    variant = SwipeyButtonVariant.Ghost,
                )
                // F8(b): was a hardcoded "Done" string; now lives in Copy.kt like everything else.
                SwipeyButton(
                    text = Copy.RESULT_DONE,
                    onClick = onHome,
                    modifier = Modifier.weight(1f),
                    variant = SwipeyButtonVariant.Ghost,
                )
            }
        }
    }
}

/** A centred supporting line under the outcome. */
@Composable
private fun Outcome(text: String, top: Dp) {
    SwipeyText(
        text,
        modifier = Modifier.widthIn(max = Measure).padding(top = top),
        style = SwipeyTheme.typography.bodyNumeric,
        color = SwipeyTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}
