package com.swipey.app.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.swipey.app.domain.MediaAccess
import com.swipey.app.domain.resolveMediaAccess
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun currentMediaAccess(context: Context): MediaAccess = resolveMediaAccess(
    imagesGranted = granted(context, Manifest.permission.READ_MEDIA_IMAGES),
    videoGranted = granted(context, Manifest.permission.READ_MEDIA_VIDEO),
    userSelectedGranted = Build.VERSION.SDK_INT >= 34 &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
)

/** Standard "find the hosting Activity from a possibly-wrapped Context" walk. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

private fun requestedPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 34) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    }

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var access by remember { mutableStateOf(currentMediaAccess(context)) }
    // Fix round 2, Important 4: survives rotation while the gate is showing — needed
    // because `shouldShowRequestPermissionRationale` alone cannot distinguish "never
    // asked" from "permanently denied" (both return false); this flag disambiguates by
    // recording that at least one request cycle has actually completed.
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    // Re-check on resume so returning from Settings updates the gate.
    LifecycleResumeEffect(Unit) {
        access = currentMediaAccess(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasRequested = true
        access = currentMediaAccess(context)
    }

    // Android's own signal that a further launcher.launch() would be a silent no-op:
    // the user has been through at least one request, access is still DENIED, and the
    // system will no longer show a rationale for any of the requested permissions —
    // either "don't ask again" was checked, or the OS's own two-strike auto-deny
    // kicked in. Without this branch the DENIED state above was an inescapable front
    // door: its only action (re-launching the request) does nothing, and Back is the
    // sole way out.
    val permanentlyDenied = access == MediaAccess.DENIED && hasRequested &&
        activity != null &&
        requestedPermissions().none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    when {
        access == MediaAccess.FULL -> content()
        access == MediaAccess.PARTIAL -> Message(
            title = Copy.PARTIAL_TITLE,
            body = Copy.PARTIAL_BODY,
            action = Copy.PARTIAL_ACTION,
            onAction = { openAppSettings(context) },
        )
        permanentlyDenied -> Message(
            title = Copy.DENIED_TITLE,
            body = Copy.DENIED_BODY,
            action = Copy.DENIED_ACTION,
            onAction = { openAppSettings(context) },
        )
        else -> Message(
            title = Copy.PERMISSION_TITLE,
            body = Copy.PERMISSION_BODY,
            action = Copy.PERMISSION_GRANT,
            onAction = { launcher.launch(requestedPermissions()) },
        )
    }
}

/**
 * All three gate states share this shape: centred, one heading, one paragraph, one
 * filled action, and nothing else at all.
 *
 * The wording is [Copy]'s, verbatim — the partial-access text in particular is making a
 * genuine promise about what Swipey cannot guarantee, so it is typeset to be read rather
 * than shortened. Generous space and a measure capped at 420dp do that work; on a tablet
 * an uncapped line would run to 90-odd characters and stop being a sentence anyone reads.
 */
@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) {
    SwipeyScreen {
        Column(
            Modifier
                .fillMaxSize()
                // The gate is the one screen a user can meet at a 200% font scale before
                // they have granted anything; it must never trap its own button offscreen.
                .verticalScroll(rememberScrollState())
                .padding(vertical = SwipeySpacing.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SwipeyText(
                title,
                modifier = Modifier.widthIn(max = 420.dp),
                style = SwipeyTheme.typography.title,
                color = SwipeyTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwipeySpacing.md))
            SwipeyText(
                body,
                modifier = Modifier.widthIn(max = 420.dp),
                style = SwipeyTheme.typography.body,
                color = SwipeyTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SwipeySpacing.xxl))
            SwipeyButton(
                text = action,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
        }
    }
}
