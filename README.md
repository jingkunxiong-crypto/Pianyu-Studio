<div align="center">

<img src="app/src/main/res/drawable-nodpi/studio_icon.png" width="96" alt="Pianyu Studio 图标" />

# Pianyu Studio · 片屿工作室

**留住灵感，慢慢完成。**

一款将照片编辑、自由拼图、签名水印与色彩取样整合在一起的 Android 创作工具。

白色磨砂界面 · 本机处理 · 统一创作流程

[下载预览版](https://github.com/jingkunxiong-crypto/Pianyu-Studio/releases/tag/v0.1.0-preview) · [功能介绍](#四种创作方式) · [构建说明](#从源码构建)

</div>

## 四种创作方式

| 功能 | 可以做什么 |
| --- | --- |
| 照片编辑 | 裁剪、旋转、光线与色彩调整、本地滤镜，完成后继续添加签名 |
| 自由拼图 | 将 2–9 张照片组合排版，调整成品比例和内部间距，裁切或替换单张照片 |
| 签名水印 | 手写、提取和管理透明签名，为照片署名，支持批量处理 |
| 色彩取样卡 | 从照片提取 4–6 种代表色，生成圆形色块与 HEX 色号组成的配色卡 |

### 从照片到作品，一套连贯的交互

- 固定首页：四个带功能线条图案的圆角入口，2 × 2 排列，无需滚动寻找工具。
- 统一视觉：浅色磨砂面板、灰绿色强调色、清晰的正文与说明层级。
- 连贯动效：开场图标归位、页面进入与返回过渡、按钮按压反馈和拼图面板切换。
- 最近作品：最新作品置顶叠放，点击进入完整列表；记录最近 30 个已保存作品。
- 连续创作：照片编辑和拼图导出后可以接着添加签名。

### 色彩取样卡

提供自适应、1:1、3:4、4:5、4:3、16:9 卡片比例；完整容纳照片，底部排列圆形色块及 HEX 色号。

开启签名时，署名位于左下角，色块排列于右下角。直接共用应用内签名水印模块的签名库，无需重复创建；色卡中使用深色墨迹。接近纯色的照片可能提取到少于设定数量的颜色。

## 界面预览

以下为实际 Android 模拟器截图。

<table>
<tr><th>创作首页</th><th>色彩取样卡</th></tr>
<tr><td><img src="docs/screenshots/home.png" width="300" alt="四个创作入口与最近作品" /></td><td><img src="docs/screenshots/color-card.png" width="300" alt="色卡比例和签名选项" /></td></tr>
<tr><th>自由拼图</th><th>签名水印</th></tr>
<tr><td><img src="docs/screenshots/collage.png" width="300" alt="自由拼图界面" /></td><td><img src="docs/screenshots/signature.png" width="300" alt="签名水印界面" /></td></tr>
</table>

## 安装

1. 前往 [Releases](https://github.com/jingkunxiong-crypto/Pianyu-Studio/releases)。
2. 下载 `Pianyu-Studio-v0.1.0-preview.apk`，在 Android 手机上打开安装。
3. 如系统询问，允许当前下载来源安装应用。

支持 **Android 8.0（API 26）及以上**。此版本为 **debug 签名的预览版**，不是应用商店正式版。Release 同时提供 SHA-256 校验文件。

内置签名为通用的 **Pianyu 演示字样**，不包含作者个人签名。

## 隐私与保存

照片、配色提取和签名处理在设备本地完成，当前应用未声明网络访问权限。通过系统照片选择器读取用户选择的图片；保存时生成新作品，不覆盖原图。Android 8–9 保存到相册时需要存储权限。

签名库是 Pianyu Studio 应用内部共享的库；不会自动读取其他独立应用的私有数据。旧签名可以通过透明 PNG 导入。

## 从源码构建

- 安装 Android Studio、JDK 25（Gradle Daemon 配置指定版本）、Android SDK Platform 37。
- 项目使用 Kotlin、Jetpack Compose、Material 3，Gradle Wrapper 9.4.1，Android Gradle Plugin 9.2.1。
- 在 Android Studio 中打开仓库根目录，设置本机 SDK 位置并同步 Gradle。

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。

本机 SDK 路径、构建缓存、签名密钥与临时工作目录不纳入版本控制。

## 版本状态

`0.1.0-preview`：已完成本地 Debug 构建及 Android 模拟器上的基础启动、导航和界面检查；尚未覆盖不同品牌真机、系统版本和大字体的完整兼容性测试。

欢迎通过 Issues 提交问题，并附上系统版本、操作步骤和截图。

## 项目来源

整合片屿、Geyi、Signature Watermark Studio 的创作能力，并统一主题与入口。依赖及来源说明见 [THIRD_PARTY_NOTICES](docs/THIRD_PARTY_NOTICES.md)。
