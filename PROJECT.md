# TicketSnap 抢票连点器

## 项目目录

```
G:\Work\ticket-snapper\
├── tap.py                 # [主力] Python ADB 点击脚本
├── PROJECT.md             # 本文档
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ticketsnap/
│       │   ├── MainActivity.kt              # 主界面（授权、开关悬浮窗）
│       │   ├── FloatingOverlayService.kt    # 悬浮窗（拖动框 + 启停钮）
│       │   ├── TapAccessibilityService.kt   # 无障碍服务（启停状态 + 音量键）
│       │   └── PreferencesManager.kt        # SharedPreferences 坐标/状态存储
│       ├── res/layout/activity_main.xml
│       ├── res/drawable/                    # box_bg_orange.xml, box_bg_blue.xml 等
│       └── res/xml/accessibility_service_config.xml
├── build.gradle.kts       # AGP 8.3.2, Kotlin
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.5
└── .gitignore
```

## 架构

### 为什么是这个结构

vivo 手机（OriginOS/FunTouch OS）封死了无障碍服务的 `dispatchGesture` 和 `performAction(ACTION_CLICK)`，App 内无法执行有效点击。所以分成两层：

| 层 | 运行位置 | 职责 |
|---|---|---|
| **Android App** (`app/`) | 手机 | 悬浮框定位、启停状态、音量键停止 |
| **Python 脚本** (`tap.py`) | 电脑 | 通过 ADB `input tap` 执行真正的点击（绕过 vivo 限制） |

### App 组件

- **MainActivity**：授权悬浮窗 + 无障碍，开启/关闭悬浮窗，调节点击间隔滑条
- **FloatingOverlayService**：前景服务，显示 3 个浮动控件
  - 橙色「抢」框（72dp）：定位会员购/提交按钮（框1）
  - 蓝色「试」框（72dp）：定位重试按钮区域（框2）
  - 绿/红圆形「启/停」钮（56dp）：短按切换连点状态
  - 连点中（`is_running=true`）定位框 **触摸穿透**，ADB 点击才能落到下层 App
- **TapAccessibilityService**：
  - 维护 `is_running` 状态（实际点击由 `tap.py` 负责，App 不连点）
  - `onKeyEvent`：连点中按音量↓/↑ → `stopClicking()`，非连点状态放行音量键
  - `onInterrupt` / `onDestroy` 不清除连点状态（避免 uiautomator dump 误停）
- **PreferencesManager**：SharedPreferences `ticket_snap_prefs.xml`
  - `box1_x/y`, `box2_x/y`, `toggle_x/y`：悬浮框左上角（脚本 +126px 转中心）
  - `box1_enabled`, `box2_enabled`：区域开关
  - `click_interval`：点击间隔（默认 100ms，供普通模式读取）
  - `is_running`：连点状态（`commit()` 同步写入）

### Python 脚本 (`tap.py`)

| 模式 | 命令 | 行为 |
|---|---|---|
| **推荐** | `--turbo` | 框1 手机端死循环极限连点；框2 检测到「再XX」时同步极限连点 |
| 普通 | `--interval N` | 框1 恒定间隔连点；框2 智能/强制（见参数） |

- 坐标：`run-as com.ticketsnap cat .../ticket_snap_prefs.xml` 读取，自动 +126px 转中心
- 框2 触发：屏幕文字匹配 `再.`（如 **再试、再抢、再次**）
- 停止检测：后台轮询 `is_running`，从 true→false 时退出（音量键/点「停」）
- 停止方式：手机音量键 / 悬浮窗「停」/ 通知栏紧急停止 / 电脑 Ctrl+C

## 环境

- **手机**：vivo V2301A (PD2301)，屏幕 1260x2800，density 560 (3.5x)
- **JDK**：`E:\TOOL\Android\download\jbr` (JetBrains Runtime 21)
- **Android SDK**：`C:\Users\Junsh\AppData\Local\Android\Sdk` (API 34)
- **Gradle**：8.5
- **AGP**：8.3.2（vivo 兼容版本）
- **ADB**：`C:\Users\Junsh\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Python**：3.x（Windows 自带 `python` 即可）

## 构建与安装

```powershell
$env:JAVA_HOME="E:\TOOL\Android\download\jbr"
cd G:\Work\ticket-snapper
.\gradlew assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 使用流程

### 手机端（每次抢票前）

1. 打开 **TicketSnap**
2. 授予 **悬浮窗** + **无障碍服务**（两项都必须开）
3. 点 **「开启连点」** → 出现三个浮控件
4. 在 **抢票页面** 上拖动：
   - 橙色框 → 会员购 / 提交按钮（框1）
   - 蓝色框 → 「再试」类重试按钮区域（框2）
5. 点绿色 **「启」** → 变红 **「停」**
6. 保持抢票页面在前台（屏幕常亮、勿锁屏）

### 电脑端（启动连点）

```powershell
cd G:\Work\ticket-snapper
python tap.py --nowait --turbo
```

**推荐命令说明：**

| 参数 | 含义 |
|---|---|
| `--nowait` | 不等待按 Enter，立即开始 |
| `--turbo` | 框1 全程极限连点；框2 见「再XX」自动极限连点 |

### 其他命令

```powershell
# 普通模式（可调间隔，默认读 App 滑条设置）
python tap.py --nowait --interval 30

# 只框1，不要框2
python tap.py --nowait --turbo --no-box2

# 框2 不检测文字、始终连点（慎用，可能误点）
python tap.py --nowait --turbo --force-box2

# 测试固定次数
python tap.py --nowait --turbo -n 50
```

### 停止

| 方式 | 操作 |
|---|---|
| 音量键 | 连点中按 **音量+** 或 **音量-** |
| 悬浮窗 | 点红色 **「停」** |
| 通知栏 | **紧急停止** |
| 电脑 | **Ctrl+C** |

### 理想抢票时序

```
框1 TURBO 狂点会员购
    │
    ├─ 成功 → 进入下单页（手动或后续流程）
    │
    └─ 未抢到，出现「再试」等（再XX）
           └─ 框2 自动 TURBO 狂点重试按钮
                └─ 按钮消失 → 框2 停，框1 继续
```

终端框2 日志示例：

```
[box2] retry=no
[box2] retry=YES turbo ON
[box2] turbo OFF
```

## 已修复的问题

1. `settings.gradle.kts`：`dependencyResolution` → `dependencyResolutionManagement`
2. `activity_main.xml` padding / 转义 / SeekBar 默认值
3. `AndroidManifest.xml` 补 `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS`
4. AGP 8.2.0 → 8.3.2（JDK 21 jlink 兼容）
5. 坐标从左上角修正为中心点（+half_box）
6. `onKeyEvent` 非连点状态放行音量键
7. **脚本误停**：`is_running` 改 `commit()`；停止检测改 prefs 轮询；`onInterrupt`/`onDestroy` 不误清状态
8. **点击无反应**：连点中悬浮定位框触摸穿透，ADB 点击落到下层 App
9. **框2 误触 / uiautomator 干扰**：App 不再内部连点；dump 与 turbo 分线程
10. **框1 加速**：`--turbo` 手机端死循环；普通模式复用 ADB shell 连接
11. **框2 规则**：匹配「再XX」（正则 `再.`），出现则 turbo，消失则停
