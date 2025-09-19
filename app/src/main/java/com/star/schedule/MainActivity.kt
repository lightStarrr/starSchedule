package com.star.schedule

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.star.schedule.ui.layouts.DateRange
import com.star.schedule.ui.layouts.Settings
import com.star.schedule.ui.theme.StarScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StarScheduleTheme {
                Layout(content = this)
            }
        }
    }
}

@Composable
fun Layout(content: Activity) {
    var selectedItem by remember { mutableIntStateOf(0) }

    data class ItemData(
        val name: String,
        val icon: ImageVector
    )

    val items = listOf(
        ItemData("日程", Icons.Rounded.DateRange),
        ItemData("设置", Icons.Rounded.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.name
                            )
                        },
                        label = { Text(item.name) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedItem,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                if (targetState > initialState) {
                    // 从右往左切
                    (slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { it / 5 }
                    ) + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { -it / 5 }
                            ) + fadeOut(tween(300)))
                } else {
                    // 从左往右切
                    (slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { -it / 5 }
                    ) + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { it / 5 }
                            ) + fadeOut(tween(300)))
                }
            }
            ,
                    label = "pageAnimation"
        ) { page ->
            when (page) {
                0 -> DateRange(content)
                1 -> Settings(content)
            }
        }
    }
}
