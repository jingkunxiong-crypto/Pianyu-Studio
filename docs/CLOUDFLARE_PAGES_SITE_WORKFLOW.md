# 静态项目官网：从制作到 Cloudflare Pages 上线

这份流程以 Pianyu Studio 官网为实例，适用于介绍页、作品集、软件下载页等**静态网站**。示例命令使用 Windows PowerShell。将 `my-project`、`app.example.com` 和 `site` 换成新项目的实际名称、域名与输出目录即可复用。

> 当前实例：本地目录 `E:\Android Studio\Pianyu Studio\site` → Pages 项目 `pianyu-studio` → `https://pianyu.jacksakuya.com/`。本站采用 **Direct Upload**，不是 Git 自动构建。Cloudflare 说明 Direct Upload 项目之后不能直接切换为 Git 集成；若未来需要 Git 自动部署，应在新项目开始时选好方式。[官方说明](https://developers.cloudflare.com/pages/get-started/direct-upload/)

## 1. 先确定网站的内容与形式

把信息写成一页简单清单，再开始设计：

| 要决定的事 | Pianyu Studio 的做法 | 新项目填写 |
| --- | --- | --- |
| 网站目标 | 展示应用、直接下载 Android APK | 项目介绍 / 获客 / 下载 / 作品集 |
| 主要访问设备 | 手机优先，同时兼容桌面 | 手机 / 桌面 / 两者 |
| 主要行动按钮 | 下载 APK | 下载 / 联系 / 购买 / 注册 |
| 页面顺序 | 首页 → 创作方式 → 四个功能 → 关于 → 下载 → 制作信息 | 写下自己的顺序 |
| 视觉素材 | 演示照片、应用图标衍生图 | 自有图片、授权素材 |
| 互动 | 翻页动画、功能示例 | 只保留能解释产品的互动 |

先做文字和版式，再补视觉素材。Pianyu 官网没有用一排应用截图代替介绍，而是用照片演示功能结果；不同功能页用对应的浅色背景，最后两页才用深色收束。手机端应先看首屏、每页信息密度、按钮位置与图片加载速度。

## 2. 准备可部署文件

纯 HTML/CSS/JS 项目可以直接用下面的目录。Cloudflare Pages 上传的是**整个输出目录**，目录根部要有 `index.html`。

```text
my-project/
├─ site/
│  ├─ index.html
│  ├─ styles.css
│  ├─ script.js
│  ├─ assets/
│  │  ├─ cover.webp
│  │  └─ cover-800.webp
│  ├─ download/                 # 需要直接下载时才创建
│  │  └─ MyApp-v1.0.apk
│  └─ _headers                  # 需要自定义响应头时才创建
└─ docs/
```

- 使用 Vite 等工具时，先运行项目自己的构建命令，部署 `dist` 等**构建输出目录**，不要把源代码目录当成静态输出目录。
- 图片优先用 WebP/AVIF，并为大图准备较小尺寸；在 HTML 中用 `srcset`、`sizes`，非首屏图片用 `loading="lazy"`。首屏主图应优先加载。Pianyu 使用本地 GSAP 文件，避免动画库从第三方 CDN 加载。
- 不把 API 密钥、签名密钥、`.env`、私密图片或原始工作素材放进输出目录。Cloudflare 会公开服务该目录中的静态文件。
- 若提供 APK，下载链接可写成同域名绝对路径：`href="/download/MyApp-v1.0.apk"`。文件名带版本号，便于更新后区分缓存。

### APK 下载响应头（可选）

在 `site/_headers` 写入以下内容；路径和文件名要与实际文件完全一致：

```text
/download/MyApp-v1.0.apk
  Content-Type: application/vnd.android.package-archive
  Content-Disposition: attachment; filename="MyApp-v1.0.apk"
  Cache-Control: public, max-age=31536000, immutable
```

`immutable` 只适合**文件名随版本变化**的安装包。若始终复用同一个文件名，就不要使用一年缓存。`_headers` 必须放在最终上传目录中；Cloudflare Pages 会解析它，而不会把它作为普通文件提供。[响应头规则](https://developers.cloudflare.com/pages/configuration/headers/)

## 3. 本地预览与发布前检查

在项目根目录运行：

```powershell
python -m http.server 4173 --directory site
```

打开 `http://localhost:4173/`。若项目使用 Vite，运行其开发命令，并在发布前另行检查构建后的 `dist`。至少检查手机窄屏、常见手机宽度和桌面宽度（例如 320、390、1440 像素）；逐页看文字是否遮挡、图片是否变形、按钮能否点击、动画与“减少动态效果”设置是否兼容。Pianyu 上线前就是按这些宽度看实际渲染图，再修正小屏的装饰图形。

用 PowerShell 检查输出目录中是否有超过 Pages Direct Upload 单文件限制的文件：

```powershell
Get-ChildItem .\site -File -Recurse |
  Where-Object Length -gt 25MB |
  Select-Object FullName, Length
```

当前 Cloudflare 文档列出的 Wrangler Direct Upload 上限为 **20,000 个文件、单文件 25 MiB**；限制可能变化，上线前再看[官方 Direct Upload 文档](https://developers.cloudflare.com/pages/get-started/direct-upload/)。大于限制的安装包可单独放在 R2，并绑定下载子域名，再让网站按钮指向它。[R2 公共存储桶说明](https://developers.cloudflare.com/r2/buckets/public-buckets/)

## 4. 登录 Cloudflare 并创建 Pages 项目

需要 Node.js/npm、Cloudflare 账号，以及可用的项目名。首次在本机部署：

```powershell
npx --yes wrangler@latest login
npx --yes wrangler@latest whoami
```

`login` 会打开浏览器完成授权；`whoami` 用来确认 CLI 登录到预期账号。如果账号下有多个 Cloudflare account，可从控制台确认目标 account ID，再在当前 PowerShell 会话设置 `$env:CLOUDFLARE_ACCOUNT_ID = '你的-account-id'`。无需把它写入网站文件。[Wrangler 登录命令](https://developers.cloudflare.com/workers/wrangler/commands/general/)

创建 Direct Upload 项目，并把正式分支设为 `main`：

```powershell
$Project = 'my-project'
npx --yes wrangler@latest pages project create $Project --production-branch main
```

也可以在 Cloudflare 控制台 `Workers & Pages → Create application → Pages → Direct Upload` 创建；此处只需选择一种创建方式。项目创建成功后会有 `https://my-project.pages.dev/`。[Wrangler Pages 命令](https://developers.cloudflare.com/workers/wrangler/commands/pages/)

## 5. 上传正式版本

在项目根目录执行；把 `site` 换成实际的静态输出目录：

```powershell
$Project = 'my-project'
$OutputDir = 'site'
npx --yes wrangler@latest pages deploy $OutputDir --project-name $Project --branch main
```

每次更新网站都重复这条部署命令。部署成功后，Wrangler 会给出一个 `*.pages.dev` 部署地址。若 Git 工作区未提交，Wrangler 可能提示有未提交修改；这是提示，应确认自己上传的目录内容确实是想发布的版本。`--branch main` 要与创建项目时指定的正式分支一致。[Pages 部署参数](https://developers.cloudflare.com/workers/wrangler/commands/pages/)

Pianyu 的实际命令是：

```powershell
npx --yes wrangler@latest pages deploy site --project-name pianyu-studio --branch main
```

## 6. 绑定自己的子域名

先在 Cloudflare 控制台打开 `Workers & Pages → 你的 Pages 项目 → Custom domains → Set up a domain`，输入例如 `app.example.com`。**先在 Pages 里关联自定义域名**；只手工加 DNS CNAME 而不关联 Pages，可能出现 522 错误。若主域名的 DNS 已由同一 Cloudflare 账号管理，确认后通常会自动创建所需 CNAME；若 DNS 在外部服务商，按控制台提示把子域名 CNAME 指向 `<项目名>.pages.dev`。等待域名与证书状态变成 Active 后，用 HTTPS 打开。[自定义域名步骤](https://developers.cloudflare.com/pages/configuration/custom-domains/)

Pianyu 的对应关系：`pianyu.jacksakuya.com` → Pages 项目 `pianyu-studio`。新项目可以用另一个子域名，不必改动已有主站。

## 7. 上线后验证

浏览器打开正式域名，并在手机上走一遍主要流程；不要只看 Wrangler 的“部署成功”。PowerShell 可先检查主页和下载文件的 HTTP 状态：

```powershell
$Base = 'https://app.example.com'
(Invoke-WebRequest -Uri "$Base/" -UseBasicParsing).StatusCode
(Invoke-WebRequest -Uri "$Base/download/MyApp-v1.0.apk" -Method Head -UseBasicParsing).StatusCode
```

如果必须确认线上 APK 与本地文件完全相同，可下载一份临时副本比较 SHA-256：

```powershell
$LocalApk = '.\site\download\MyApp-v1.0.apk'
$RemoteApk = Join-Path $env:TEMP 'MyApp-v1.0-online-check.apk'
Invoke-WebRequest -Uri 'https://app.example.com/download/MyApp-v1.0.apk' -OutFile $RemoteApk
(Get-FileHash $LocalApk -Algorithm SHA256).Hash
(Get-FileHash $RemoteApk -Algorithm SHA256).Hash
```

两行哈希应相同。再检查下载响应头里的 `Content-Type`、`Content-Disposition`，并在真实手机上确认下载与安装流程。对图片加载慢的问题，先看手机实际下载的图片尺寸、文件大小和首屏资源顺序，不要仅凭电脑端体验判断。

## 8. 更新和常见问题

| 现象 | 优先检查 |
| --- | --- |
| 上传成功但域名仍是旧版 | 确认部署到 `main`、自定义域名指向该项目；强制刷新并检查 CSS/JS 实际响应内容 |
| 页面正常，下载 404 | APK 是否放进**输出目录**、下载链接大小写是否一致 |
| APK 太大传不上去 | 核对当前单文件限制；大文件改用 R2 自定义域名 |
| 子域名 522 | 是否在 Pages `Custom domains` 先关联；DNS 是否指向正确的 Pages 项目 |
| 手机图片慢 | 换小尺寸 WebP/AVIF、补 `srcset`，非首屏延迟加载；避免多个大图同时请求 |
| 修改 `_headers` 没生效 | 文件是否在上传目录根部；请求是否由静态资源提供而不是 Pages Function |

### 每个新项目的最短复用路径

1. 复制“第 1–3 步”的内容规划和静态目录结构，完成页面与手机检查。
2. 替换项目名、输出目录、域名、下载文件名。
3. 首次运行 `login`、`pages project create`、`pages deploy`。
4. 在 Pages `Custom domains` 绑定子域名。
5. 按“第 7 步”检查线上页面、资源、下载与手机效果。

此流程适用于纯静态输出；需要登录、数据库、动态 API 或服务端渲染的项目，应先选相应架构，再决定是否使用 Pages Functions 或 Workers。
