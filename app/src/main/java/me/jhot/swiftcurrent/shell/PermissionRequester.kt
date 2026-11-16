package me.jhot.swiftcurrent.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** Asks the system for one runtime permission; the answer comes back through the callback given when it was made. */
interface PermissionRequester {
    fun request(permission: String)

    /** False after the owner chose not to be asked again, when [request] would return a denial with no dialog. */
    fun shouldShowRationale(permission: String): Boolean
}

/** Makes a requester that reports each answer as (permission, granted); a seam so tests need no system dialog. */
typealias PermissionRequesterFactory = @Composable (onResult: (String, Boolean) -> Unit) -> PermissionRequester

@Composable
fun rememberActivityPermissionRequester(onResult: (String, Boolean) -> Unit): PermissionRequester {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    // The contract carries no permission name back, so the one in flight is remembered here.
    val pending = remember { arrayOfNulls<String>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.let { latest(it, granted) }
    }
    return remember(launcher, context) {
        object : PermissionRequester {
            override fun request(permission: String) {
                pending[0] = permission
                launcher.launch(permission)
            }

            override fun shouldShowRationale(permission: String) =
                context.findActivity()?.shouldShowRequestPermissionRationale(permission) ?: false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
