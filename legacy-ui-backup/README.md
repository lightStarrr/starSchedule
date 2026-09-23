# 旧版 UI 备份

本目录保存新 UI 开始建设前的旧版 Compose 界面源码，不参与 Android 源集编译。

备份基线提交：`ec5adcb`（解除数据库对通知实现的依赖）。

## 备份范围

- 旧 `MainActivity.kt` 及其主页、周次选择、页面切换和更新提示。
- 旧 `WebActivity.kt`。
- 原 `ui/components`、`ui/layouts`、`ui/theme` 和 `ui/viewmodel` 全部源码。

## 已从活动工程移除的旧 UI 配置

- Manifest 中的 `WebActivity` 注册。
- `MainActivity` 的旧版配置变更接管声明。
- Material Icons Extended。
- Lifecycle ViewModel Compose。
- WheelPicker Compose。
- Compose Color Picker。

恢复旧 UI 时，应以基线提交 `ec5adcb` 为准同时恢复源码、Manifest、`app/build.gradle.kts` 和 `gradle/libs.versions.toml`，避免只复制 Kotlin 文件导致依赖不完整。

`app/src/main/res` 中的字符串和其他资源没有整体移入本目录，因为其中大量内容仍被数据库初始化、业务校验、通知和 Widget 共用。基线提交保留了这些资源的完整快照。
