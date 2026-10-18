package com.star.schedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.crash.presentation.CrashScreen
import com.star.schedule.platform.crash.CrashStore
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 崩溃页。跑在独立的 `:crash` 进程，主进程崩掉也不会把它一起带走。
 */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        val report = CrashStore.loadLatest(this)

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                val context = LocalContext.current
                CrashScreen(
                    report = report,
                    onCopy = { copyToClipboard(context, report?.asShareText().orEmpty()) },
                    onRestart = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                        finishAndKillProcess()
                    },
                    onClose = ::finishAndKillProcess,
                )
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("crash", text))
        Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    }

    /** 关掉崩溃页，并把 `:crash` 进程一并结束。 */
    private fun finishAndKillProcess() {
        finish()
        Process.killProcess(Process.myPid())
    }
}
