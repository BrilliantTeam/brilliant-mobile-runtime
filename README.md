# 輝煌啟動器 Android 執行層

這是 [輝煌啟動器](https://www.brilliantw.net/launcher/) Android 版使用的遊戲執行層，改作自 [Angel Aura Amethyst](https://github.com/AngelAuraMC/Amethyst-Android)，依 LGPL-3.0 公開修改後的原始碼。輝煌啟動器本身（Tauri 介面）是另外的閉源程式，這個 repo 只提供 Minecraft 的執行環境。

## 與 Amethyst 的主要差異

- 新增 `brilliant_runtime` 模組，打包成 AAR 給輝煌啟動器呼叫：由啟動器指定版本、遊戲資料夾、帳號與直連分流後啟動遊戲，並可在 App 內執行 Forge／NeoForge 官方安裝程式
- 觸控按鍵改為圖示樣式，新增「快捷動作」二級選單、長按觸發、獨立的隱藏按鍵開關，以及有情境分頁與明確儲存的按鍵編輯器
- 遊戲內選單、確認視窗與按鍵面板改用輝煌啟動器的配色與圖示
- 攔截 SDL3 的 `SDL_OpenURL`，讓 Minecraft 26.x 的「打開資料夾」與聊天網址能在 Android 開啟
- 系統「檔案」App 可瀏覽實例資料夾（只公開實例，不含登入資料）
- 資料位置可由啟動器指定；LWJGL 3.4.1 的 SDL 併入合併模組，並略過遊戲自帶的 LWJGL，讓 Forge 的模組系統能正常啟動
- 修正多指觸控與切到背景時滑鼠鍵卡住的問題

## 建置

需要 JDK 21（Gradle）與 JDK 8（部分元件），以及 Android NDK 27.3.13750724：

```
./gradlew :brilliant_runtime:assembleRelease :androidnsbypass:assembleRelease -Porg.gradle.java.installations.paths=<JDK 8 路徑>
```

產出 `brilliant_runtime/build/outputs/aar/brilliant_runtime-release.aar`。以下為 Amethyst 原本的說明。

---

<h1 align="center">Angel Aura Amethyst</h1>

<img src="https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/assets/amethyst.png" align="left" width="130" height="130" alt="Amethyst logo">

[![Android CI](https://github.com/AngelAuraMC/Amethyst-Android/workflows/Android%20CI/badge.svg)](https://github.com/AngelAuraMC/Amethyst-Android/actions)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/AngelAuraMC/Amethyst-Android)](https://github.com/AngelAuraMC/Amethyst-Android/actions)
[![Crowdin](https://badges.crowdin.net/pojavlauncher/localized.svg)](https://crowdin.com/project/pojavlauncher)
[![Discord](https://img.shields.io/discord/724163890803638273.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/5ptqkyZxEy)

*From [Boardwalk](https://github.com/zhuowei/Boardwalk)'s ashes and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)'s ruined reputation, here comes Amethyst!*

Amethyst is a launcher that allows you to play Minecraft: Java Edition on your Android and [iOS](https://github.com/AngelAuraMC/Amethyst-iOS) devices.

For more details, check out our [wiki](https://wiki.angelauramc.dev)!

## Table of Contents

* [Introduction](#introduction)
* [Getting Amethyst](#getting-amethyst)
* [Building](#building)
    * [Quick Build (Recommended)](#quick-build-recommended)
    * [Detailed Build](#detailed-build)
* [Current Status](#current-status)
* [Known Issues](#known-issues)
* [FAQ](#faq)
* [Contributing](#contributing)
* [Support](#support)
* [License](#license)
* [Credits & Dependencies](#credits--dependencies)
* [Roadmap](#roadmap)

## Introduction

* Amethyst is a Minecraft: Java Edition launcher for Android and iOS based on [Boardwalk](https://github.com/zhuowei/Boardwalk) and [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
* This launcher can launch almost all available Minecraft versions ranging from rd-132211 to 26.3-snapshot-1.
* Modding via Forge and Fabric are also supported.
* This repository contains source code for Android. For iOS/iPadOS, check out [Amethyst-iOS](https://github.com/AngelAuraMC/Amethyst-iOS).

## Getting Amethyst

You can get Amethyst via two methods:

1. **Releases:** Download the latest prebuilt app from [nightly.link](https://nightly.link/AngelAuraMC/Amethyst-Android/workflows/android/v3_openjdk/app-debug%20%28recommended%29.zip) or select an older version from our [automatic builds](https://github.com/AngelAuraMC/Amethyst-Android/actions).
2. **Build from Source:** Follow the [building instructions](#building) below.

## Building

### Pre-requisites

<details>

<summary>Preparing your build environment</summary>

1. ⚠️ Because of an upstream [issue](https://github.com/android/ndk/issues/1400#issuecomment-744620270) with ndk-build, do not clone the repository into any directory whose absolute path contains spaces. Doing so on will result in a build failure.

2. You must have a JDK installation, [Temurin](https://adoptium.net/temurin/releases) or [OpenJDK](https://openjdk.org/install/) are recommended, and a `JAVA_HOME` system environment variable configured for compiling your builds

</details>

### Quick Build (Recommended)

The easiest way to build Amethyst is to use the pre-built JREs provided by our CI.

1. Clone the repository: `git clone --recursive https://github.com/AngelAuraMC/Amethyst-Android.git`
2. Build the launcher: `./gradlew :app_pojavlauncher:assembleDebug` (Use `gradlew.bat` on Windows)

The built APK will be located in `app_pojavlauncher/build/outputs/apk/debug/`.

### Detailed Build

If you need more control over the build process, follow these steps:

1. **Java Runtime Environment (JRE):** Download the `jre8-pojav` artifact from our [CI auto builds](https://github.com/AngelAuraMC/openjdk-build-multiarch/actions), unzip and paste the contents of the downloaded zip into `app_pojavlauncher/src/main/assets/components/jre/`, create the folder if it does not exist.  This package contains pre-built JREs for all supported architectures.  If you need to build the JRE yourself, follow the instructions in the [android-openjdk-build-multiarch](https://github.com/AngelAuraMC/openjdk-build-multiarch) repository.

2. **LWJGL:** The build instructions for the custom LWJGL are available over the [LWJGL repository](https://github.com/AngelAuraMC/lwjgl3).

3. **Language List:** Because languages are auto-added by Crowdin, you need to run the language list generator before building. In the project directory, run:
   * Linux/macOS:
     ```bash
     chmod +x scripts/languagelist_updater.sh
     bash scripts/languagelist_updater.sh
     ```
   * Windows:
     ```batch
     scripts\languagelist_updater.bat
     ```

4. **Build GLFW stub:** `./gradlew :jre_lwjgl3glfw:build`

5. **Build the launcher:** `./gradlew :app_pojavlauncher:assembleDebug` (Replace `gradlew` with `gradlew.bat` on Windows).

## Current Status

* [x] OpenJDK 8 Mobile port: ARM32, ARM64, x86, x86_64
* [x] OpenJDK 17 Mobile port: ARM32, ARM64, x86, x86_64
* [x] OpenJDK 21 Mobile port: ARM32, ARM64, x86, x86_64
* [x] Headless mod installer
* [x] Mod installer with GUI
* [x] OpenGL in OpenJDK environment
* [x] OpenAL (works on most devices)
* [x] Support for Minecraft 1.12.2 and below
* [x] Support for Minecraft 1.13 and above
* [x] Support for Minecraft 1.17 (22w13a) and above
* [x] Game surface zooming
* [x] New input pipe rewritten to native code
* [x] Rewritten entire controls system
* [ ] More to come!

## Known Issues

See our [issue tracker](https://github.com/AngelAuraMC/Amethyst-Android/issues) for a list of known issues and their current status.

## FAQ

See our [wiki](https://wiki.angelauramc.dev/) for more information.

## Contributing

Contributions are welcome! We welcome any type of contribution, not only code. For example, you can help improve the [wiki](https://github.com/AngelAuraMC/angelauramc.github.io/), contribute to the [translations](https://crowdin.com/project/pojavlauncher), or submit bug reports and feature requests.

Any code change should be submitted as a pull request. The description should explain what the code does and give steps to execute it.

## Support

For support, please join our [Discord server](https://discord.gg/5ptqkyZxEy).

## License

Amethyst is licensed under [GNU LGPLv3](https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/LICENSE).

## Credits & Dependencies

* [Boardwalk](https://github.com/zhuowei/Boardwalk) (JVM Launcher): Unknown License/[Apache License 2.0](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) or GNU GPLv2.
* [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GLGPL](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
* Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
* [GL4ES](https://github.com/AngelAuraMC/gl4es): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
* [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues): [LGPL-2.1 License](https://github.com/MobileGL-Dev/MobileGlues/blob/dev-es/LICENSE).
* [Krypton Wrapper](https://github.com/BZLZHH/NG-GL4ES): [MIT License](https://github.com/BZLZHH/NG-GL4ES/blob/main/LICENSE)
* [ANGLE](https://chromium.googlesource.com/angle/angle): [All Rights Reserved](app_pojavlauncher/src/main/assets/licenses/ANGLE_LICENSE).
* [OpenJDK](https://github.com/AngelAuraMC/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
* [LWJGL3](https://github.com/AngelAuraMC/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
* [LWJGLX](https://github.com/AngelAuraMC/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license.
* [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
* [bhook](https://github.com/bytedance/bhook) (Used for exit code trapping): [MIT license](https://github.com/bytedance/bhook/blob/main/LICENSE).
* [libepoxy](https://github.com/anholt/libepoxy): [MIT License](https://github.com/anholt/libepoxy/blob/master/COPYING).
* [virglrenderer](https://github.com/AngelAuraMC/virglrenderer): [MIT License](https://gitlab.freedesktop.org/virgl/virglrenderer/-/blob/master/COPYING).
* [OpenAL-Soft](https://github.com/kcat/openal-soft): [GNU GPLv2](app_pojavlauncher/src/main/assets/licenses/OPENAL-SOFT_GPL2)
  * [oboe](https://github.com/google/oboe): [Apache License 2.0](app_pojavlauncher/src/main/assets/licenses/OBOE_APACHE2).
  * [pfffft](https://bitbucket.org/jpommier/pffft/src/master/): [ARR](app_pojavlauncher/src/main/assets/licenses/PFFFT_LICENSE)
* [SDL3](https://github.com/libsdl-org/SDL): [zlib License](https://github.com/libsdl-org/SDL/blob/main/LICENSE.txt)
* [sdl2-compat](https://github.com/libsdl-org/sdl2-compat): [zlib License](https://github.com/libsdl-org/sdl2-compat/blob/main/LICENSE.txt)
* Thanks to [MCHeads](https://mc-heads.net) for providing Minecraft avatars.

## Roadmap

We are currently focusing on:

* Exploring new rendering technologies.

Future plans include:

* Improving stability and performance.
* Enhancing the mod installation experience.

We welcome community feedback and suggestions for our roadmap.  Please feel free to open a feature request in our [issue tracker](https://github.com/AngelAuraMC/Amethyst-Android/issues).
