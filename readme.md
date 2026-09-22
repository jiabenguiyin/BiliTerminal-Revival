<h1 align="center">哔哩终端复活版</h1>

<p align="center">
  <strong>BiliTerminal Revival</strong><br>
  为智能手表、旧手机和低性能 Android 设备继续维护的轻量哔哩哔哩客户端
</p>

<p align="center">
  <a href="https://github.com/jiabenguiyin/BiliTerminal-Revival/actions/workflows/android.yml"><img alt="Android build" src="https://github.com/jiabenguiyin/BiliTerminal-Revival/actions/workflows/android.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="GPL-3.0" src="https://img.shields.io/badge/License-GPL--3.0-blue.svg"></a>
  <img alt="Android 4.0.4+" src="https://img.shields.io/badge/Android-4.0.4%2B-3DDC84.svg">
</p>

<p align="center">
  <a href="https://jp.031030.xyz/">官网与下载</a> ·
  <a href="https://jp.031030.xyz/#updates">更新日志</a> ·
  <a href="https://jp.031030.xyz/#guide">使用说明</a> ·
  <a href="https://jp.031030.xyz/#status">服务状态</a> ·
  <a href="https://github.com/jiabenguiyin/BiliTerminal-Revival/issues">GitHub 问题反馈</a> ·
  <span>反馈群：1107953621</span>
</p>

> 正式版和测试版安装包统一从[哔哩终端复活版官网](https://jp.031030.xyz/#download)获取。GitHub 仓库用于公开源码、构建记录和问题跟踪。

## 项目简介

哔哩终端复活版是一款面向智能手表、旧手机和低性能 Android 设备的轻量 B 站客户端。

本项目基于原 BiliClient 代码继续维护，重点保留小屏设备适配、Android 4.0.4 兼容、内置播放器、动态、评论、收藏和离线缓存等能力。客户端优先直连 B 站，网络异常时可按配置切换兼容中继。

## 核心功能

- 支持 Android 4.0.4（API 15）及以上设备
- 面向智能手表、旧手机和低性能设备优化
- 内置视频、直播和音频播放器
- 支持动态、评论、收藏、私信和关注功能
- 下载任务支持暂停、继续和断点续传
- Android 5.0 及以上支持 SAF 外置 SD 卡目录
- 直连优先，网络异常时可按配置切换兼容中继

## 下载安装

1. 打开[官网下载安装页](https://jp.031030.xyz/#download)。
2. 日常使用请选择正式版；测试版用于提前验证新功能，稳定性可能较低。
3. 安装前请确认设备允许安装来源可信的 APK，并保留原有数据后再覆盖升级。

最新版本号、发布日期、文件大小和校验值均以官网实时数据为准。

## 兼容范围

| 项目 | 支持情况 |
| --- | --- |
| 最低系统 | Android 4.0.4（API 15） |
| 主要设备 | 智能手表、旧手机、安卓点读设备及低性能终端 |
| 外置 SD 卡 | Android 5.0+ 使用 SAF；Android 4.x 使用原有文件路径 |
| CPU 架构 | 以官网当前安装包说明为准 |

## 本地构建

需要 Android SDK、JDK 17 和项目自带的 Gradle Wrapper：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

未配置私有签名证书时，Release 构建会使用 Gradle debug 签名配置。正式发布请使用自己的签名证书，并通过 `local.properties` 配置；不要把证书、密码、Cookie 或 Token 提交到仓库。

## SAF 外置 SD 卡

Android 5.0 及以上设备可以在设置中选择 SAF 目录作为缓存位置。Android 4.x 设备继续使用应用专属目录或普通文件路径。

详细实现与验证说明见 [SAF_PATCH_NOTES.md](SAF_PATCH_NOTES.md)。

## 问题反馈

发现问题时可以在 [GitHub Issues](https://github.com/jiabenguiyin/BiliTerminal-Revival/issues) 提交，也可以加入反馈群 `1107953621`。请尽量附上设备型号、Android 版本、复现步骤、网络类型和日志上传 ID。请勿公开粘贴登录 Cookie、Token 或其他账号凭据。

服务是否正常可先查看[官网服务状态](https://jp.031030.xyz/#status)。

## 许可与致谢

本项目采用 [GNU GPL-3.0](LICENSE) 许可，并基于原 BiliClient 项目继续维护。项目引用的其他开源组件分别遵循其自身许可证，具体以源代码中的版权声明和许可文件为准。

## 免责声明

本项目仅供学习、研究和自用设备使用。请遵守哔哩哔哩服务条款、当地法律法规以及网络服务提供方的使用规则。请勿进行高频抓取、刷量、绕过付费内容或影响他人服务的行为。
