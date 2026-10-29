# Pianyu Studio 官网

纯静态官网。九个介绍页面以带透明磨砂边框的卡片呈现，滚轮、触屏上下滑动、方向键或页码按钮每次翻动一页；进入页面时会重播内容动画，并遵循系统的减少动态效果设置。页面用四种摄影效果示意作品，局部留彩区域可拖动、切换形状和调整大小；过场动画使用本地 GSAP 文件，无构建步骤。网站没有引用应用界面截图。

`assets/coast-umbrella.png` 与 `assets/flower-still-life.png` 是为网站生成的演示照片，`assets/demo-landscape.webp` 是项目已有的演示素材。这些视觉示意不代表从应用实际导出的作品。

下载页和制作页使用 `pianyu-ending-atmosphere.webp` 与 `pianyu-ending-emblem.webp`：以应用图标的磨砂方形和绿色弧线为灵感，分别用作深绿底图和透明图标衍生大图。两张 WebP 合计约 109 KB，供移动端按需加载。

移动端翻页保留卡片与半透明描边，但避免在全屏边框上实时计算背景模糊，并缩短过场和页内动画。英文字体存放在 `assets/fonts/`，各字体的开源许可证也在该目录；中文使用设备字体，不依赖 Google Fonts 服务。

下载按钮指向同域名下的 `download/Pianyu-Studio-v0.4.0-preview.apk`。部署前把已验证的 Android APK 放到此路径；该预览包使用 Android Debug 签名。安装包不提交到 Git 仓库，Cloudflare Pages 直接上传时会包含本地文件。

## 本地预览

在仓库根目录运行：

```powershell
python -m http.server 4173 --directory site
```

然后访问 `http://localhost:4173`。

## Cloudflare Pages

当前项目 `pianyu-studio` 使用 Direct Upload 部署，正式域名为 <https://pianyu.jacksakuya.com/>。在仓库根目录运行：

```powershell
npx wrangler@latest pages deploy site --project-name pianyu-studio --branch main
```

上传前确认 `site/download/Pianyu-Studio-v0.4.0-preview.apk` 存在；它会随网站一起上传，但不会提交到 Git 仓库。正式下载地址：<https://pianyu.jacksakuya.com/download/Pianyu-Studio-v0.4.0-preview.apk>。

每次更新 `styles.css` 或 `script.js` 时，同时更新 `index.html` 中对应的 `?v=` 版本号，以避开自定义域名上可能保留的旧静态资源缓存。
