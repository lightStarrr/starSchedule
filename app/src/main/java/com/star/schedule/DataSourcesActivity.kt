package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.datasource.presentation.DataSourcesRoute
import com.star.schedule.feature.datasource.presentation.DataSourcesViewModel
import com.star.schedule.feature.datasource.presentation.DataSourcesViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge

class DataSourcesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        val repository = (application as ScheduleApplication).dataSourceDependencies.repository
        val viewModel = ViewModelProvider(
            this,
            DataSourcesViewModelFactory(repository),
        )[DataSourcesViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                DataSourcesRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                )
            }
        }
    }
}
