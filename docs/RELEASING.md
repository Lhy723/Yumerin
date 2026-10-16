# Android 发布流程

Android 应用版本定义在 `androidApp/build.gradle.kts`。每次发布都要递增 `versionCode`，并将 `versionName` 与 `CHANGELOG.md` 中的版本保持一致。

## 工作流

- `.github/workflows/android-ci.yml` 在面向 `main` 的 Pull Request、推送到 `main` 和手动运行时，构建 Android Debug、JS 和 Wasm 目标。
- `.github/workflows/android-release.yml` 响应 `v*` 标签，检查标签版本与 `versionName` 一致，构建 arm64 Release APK、验证签名，并创建 GitHub Release。
- 每个版本的公告保存在 `docs/releases/v<version>.md`。使用 `What's Changed` 逐项说明具体改动并链接 issue/PR；有新贡献者时加入 `New Contributors`；最后提供面向用户的 `更新说明`。发布工作流直接使用该文件。
- 仓库必须包含 `LICENSE`，并配置下方 Actions secrets；任一项缺失时，发布工作流都会失败。

## 配置签名

所有公开 Release 必须持续使用同一签名密钥，否则已安装的 Release 版本无法直接升级。不要把密钥或密码提交到 Git。

`v1.1.0` 是首个公开 GitHub Release，并使用新生成的 Release 密钥。本地 `1.0` APK 由 Android Debug 密钥签名，因此 Android 不会将这次 Release 安装为它的原地更新；安装前需要卸载 Debug 包。后续 Release 必须继续使用此处配置的密钥。

当前 Release 证书 SHA-256 指纹：`3ae903003d670543b111da80097d860acaf84c041a976fe036aa733c9f23a6e9`。

本机的备份保存在仓库根目录 `.release-signing/`，该目录已被 Git 忽略。请将 keystore 和 `credentials.txt` 复制到受控备份位置；GitHub Actions secrets 不能读取回明文，丢失本地备份后无法恢复私钥。

在仓库 **Settings → Secrets and variables → Actions** 中添加以下 Repository secrets：

| Secret | 内容 |
| --- | --- |
| `YUMERIN_RELEASE_KEYSTORE_BASE64` | `.jks` / `.keystore` 文件的 Base64 内容 |
| `YUMERIN_RELEASE_KEYSTORE_PASSWORD` | Key store 密码 |
| `YUMERIN_RELEASE_KEY_ALIAS` | Key alias |
| `YUMERIN_RELEASE_KEY_PASSWORD` | Key 密码 |

工作流只在 runner 临时目录中还原密钥文件。Gradle 读取 `YUMERIN_RELEASE_STORE_FILE`、`YUMERIN_RELEASE_STORE_PASSWORD`、`YUMERIN_RELEASE_KEY_ALIAS` 和 `YUMERIN_RELEASE_KEY_PASSWORD` 环境变量；本地没有配置时，Debug 构建不受影响。

## 发布步骤

1. 更新版本号、`CHANGELOG.md` 和 `docs/releases/v<version>.md`，并确认 Actions 的 Android CI 构建成功。
2. 用与 `versionName` 完全相同的版本创建标签，例如 `v1.1.0`。
3. 将标签推送到 `origin`。Release 工作流会创建 GitHub Release 并附上签名的 arm64 APK。

仓库采用 MIT License，许可证全文见根目录 `LICENSE`。
