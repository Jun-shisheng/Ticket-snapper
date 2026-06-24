# Ticket Snapper — ADB 抢票连点器

针对 vivo 手机（OriginOS / FunTouch OS）封死无障碍服务 `dispatchGesture` 的解决方案。Android App 负责悬浮框定位和启停控制，Python 脚本通过 ADB `input tap` 执行真正的极限连点。

## 架构

| 层 | 运行位置 | 职责 |
|---|---|---|
| Android App (`app/`) | 手机 | 悬浮框定位、启停状态、音量键停止 |
| Python 脚本 (`tap.py`) | 电脑 | ADB 高速连点，绕过系统限制 |

## 功能

- **双框独立定位**：橙色框瞄准提交按钮，蓝色框瞄准重试按钮
- **Turbo 模式**：框1 手机端死循环极限连点；框2 检测到「再试/再抢」等文字自动激活
- **普通模式**：可调间隔连点（默认读取 App 滑条设置，最低 10ms）
- **安全停止**：音量键 / 悬浮窗「停」/ 通知栏 / Ctrl+C 四通道
- **悬浮窗触摸穿透**：连点中定位框不拦截触摸，ADB 点击落入下层 App

## 环境要求

- **手机**：Android 8.0+，已开启 USB 调试
- **电脑**：Windows / macOS / Linux，已安装 ADB 和 Python 3.x
- **ADB**：`platform-tools` 已加入 PATH，或设置 `ADB` 环境变量

## 快速开始

### 1. 构建安装

```powershell
$env:JAVA_HOME="你的JDK路径"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 手机端准备

1. 打开 **TicketSnap** App
2. 授予 **悬浮窗权限** 和 **无障碍服务**
3. 点「开启连点」→ 出现三个浮控件
4. 在抢票页面上拖动定位框到目标按钮
5. 点绿色「启」

### 3. 电脑端启动连点

```bash
# 推荐：Turbo 极限连点
python tap.py --nowait --turbo

# 可调间隔（ms）
python tap.py --nowait --interval 30

# 仅框1
python tap.py --nowait --turbo --no-box2

# 框2 始终连点（慎用）
python tap.py --nowait --turbo --force-box2
```

### 停止方式

| 方式 | 操作 |
|---|---|
| 音量键 | 连点中按音量 + / - |
| 悬浮窗 | 点红色「停」 |
| 通知栏 | 紧急停止 |
| 电脑 | Ctrl + C |

## 项目结构

```
├── tap.py                    # Python ADB 连点脚本
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/ticketsnap/
│           ├── MainActivity.kt               # 主界面
│           ├── FloatingOverlayService.kt     # 悬浮窗服务
│           ├── TapAccessibilityService.kt    # 无障碍服务
│           └── PreferencesManager.kt         # 坐标/状态持久化
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## 技术细节

- 坐标通过 `run-as` 读取 SharedPreferences，自动 +63px 从左上角转中心点
- 框2 触发：屏幕 uiautomator dump 匹配正则 `再.`（再试、再抢、再次等）
- 停止检测：后台轮询 `is_running` 字段，状态从 true→false 时安全退出
- Turbo 模式框1 在手机端 while 死循环，无 IPC 开销

## License

MIT
