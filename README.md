# Muse Player

极简本地音乐播放器（Android）。

## 功能

- 打开即播：自动扫描手机里的音乐，随机选一首开始
- 极简界面：专辑封面 + 上一首 / 播放暂停 / 下一首
- 内嵌封面：自动提取音频文件的内嵌专辑图
- 播放列表：当前歌曲永远置顶，点歌即播
- 删除：从播放列表移除（不删文件），带确认弹窗
- .smi 歌词：同名 `.smi` 文件自动加载，歌词同步滚动高亮
- 零网络权限：纯本地播放，不连服务器

## 构建

本项目使用手动构建（aapt2 + javac + d8），因为环境 Gradle daemon 不可用：

```bash
./build.sh
```

需要：
- JDK 17
- Android SDK build-tools 34.0.0、platform android-34
- 签名密钥（`../muse-offline-player/muse-release.keystore`，不随代码发布）

## 下载

- [muse-player.apk](muse-player.apk)（直接安装）

## 截图

| 播放页 | 播放列表 |
|---|---|
| 封面 + 三按钮 | 点播 / 删除 |

## License

MIT
