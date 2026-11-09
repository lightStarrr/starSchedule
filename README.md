<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./background.svg">
  <img width="100%" src="./docs/readme/header-light.svg" alt="Star Schedule">
</picture>

<h3 align="center">一个美观的 Material 3 Expressive 风格课表</h3>

<p align="center">
  <img src="https://api.visitorbadge.io/api/visitors?path=https://github.com/lightStarrr/starSchedule&style=flat">
  <img src="https://img.shields.io/github/release/lightStarrr/starSchedule">
  <img src="https://img.shields.io/github/license/lightStarrr/starSchedule.svg">
  <img src="https://img.shields.io/badge/Android-13%2B-3DDC84?style=flat&logo=android&logoColor=white">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white">
</p>

> [!NOTE]
> Star Schedule 仍处于测试阶段，界面和功能会持续调整，欢迎通过 Issue 反馈问题与建议。

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/title-features-dark.svg">
  <img height="56" src="./docs/readme/title-features-light.svg" alt="功能特性">
</picture>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-timetable-dark.svg">
    <img width="49%" src="./docs/readme/feature-timetable-light.svg" alt="课表一览：周视图课表，字号随列宽自适应；多课表切换，行高随心调整">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-import-dark.svg">
    <img width="49%" src="./docs/readme/feature-import-light.svg" alt="多种导入：WakeUp 分享口令一键导入；也可从空白课表手动创建">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-ai-dark.svg">
    <img width="49%" src="./docs/readme/feature-ai-light.svg" alt="AI 导入（开发中）：截图、照片或文字识别课表；内置浏览器打开教务页面">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-lesson-dark.svg">
    <img width="49%" src="./docs/readme/feature-lesson-light.svg" alt="节次时间：自由增删每一节的上下课时间；保存为模板，新课表一键套用">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-reminder-dark.svg">
    <img width="49%" src="./docs/readme/feature-reminder-light.svg" alt="课前提醒：上课前准时发送通知；适配 Flyme 灵动通知">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-system-dark.svg">
    <img width="49%" src="./docs/readme/feature-system-light.svg" alt="系统集成：两日课程桌面小组件；ColorOS 可接入系统课程表能力">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-wallpaper-dark.svg">
    <img width="49%" src="./docs/readme/feature-wallpaper-light.svg" alt="壁纸取色：自定义课表背景壁纸；从壁纸提取主题色，界面随之变化">
  </picture>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/feature-update-dark.svg">
    <img width="49%" src="./docs/readme/feature-update-light.svg" alt="假期与更新：法定节假日与调休自动同步；应用内检查、下载并安装更新">
  </picture>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/title-screenshots-dark.svg">
  <img height="56" src="./docs/readme/title-screenshots-light.svg" alt="截图预览">
</picture>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/screenshots-dark.svg">
    <img width="100%" src="./docs/readme/screenshots-light.svg" alt="截图预览（待补充）">
  </picture>
</p>

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/title-download-dark.svg">
  <img height="56" src="./docs/readme/title-download-light.svg" alt="下载安装">
</picture>

<p>
  <a href="https://github.com/lightStarrr/starSchedule/releases/latest"><picture><source media="(prefers-color-scheme: dark)" srcset="./docs/readme/button-release-dark.svg"><img height="52" src="./docs/readme/button-release-light.svg" alt="下载正式版"></picture></a>
  &nbsp;
  <a href="https://github.com/lightStarrr/starSchedule/releases/tag/snapshot"><picture><source media="(prefers-color-scheme: dark)" srcset="./docs/readme/button-snapshot-dark.svg"><img height="52" src="./docs/readme/button-snapshot-light.svg" alt="体验快照版"></picture></a>
</p>

- **系统要求**：Android 13（API 33）及以上。
- **正式版**：推送 `v*` 标签后自动构建，附带签名 APK、校验和与更新日志。
- **快照版**：跟随主分支最新提交自动构建，可以抢先体验新功能，但可能不稳定。
- 安装后可在「设置 → 软件更新」中检查更新，打开「也检测快照更新」即可接收快照版。
- ColorOS 用户如需接入系统课程表能力，需要额外安装 `wakeup-proxy` 代理应用。

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/title-build-dark.svg">
  <img height="56" src="./docs/readme/title-build-light.svg" alt="构建与贡献">
</picture>

项目使用 Kotlin + Jetpack Compose（Material 3 Expressive）开发，数据层基于 Room。构建需要 JDK 17 及以上（CI 使用 JDK 21），推荐直接使用 Android Studio 自带的 JBR。

```powershell
git clone https://github.com/lightStarrr/starSchedule.git
cd starSchedule

.\gradlew :app:assembleDebug        # 构建调试包
.\gradlew :app:testDebugUnitTest    # 运行单元测试
```

| 模块 | 说明 |
| --- | --- |
| `app` | 主应用 |
| `wakeup-proxy` | ColorOS 系统课程表能力的代理应用 |

欢迎提交 Issue 和 Pull Request。开始之前请先阅读：

- [`AGENTS.md`](./AGENTS.md)：项目架构、代码风格与提交规范
- [`docs/ci.md`](./docs/ci.md)：自动构建与发布流程

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/readme/title-license-dark.svg">
  <img height="56" src="./docs/readme/title-license-light.svg" alt="开源许可">
</picture>

本项目基于 [Apache License 2.0](./LICENSE) 开源。
