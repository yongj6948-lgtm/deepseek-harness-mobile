<p align="center">
  <img src="design/android-whale-logo-white.png" width="260" alt="DeepSeek Harness Mobile logo">
</p>

<h1 align="center">DeepSeek Harness Mobile</h1>

<p align="center">
  一个连接自托管 DeepSeek Harness 的 Android 客户端。
</p>

<p align="center">
  <a href="https://github.com/Venompool888/deepseek-harness-mobile/releases/latest"><strong>下载最新版 APK</strong></a>
</p>

## 不只是聊天：在手机上看着 Agent 干活

原生 Android 界面会持续呈现 Harness 的真实执行过程，而不是只留下一个等待动画：

- 实时展示 **Think、Write、工具调用、回复内容和运行时长**。
- **To-dos** 汇总已完成、进行中和待处理步骤，长任务进度一眼可见。
- 上下文仪表显示使用比例，并可展开查看 System prompt、Tools 与 Messages 占用。
- 切到后台后，系统通知仍会显示 Harness 任务正在运行，方便随时返回会话。
- 在支持的 OPPO / ColorOS 设备上，可通过 **流体云** 快速查看当前任务状态。

<p align="center">
  <img src="docs/images/showcase/live-agent-progress.png" width="310" alt="在 Android 原生界面实时查看 Agent 的思考、写入、待办与运行时长">
  &nbsp;
  <img src="docs/images/showcase/context-and-todos.png" width="310" alt="查看 Harness 上下文占用明细和已完成的任务清单">
</p>

<p align="center">
  <sub>实时执行过程、上下文压力与任务清单，都集中在同一个移动会话里。</sub>
</p>

<p align="center">
  <img src="docs/images/showcase/background-task-notification.png" width="520" alt="Android 系统通知持续显示 DeepSeek Harness 后台任务运行状态">
</p>

<p align="center">
  <sub>离开应用也能看到后台任务状态。</sub>
</p>

### ColorOS 流体云

已在 OPPO CPH2797（ColorOS 16）实机显示胶囊态与展开卡片态，无需打开应用即可确认 Harness 任务仍在运行。

<p align="center">
  <img src="docs/images/showcase/coloros-fluid-cloud-capsule.jpg" width="620" alt="DeepSeek Harness 任务在 ColorOS 流体云中的胶囊态">
</p>

<p align="center">
  <img src="docs/images/showcase/coloros-fluid-cloud-expanded.jpg" width="620" alt="DeepSeek Harness 任务在 ColorOS 流体云中的展开卡片态">
</p>

<p align="center">
  <sub>胶囊态快速扫一眼，展开后查看任务标题与运行状态。</sub>
</p>

## 安装

本流程已在 **OPPO CPH2797（Android 16 / ColorOS 16）** 上实际验证。

