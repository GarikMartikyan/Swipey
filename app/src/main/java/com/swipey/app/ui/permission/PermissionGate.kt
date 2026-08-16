package com.swipey.app.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.swipey.app.domain.MediaAccess
import com.swipey.app.domain.resolveMediaAccess
import com.swipey.app.ui.common.Copy

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

fun currentMediaAccess(context: Context): MediaAccess = resolveMediaAccess(
    imagesGranted = granted(context, Manifest.permission.READ_MEDIA_IMAGES),
    videoGranted = granted(context, Manifest.permission.READ_MEDIA_VIDEO),
    userSelectedGranted = Build.VERSION.SDK_INT >= 34 &&
        granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
)

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
    var access by remember { mutableStateOf(currentMediaAccess(context)) }

    // Re-check on resume so returning from Settings updates the gate.
    LifecycleResumeEffect(Unit) {
        access = currentMediaAccess(context)
        onPauseOrDispose { }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { access = currentMediaAccess(context) }

    when (access) {
        MediaAccess.FULL -> content()
        MediaAccess.PARTIAL -> Message(
            title = Copy.PARTIAL_TITLE,
            body = Copy.PARTIAL_BODY,
            action = Copy.PARTIAL_ACTION,
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
        MediaAccess.DENIED -> Message(
            title = Copy.PERMISSION_TITLE,
            body = Copy.PERMISSION_BODY,
            action = Copy.PERMISSION_GRANT,
            onAction = { launcher.launch(requestedPermissions()) },
        )
    }
}

@Composable
private fun Message(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAction) { Text(action) }
    }
}
