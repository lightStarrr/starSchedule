# GitHub Actions 构建与发布

仓库里有两条流水线，职责分开：**快照**跟提交走，**正式版**跟标签走。

| 工作流 | 触发 | 产物 | 发布位置 |
| --- | --- | --- | --- |
| `snapshot.yml` | 每次推送到 `master`（`*.md`、`docs/`、`mappings/` 改动除外）、手动触发 | 签名 APK + 压缩后的 mapping | Release 标签 `snapshot`，pre-release，每次重建 |
| `release.yml` | 推送 `v*` 标签 | 签名 APK + mapping.txt + SHA256SUMS | 对应标签的 Release，标记为 latest |

两条都构建 `release` 变体（开启 R8），不是 `debug`。理由是混淆和资源压缩引发的问题只在 release 变体上出现，快照如果是 debug 包，测出来的结果和用户装到的包不是同一份东西。代价是快照包也带混淆，得配着 mapping 一起发。

## 一、首次配置：签名密钥

四个 Secrets 放在 **Settings → Secrets and variables → Actions → Repository secrets**。

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | release keystore 文件的 base64，单行、无换行 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 的 store password |
| `ANDROID_KEY_ALIAS` | 签名条目别名 |
| `ANDROID_KEY_PASSWORD` | 该条目的 key password |

不要把密钥写进仓库的环境变量（Variables）——那是明文，任何能看仓库的人都能读到。Secrets 保存后不可再读，只能覆盖。

生成 base64（PowerShell，直接进剪贴板）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\path\to\release.jks")) | Set-Clipboard
```

已经装了 GitHub CLI 的话可以整条命令行设置，避免手工粘贴错行：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\path\to\release.jks")) > keystore.b64
gh secret set ANDROID_KEYSTORE_BASE64 < keystore.b64
Remove-Item keystore.b64
```

**必须是现在对外发布用的那把 release 密钥。** 换了密钥，已装用户无法覆盖安装，只能卸载重装；而且 `versionCode` 相同也不能绕过签名校验。

## 二、本地构建怎么用同一套配置

`app/build.gradle.kts` 按「`keystore.properties` → 环境变量」的顺序取签名信息，CI 用环境变量，本地用属性文件。在**仓库根目录**建 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=D:/keystore/release.jks
storePassword=***
keyAlias=***
keyPassword=***
```

`storeFile` 相对路径按仓库根目录解析，也可以写绝对路径。两者都没配时 `assembleRelease` 仍然产出 `app-release-unsigned.apk`，和以前的本地行为一致。

## 三、快照发布的工作方式

GitHub 上没有"覆盖式 Release"，所以流程是：删掉旧的 `snapshot` Release 和它指向的标签 → 用当前提交重建同名标签 → 上传新产物。对外只有一个固定地址：

```text
https://github.com/lightStarrr/starSchedule/releases/tag/snapshot
```

这样测试同学收藏一次链接就行，不用每提交一次换一个地址。`snapshot` 被标记为 pre-release 且不占 `latest`，正式版仍然在 `/releases/latest`。

同一分支上连续提交会取消前一次还在跑的任务（`concurrency.cancel-in-progress`），避免排队。

版本号策略：**快照沿用当前的 `versionCode`，只在 `versionName` 后面追加 `-snapshot.<短哈希>`**。

- 不递增 versionCode 是刻意的。如果快照用 `versionCode + 1000` 这类高位数值，之后发正式版（120 → 121）会因为 versionCode 比快照小被系统当成降级，测试机必须卸载才能装正式版。
- 相同 versionCode 允许覆盖安装，正式版 ↔ 快照可以互相覆盖。
- 区分版本靠应用内的 `BUILD_STAMP`（版本号 · 短哈希 · 打包时间）和 Release 上的构建标识，不靠 versionCode。

## 四、mapping 与崩溃还原

应用内的崩溃页会显示 `BUILD_STAMP`，形如 `1.2.0-snapshot.81bca3b (120) · 81bca3b · 2026-10-24 18:40`。

- 正式版：Release 附件里的 `mapping-<标签>.txt`，永久保留。
- 快照：Release 附件里的 `mapping-<buildId>.txt.gz`，`buildId` 就是 `build.gradle.kts` 里 `archiveMapping` 的命名（短哈希 + 构建时间），和应用内 `BUILD_ID` 同源；同时另存一份 90 天的 Actions artifact。

还原堆栈：

```bash
gunzip -k mapping-81bca3b-202610241840.txt.gz
retrace mapping-81bca3b-202610241840.txt stacktrace.txt
```

`retrace` 在 Android SDK 的 `cmdline-tools/<版本>/bin/` 下；也可以用 Android Studio 的 `Analyze → Analyze Stack Trace`，把 mapping 指定进去。

## 五、流水线里的校验点

`release.yml` 在发布前会检查，任何一条不满足就中止，不会发出一个"看起来正常"的包：

- `mapping.txt` 非空（R8 没开或没产出混淆映射时报错）
- APK 的 `versionName` 与标签一致（`v1.3.0` 必须对应 `versionName 1.3.0`），防止忘改版本号就发版
- 签名证书不是 `CN=Android Debug`

单元测试在构建前跑，`WakeUpShareSourceLiveTest` 这类需要真机或口令的用例在 CI 上会自动跳过。

## 六、常见问题

**`./gradlew: Permission denied`**：`gradlew` 在 git 里必须是 755。仓库里已经用 `git update-index --chmod=+x gradlew` 修过；如果重新导入过文件导致又变回 644，再执行一次。

**`配置 Android SDK` 步骤失败**：不要用 `android-actions/setup-android`。它内部会执行 `sdkmanager tools`，而新版 cmdline-tools 已经删掉这个包，会以退出码 1 结束整条流水线。runner 镜像本来就有 SDK 和已接受的许可证，工作流里只跑 `sdkmanager --licenses` 即可。

**编译找不到 `android-37` 平台**：新版 SDK 的平台目录带小版本号（本机是 `platforms/android-37.0`），缺失时由 AGP 按自己的包名规则自动下载，不需要手工 `sdkmanager`。runner 上平台包不在本地缓存时，第一次构建会多花一两分钟。

**快照间隔太密**：一次完整的 R8 + kapt 构建约几分钟。不想每次提交都出包，可以给提交信息加 `[skip ci]`，或者把 `paths-ignore` 放宽到只构建 `app/**` 的改动。

**想加 PR 构建**：复制 `snapshot.yml` 的构建部分，去掉发布相关步骤，`on` 换成 `pull_request`。PR 构建不产出 Release，避免测试版页面被无关提交覆盖。
