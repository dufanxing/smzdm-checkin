# SMZDM 自动签到 Xposed 模块

基于什么值得买 v11.2.5 逆向分析（2026-11），复用宿主 App 自身网络栈发起签到，
无需账号密码，签名/token 由 App 自动处理。

## 功能

- **自动签到**：宿主 App 启动后延迟 N 秒，复用其网络引擎调用
  `POST https://user-api.smzdm.com/checkin/all_reward`
- **观察模式**：打印宿主所有 API 请求（URL + 参数），用于验证 hook 生效与协议学习
- **页面代签**：打开签到页时若今日未签则自动签（可选）
- **每日一次守卫**：状态记录在宿主 App 内 `xposed_checkin_state` SP
- **风控透出**：触发 GeeTest 验证码时 Toast 提示转手动，不重试

## 构建方式

### 方式一：Android Studio（本地）

1. 用 Android Studio 打开本目录
2. 等待 Gradle Sync 完成
3. 菜单 Build → Build Bundle(s)/APK(s) → Build APK(s)
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`

### 方式二：GitHub Actions（云端，零本地环境）

1. 将整个 `SMZDMAutoCheckin` 目录推送到 GitHub 仓库
2. 仓库 Actions 页自动运行 `build.yml`（push 或手动触发均可）
3. 构建完成后在 Artifacts 下载 `SMZDMAutoCheckin-debug`

> debug APK 已自动签名，可直接安装激活。

## 使用

1. 安装 APK
2. LSPosed → 模块 → 启用本模块（作用域已限定：什么值得买）
3. 打开模块设置页按需调整（首次建议只开观察模式）
4. 强停什么值得买 → 重新打开 → 等待延迟后自动签到
5. 查看日志：LSPosed → 日志，或 `adb logcat -s SMZDM_Checkin`

## Hook 点

| 目标 | 类（v11.2.5 混淆名） | 用途 |
|------|---------------------|------|
| 网络引擎 | `Pg.g.q(...)` | 发起请求 / 观察所有请求 |
| 回调接口 | `Pg.e{onSuccess,onFailure}` | 接收响应 |
| 签到页 | `SignInActivity.onResume` | 页面代签（可选） |

> 混淆类名随版本变化。升级后如失效，用观察模式验证：
> 稳定锚点是 URL 字符串 `checkin/all_reward`，反查新调用点。

## 风险提示

自动化工具违反平台用户协议，存在积分清零/封号风险；
保持每日一次自然频率，出现验证码请手动完成。仅供学习研究。
