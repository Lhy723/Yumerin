# Android 发布流程

Android 应用版本定义在 `androidApp/build.gradle.kts`。每次发布都要递增 `versionCode`，并将 `versionName` 与 `CHANGELOG.md` 中的版本保持一致。

## 工作流

- `.github/workflows/android-ci.yml` 在面向 `main` 的 Pull Request、推送到 `main` 和手动运行时，构建 Android Debug、JS 和 Wasm 目标。
- `.github/workflows/android-release.yml` 响应 `v*` 标签，检查标签版本与 `versionName` 一致，构建 arm64 Release APK、验证签名，并创建 GitHub Release。
- 仓库需要已有的 `LICENSE` 文件和现有签名密钥的 Actions secrets；任一项缺失时，发布工作流都会失败。

## 配置签名

Release 必须使用这个应用既有的签名密钥，保证已安装版本可以升级。不要把密钥或密码提交到 Git，也不要为发布流程随意生成替代密钥；更换签名身份会使现有安装无法直接升级。

在仓库 **Settings → Secrets and variables → Actions** 中添加以下 Repository secrets：

| Secret | 内容 |
| --- | --- |
| `YUMERIN_RELEASE_KEYSTORE_BASE64` | 既有 `.jks` / `.keystore` 文件的 Base64 内容 |
| `YUMERIN_RELEASE_KEYSTORE_PASSWORD` | Key store 密码 |
| `YUMERIN_RELEASE_KEY_ALIAS` | Key alias |
| `YUMERIN_RELEASE_KEY_PASSWORD` | Key 密码 |

工作流只在 runner 临时目录中还原密钥文件。Gradle 读取 `YUMERIN_RELEASE_STORE_FILE`、`YUMERIN_RELEASE_STORE_PASSWORD`、`YUMERIN_RELEASE_KEY_ALIAS` 和 `YUMERIN_RELEASE_KEY_PASSWORD` 环境变量；本地没有配置时，Debug 构建不受影响。

## 发布步骤

1. 更新版本号和 `CHANGELOG.md`，并确认 Actions 的 Android CI 构建成功。
2. 用与 `versionName` 完全相同的版本创建标签，例如 `v1.1.0`。
3. 将标签推送到 `origin`。Release 工作流会创建 GitHub Release 并附上签名的 arm64 APK。

当前仓库尚未添加项目级许可证。公开 GitHub Release 前，请由项目所有者确定并添加许可证；不要由发布工作流替项目选择许可证。
