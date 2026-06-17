package soft.shadlv.twp_rewritekts

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity

fun ComponentActivity.checkExit() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exitInfos = activityManager.getHistoricalProcessExitReasons(packageName, 0, 1)

        if (exitInfos.isNotEmpty()) {
            val lastExit = exitInfos[0]

            when (lastExit.reason) {
                ApplicationExitInfo.REASON_USER_REQUESTED -> {
                    // Дропнули сервис через Диспетчер задач
                    Log.d(".MainActivity", "REASON_USER_REQUESTED")
                }

                ApplicationExitInfo.REASON_USER_STOPPED -> {
                    Log.d(".MainActivity", "Force stop. REASON_USER_STOPPED")
                }

                ApplicationExitInfo.REASON_ANR -> {
                    Log.e(".MainActivity", "REASON_ANR")
                }

                ApplicationExitInfo.REASON_CRASH -> {
                    Log.e(".MainActivity", "REASON_CRASH")
                }

                ApplicationExitInfo.REASON_LOW_MEMORY -> {
                    Log.w(".MainActivity", "REASON_LOW_MEMORY")
                }

                ApplicationExitInfo.REASON_CRASH_NATIVE -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_EXIT_SELF -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_FREEZER -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_OTHER -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_PACKAGE_UPDATED -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_PERMISSION_CHANGE -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_SIGNALED -> {
                    TODO()
                }

                ApplicationExitInfo.REASON_UNKNOWN -> {
                    TODO()
                }
            }

            val description = lastExit.description
            Log.d(".MainActivity", "Доп. инфо: $description")
        }
    }
}