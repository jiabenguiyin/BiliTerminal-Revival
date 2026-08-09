# BiliTerminal Revival

哔哩终端复活版，一款面向智能手表、旧手机和低性能 Android 设备的轻量 B 站客户端。

本项目基于原 BiliClient 代码继续维护，重点保留小屏设备适配、Android 4.0.4 兼容、内置播放器、动态、评论、收藏和离线缓存等能力。

## 特性

- 支持 Android 4.0.4（API 15）及以上设备
- 面向智能手表、旧手机和低性能设备优化
- 内置视频、直播和音频播放
- 动态、评论、收藏、私信和关注功能
- 下载任务支持暂停、继续、断点续传和 SAF 外置 SD 卡目录
- 直连优先，网络异常时可按配置切换兼容中继
- GPL-3.0 开源许可

## 构建

需要 Android SDK、JDK 17 和项目自带的 Gradle Wrapper：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

未配置私有签名证书时，Release 构建会使用 Gradle debug 签名配置。正式发布请使用自己的签名证书，并通过 `local.properties` 配置，不要把证书或密码提交到仓库。

## SAF 外置 SD 卡

Android 5.0 及以上设备可以在设置中选择 SAF 目录作为缓存位置。Android 4.x 设备继续使用应用专属目录或普通文件路径。

详细说明见 [SAF_PATCH_NOTES.md](SAF_PATCH_NOTES.md)。

## 免责声明

本项目仅供学习、研究和自用设备使用。请遵守哔哩哔哩服务条款、当地法律法规以及网络服务提供方的使用规则。请勿进行高频抓取、刷量、绕过付费内容或影响他人服务的行为。

## 许可与致谢

本项目采用 [GPL-3.0](LICENSE) 许可。项目参考并使用了部分开源项目和组件，具体以源代码中的许可文件及版权声明为准。
