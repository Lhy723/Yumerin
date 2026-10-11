# Yumerin 项目交接

更新日期：2026-10-11

## 项目概况

Yumerin 是一款本地优先的 AI 情感陪伴应用。Android 是当前完整产品目标；JS/Wasm 版本提供界面与交互预览，数据保存在内存中。产品术语、角色关系、记忆规则和安全边界以根目录的 [`CONTEXT.md`](../CONTEXT.md) 为准。

## 模块与边界

| 模块 | 职责 |
| --- | --- |
| `androidApp/` | Android 启动入口、应用级配置和平台资源 |
| `shared/src/commonMain/` | Compose Multiplatform 界面、框架无关领域模型、用例、Repository contracts、内存实现 |
| `shared/src/androidMain/` | Room/SQLCipher、本地凭据、LiteRT-LM、网络适配、WorkManager、通知和平台文件访问 |
| `webApp/` | 浏览器 JS/Wasm 预览入口；复用 shared UI，以内存实现支撑预览 |

依赖方向保持在领域边界内：UI 触发 Use cases，Use cases 调用 Repository contracts，Android 数据实现承接 Room、平台存储与运行时。领域模型不依赖 Android、Room、HTTP DTO 或 UI 类型。Android 对象图由 Koin 组装。

版本以 `gradle/libs.versions.toml` 为准：Kotlin 2.4.10、Compose Multiplatform 1.11.1、Android Gradle Plugin 9.3.0、Room 2.8.4、SQLCipher 4.17.0 和 LiteRT-LM 0.16.1。Gradle Wrapper 当前为 9.7.0，Android/Kotlin 字节码目标为 JVM 11。

## 主要能力

- 角色资料、关系设定、归档/恢复和永久删除
- 私聊与多角色群聊；消息先持久化，再进入投递流程
- 角色隔离的长期记忆，支持来源追踪、审核、固定、编辑和删除
- OpenAI-compatible 用户自带模型配置，以及 LiteRT-LM 本地模型导入/下载/激活
- 有边界的主动消息与后台陪伴；静默时段只抑制通知
- 本地数据包导出、导入预览和冲突策略选择

详细产品约束见 [`CONTEXT.md`](../CONTEXT.md)，关键架构取舍见 [`adr/`](adr/) 下的 19 份决策记录。

## 开发环境与命令

项目使用 Gradle Wrapper。建议使用 Android Studio 自带 JDK，并安装 Android SDK Platform 37。首次构建需要网络访问 Google Maven、Maven Central 和 Gradle Plugin Portal。

在项目根目录创建本机 `local.properties`，设置 Android SDK 路径；该文件已排除在 Git 外：

```properties
sdk.dir=C:/Users/<用户名>/AppData/Local/Android/Sdk
```

Windows PowerShell：

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:testAndroidHostTest
.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
```

macOS / Linux：

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

Debug APK：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`。Web 也可运行 `:webApp:jsBrowserDevelopmentRun`。

## 数据与安全约束

- Android 使用 Room 持久化产品数据，并以 SQLCipher 加密数据库；模型凭据由 Android Keystore 保护。
- 应用备份默认关闭。可携带数据包不包括 API Key 或大型模型权重。
- 归档是可逆状态；永久删除和清除全部本地数据都必须清楚说明影响范围并显式确认。
- 外部搜索、跨平台同步和外部平台发信不是 V1 的完整功能。不得将占位接口描述成已接通能力，也不得未经用户明确操作发送外部消息。
- 不把 `local.properties`、签名文件、环境文件或本地模型权重加入版本控制。`.gitignore` 已包含对应规则。

## 当前实现状态

- Android Debug APK 最近一次 `:androidApp:assembleDebug` 构建成功。
- 主要 UI 正在使用 Compose、Material 3、Miuix 和 Backdrop 组件；聊天列表/详情、液态导航、角色列表/编辑、个人资料和设置页已有 HyperOS/Liquid Glass 方向的整理。
- 当前任务未运行自动化测试；需要发布前运行上面的 host tests，并对 Android 设备权限、后台限制和数据迁移做设备验证。
- Web/Wasm 仍是内存预览，不是持久化 Web 产品。
- 仓库尚无项目级 `LICENSE` 文件。公开分发或允许第三方复用前，项目所有者需要确定许可证并加入文件。

## 发布前检查

1. 确定仓库可见性和项目许可证。
2. 验证数据库迁移、导入冲突、数据清除、Keystore 凭据与后台权限流程。
3. 配置独立的 Release 签名；签名密钥、凭据和本地模型文件不得提交。
4. 根据目标市场补齐隐私说明、年龄确认说明和发布渠道配置。

## 交接入口

- 快速开始与功能概览：[`README.md`](../README.md)
- 产品与领域上下文：[`CONTEXT.md`](../CONTEXT.md)
- 架构决策索引：[`docs/README.md`](README.md)
- 动效实现计划：[`plans/README.md`](../plans/README.md)