1. 在手机 Chrome 中打开 [Latest Release](https://github.com/Venompool888/deepseek-harness-mobile/releases/latest)。
2. 找到 **Assets**，点击 `deepseek-harness-mobile-v1.1.0.apk` 下载。
3. 下载完成后打开 APK，并按 ColorOS 的安装提示继续。如果系统阻止安装来自浏览器的应用，请按系统提示临时允许当前浏览器安装未知应用，然后返回继续安装。
4. 安装完成后打开 **DeepSeek Harness Mobile**。

<p align="center">
  <img src="docs/images/oppo/01-github-release-apk.png" width="320" alt="在 OPPO 手机上从 GitHub Release 下载 APK">
</p>

> 当前 v1.1.0 APK 的 SHA-256：`60e39df2e6e36210b752e21ec902211ef17becd5bad3824df90ef7318e3bf265`

## 首次连接

首次打开时，应用不会预置任何服务器地址：

1. 输入你自己的 Harness 服务器地址，例如 `https://harness.example.com`。
2. 点击 **测试并连接**。
3. 如果服务器启用了 Cloudflare Access，请在出现的登录页中完成服务器所有者配置的验证方式。
4. 如果 Harness 显示 **Internal Testing Notice**，点击 **Continue**。
5. 返回客户端后，顶部出现 **已连接** 即表示配置成功。
<p align="center">
  <img src="docs/images/oppo/02-first-launch.png" width="320" alt="首次打开时配置 Harness 服务器">
  &nbsp;
  <img src="docs/images/oppo/optional-harness-notice.png" width="320" alt="Harness Internal Testing Notice">
</p>

<p align="center">
  <img src="docs/images/oppo/03-connected-session.png" width="320" alt="连接成功后的原生会话界面">
</p>

实测中，强制停止并重新打开应用后，服务器配置、登录状态和当前会话均能保留。

## SSH 隧道连接（参考 dsh-mobile-app）

如果电脑的 `dsh web` 只监听在 `127.0.0.1:3080`，不想暴露公网 HTTPS，可以直接用隐私内网 SSH 隧道连接：

1. 在服务器设置对话框底部点击 **或通过 SSH 隧道连接（免公网 HTTPS）**。
2. 填写 **SSH 主机**（电脑的 VPN/IP 或域名）、**SSH 用户名**、**SSH 端口**（默认 22）与 **远端 DSH 端口**（默认 3080）。
3. 首次连接时填一次 **SSH 密码**：应用会经 SFTP 把本机公钥写入电脑 `~/.ssh/authorized_keys`，密码不落盘。
4. 连接成功后应用会把电脑 `127.0.0.1:3080` 通过 SSH 端口转发到手机回环地址，并把手机侧的 Harness 当作本机访问（Web Crypto / API 均可正常使用）。
5. 之后只走公钥，无需再输入密码；连接由前台服务保活，通知栏可随时断开。

- 首次启动会自动在应用私有目录生成 RSA 3072 密钥。
- 装公钥失败时，可在 SSH 设置里点击 **复制本机 SSH 公钥**，手动追加到电脑 `~/.ssh/authorized_keys`。
- 当前连接模式（直接 HTTPS / SSH 隧道）与 SSH 配置只保存在设备本地。

## 更换服务器

打开左侧边栏，点击底部设置菜单中的 **服务器连接**，即可测试并切换到其他内网 IP 或域名。

<p align="center">
  <img src="docs/images/oppo/04-settings-menu.png" width="520" alt="从侧边栏设置菜单进入服务器连接">
</p>

## 连接要求

- Android 8.0（API 26）或更高版本。
- 手机必须能够访问你的 Harness 服务器。
- 公网服务建议并默认要求使用 HTTPS。
- HTTP 仅适用于局域网 IP、`localhost` 或 `.local` 地址。

服务器地址与登录 Cookie 只保存在设备本地。本仓库及发布 APK **不包含维护者的服务器域名、账号、密钥或访问凭据**。

## 开发：离线编译验证（无 Android SDK 时）

本机可能没有 Android SDK / JDK 17+，无法直接跑 `gradle assemble`。可用仓库自带脚本做**离线等价编译**（类型/语法/资源引用层面校验，与真实编译基本等价）：

```bash
# 用 /tmp 下的工具链（kotlinc + android-all(API 36) + 真实 jsch/okhttp/okio）
tools/verify_compile.sh

# 若工具链换到了别处，用环境变量指定：
KOTLINC=/path/kotlinc ANDROID_JAR=/path/android-api36.jar tools/verify_compile.sh
```

脚本会**自动从源码扫描 `R.<type>.<name>` 引用生成 `R.kt`/`BuildConfig.kt` 存根**，编译全部 `app/src/main/java` 并报告 error。

> 注意：这只验证编译通过，**不产 APK**；真正的出包仍由 GitHub Actions（`.github/workflows/android.yml`）在有 Android SDK 的 runner 上完成。改代码前先用该脚本自查，能省去 CI 排队时间。
