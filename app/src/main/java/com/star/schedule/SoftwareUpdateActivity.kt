package com.star.schedule

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.update.presentation.SoftwareUpdateRoute
import com.star.schedule.feature.update.presentation.SoftwareUpdateViewModel
import com.star.schedule.feature.update.presentation.SoftwareUpdateViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge

class SoftwareUpdateActivity : ComponentActivity() {

    private lateinit var viewModel: SoftwareUpdateViewModel
    private lateinit var dependencies: com.star.schedule.app.UpdateDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        dependencies = (application as ScheduleApplication).updateDependencies
        viewModel = ViewModelProvider(
            this,
            SoftwareUpdateViewModelFactory(
                dependencies.repository,
                dependencies.downloadRepository,
                dependencies.installed,
            ),
        )[SoftwareUpdateViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                SoftwareUpdateRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenRelease = { release ->
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()),
                            )
                        } catch (_: Exception) {
                            viewModel.notifyOpenReleaseFailed()
                        }
                    },
                    onOpenInstallSettings = {
                        try {
                            startActivity(dependencies.installer.unknownSourcesIntent())
                        } catch (_: Exception) {
                            viewModel.notifyOpenSettingsFailed()
                        }
                    },
                    onStartInstall = { identity, localPath ->
                        val path = viewModel.consumeInstallRequest(identity, localPath)
                        if (path == null) {
                            viewModel.notifyInstallLaunchFailed(identity)
                        } else try {
                            startActivity(dependencies.installer.installIntent(path))
                            viewModel.notifyInstallLaunched(identity)
                        } catch (_: Exception) {
                            viewModel.notifyInstallLaunchFailed(identity)
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setPageActive(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInstallPermission(
            dependencies.installer.canRequestPackageInstalls(),
        )
    }

    override fun onStop() {
        super.onStop()
        viewModel.setPageActive(false)
    }
}
