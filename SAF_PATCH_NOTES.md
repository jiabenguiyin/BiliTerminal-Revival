# 外置 SD 卡 SAF 修复说明

此补丁为 BiliTerminal 1.1.1 增加可持久化的 Storage Access Framework（SAF）目录权限，解决“移动到外置 SD 卡后可以读取，但下载器不能创建/续写文件”的问题。

## 使用方法

1. 打开“设置 → 实验室 → 缓存存储位置”。
2. 选择“选择其他文件夹（SAF）”。
3. 在系统文件选择器中进入外置 SD 卡。
4. 选择已有的 BiliTerminal 缓存根目录，或新建并选择一个目录。
5. 授权完成后，新下载和本地缓存扫描都会使用该目录。

不要把系统返回的 `content://` URI 转换成 `/storage/XXXX-XXXX/...` 普通路径；普通路径不携带 SAF 写权限。

## 修改内容

- 新增 `VideoStorageUtil`，统一普通 File 与 SAF DocumentFile 两种存储后端。
- 保存并持久化 `ACTION_OPEN_DOCUMENT_TREE` 的读写权限。
- 选择目录时创建、写入和删除探针文件，确认目录真实可写。
- 新版下载服务支持 SAF 文件创建、覆盖写入和 HTTP Range 断点续传。
- 文档提供程序不支持 seek 时，自动清空该分片并从头重新下载。
- 旧版下载器也接入 SAF 写入。
- 下载数据库升级到版本 5，每个任务记录 `storage_mode` 和 `storage_ref`，避免切换目录后任务路径漂移。
- 本地缓存列表支持扫描 SAF 目录、读取封面、播放 `content://` 媒体、读取弹幕和字幕、删除视频及分页。
- 视频详情页、多 P 页面和下载任务删除逻辑支持 SAF。
- 移除可直接填写任意 `/storage/...` 路径的“缓存路径”输入项，防止再次保存一个无写权限的伪路径。

## 兼容性

- SAF 自定义目录需要 Android 5.0（API 21）或更高版本。
- Android 4.x 继续使用应用专属目录或普通文件路径。
- 选择 `Android/data/<包名>/files/video` 类型的应用专属外置目录时，不需要 SAF，但卸载应用时目录可能被系统删除。
- 选择已有缓存目录后，不需要再次搬运文件；扫描器会直接读取该目录。

## 建议实机测试

1. 选择外置 SD 卡已有缓存目录，确认旧视频和封面可见。
2. 下载单集视频，确认生成 `.DOWNLOADING`、`cover.png`、`danmaku.xml`、`video.mp4`。
3. 暂停并继续下载，确认支持续传；若设备文档提供程序不支持 seek，应从零重下而不是持续报错。
4. 下载多 P 视频并删除其中一 P。
5. 播放 SAF 视频，检查弹幕和本地字幕。
6. 重启应用，确认目录权限仍有效。
7. 拔出再插入 SD 卡，确认应用能提示目录不可访问并允许重新授权。

## 验证状态

已完成：

- `clean testDebugUnitTest lintDebug assembleRelease` 完整通过。
- Android 4.0.4（API 15）模拟器安装与启动通过，没有新增 API 校验错误。
- Android 5.0（API 21）确认可以进入系统 SAF 目录选择器。
- Android 9（API 28）完成目录授权、写入探针和应用重启后的持久授权验证。
- API 28 设备测试验证覆盖写入、追加写入、读回校验与递归删除。
- API 28 设备测试验证下载数据库从 v4 升级到 v5 后保留旧任务及原文件存储位置。
- Release APK 覆盖安装与启动通过，签名证书与现有 1.1.1 正式版一致。

复测命令：

```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

