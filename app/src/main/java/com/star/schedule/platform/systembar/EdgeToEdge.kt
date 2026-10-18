package com.star.schedule.platform.systembar

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * 所有页面的 edge-to-edge 统一入口：系统栏全透明，内容画到栏下面。
 *
 * 之前每个 Activity 各写一遍 `enableEdgeToEdge()` + `installAdaptiveStatusBarAppearance()`，
 * 参数容易走偏；有的机型还会因为导航栏对比度强制而在栏后盖一层灰，
 * 这里一并关掉，保证首页与二级页的观感一致。
 */
internal fun ComponentActivity.installEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
        ),
        navigationBarStyle = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // 不关的话，三键导航的机型会在透明导航栏后面再叠一层半透明遮罩。
        window.isNavigationBarContrastEnforced = false
    }
    installAdaptiveStatusBarAppearance()
}
