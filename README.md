# UberEats Profit Floating Calculator

安大略 Uber Eats 司机用的悬浮窗接单决策工具。3 秒内看清一单是不是烂单。

## 功能

- 悬浮球常驻屏幕，不需要切换 app
- 输入 **收入 / 公里 / 分钟** → 立刻出 Go/Hold/No-Go
- 默认参数针对 Tesla（每公里成本 $0.13），油车可自行调整
- 全本地计算，无网络/无数据上传

## 决策逻辑

| Verdict | 条件 |
|---|---|
| 🟢 GO | 单价 ≥ `minPayout` 且 `$/km ≥ perKm` 且 `$/min ≥ perMin` |
| 🟡 HOLD | 部分达标（Quest 冲刺期可接） |
| 🔴 NO | 单价不足，或 `$/km` / `$/min` 低于门槛的 70% |

默认阈值：`$/km = 1.00`、`$/min = 0.40`、`minPayout = 4.50`、`costPerKm = 0.13`

## 如何拿到 APK（零本地安装）

1. **新建 GitHub 仓库**（私有或公开都行）
2. 把整个项目推上去：
   ```bash
   cd UberEatsProfit
   git init
   git add .
   git commit -m "initial commit"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/<仓库名>.git
   git push -u origin main
   ```
3. 推完打开 GitHub 仓库 → **Actions** 标签 → 等 ~3 分钟
4. 点进那次 workflow run → 页面底部 **Artifacts** → 下载 `UberEatsProfit-debug-apk.zip`
5. 解压得到 `UberEatsProfit-debug.apk`，传到手机安装（需要允许"未知来源"）

## 手机安装后

1. 打开应用 → 点"启动悬浮窗"
2. 系统会弹出"允许在其他应用上层显示" → 打开开关 → 返回
3. 再点"启动悬浮窗" → 屏幕左侧出现一个绿色 `$` 悬浮球
4. Uber 派单来了，看一眼金额/公里/分钟 → 点悬浮球 → 输入 → 看结果
5. 不想用时在 MainActivity 点"关闭悬浮窗"，或直接划掉状态栏通知

## 本地修改代码

- 用 Android Studio 打开项目根目录，或 IntelliJ IDEA Ultimate
- 改完 commit + push，GitHub Actions 会自动重新构建一个新 APK

## 项目结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/jieyi/ubereats/
│   ├── MainActivity.kt        # 主界面：启停服务 + 显示阈值
│   ├── SettingsActivity.kt    # 阈值设置页
│   ├── FloatingService.kt     # 悬浮球 + 计算面板
│   ├── Calculator.kt          # 接单决策逻辑
│   └── Prefs.kt               # SharedPreferences
└── res/
    ├── layout/                # activity_main, activity_settings, floating_bubble, floating_panel
    ├── drawable/              # bubble / panel / verdict 背景
    ├── values/                # strings, colors, themes
    └── mipmap-anydpi-v26/     # 启动图标（adaptive icon）
```
