---
name: stepx-neo-matrix
description: 开发和控制 STEPX Neo 手机的 17×23 背屏点阵，生成动画项目与独立 Android App，诊断常亮和熄屏刷新。适用于 Studio、AI 助手或其他 Agent 的 Neo 背屏开发，不用于一般手机屏幕自动化。
---

# StepX Neo 背屏点阵

先识别目标手机、固件、目标 App 和执行环境。2026-10-18 在 PZ261A / Android 16 上，以普通 App 身份验证了原生逐帧显示、临时常亮、停止恢复；用户已确认熄屏后方框/X持续交替。该后台结果依赖当前 App 的 Neo 冻结例外。固件升级或换机后重新检测，不把接口存在或帧计数当成物理显示验收。

## 选择通道

- **在 StepX Neo Studio 中开发**：创建「背屏点阵工坊」项目，使用已有的项目文件编辑通道修改 `neo-matrix.json`。读项目 `MATRIX.md`，保存时带最新文件基线；通过本机构建生成独立 APK，或上传 Studio 继续开发。读 [动画与运行壳接口](references/project-api.md)。
- **开发自己的原生 App**：读 [原生接口](references/native-api.md)。通过 `Context.getSystemService("display_ext")` 取得管理器，普通反射调用公开方法。无需模型 API Key、云端凭据、系统签名或 root；不要使用隐藏 API 豁免。接口不可用时报告具体限制。
- **熄屏/后台调试**：读 [后台与验收](references/background.md)。本技能附带的 `scripts/neo_background.py` 通过用户已连接的 ADB 查询/单 App 放行/撤销，不修改全局冻结策略。普通 App 无法自行执行该 shell 操作。
- **官方 Studio 网页壳**：用已配置的 `ic_atomic_990001` 连接器与平台 MCP bridge；保留平台 bootstrap。该平台路径与本机原生后台路径不同，不复制其他 App 的能力凭据。功能范围及权限差异见 [通道对比](references/channels.md)。

## 执行与验收

1. 先读取目标项目和当前背屏模式，避免覆盖用户文件；一个时间只运行一个背屏播放器。
2. 通过 `scripts/matrix_json.py validate FILE` 验证动画，或 `sample FILE` 生成方框/X测试动画。`sample` 不覆盖已有文件。
3. 由用户可见界面启动播放器，保留通知和停止入口。临时常亮需保存旧模式，停止恢复；异常退出后再次打开 App 时恢复。
4. 分开记录：源码/构建、实际安装、前台物理显示、熄屏超过原30秒超时的持续动画。检查系统 `mainScreenOff=true`、计数随时间增长，最后通过用户观察或已连接的外部摄像头核验背面图案是否变化。
5. 停止、清帧、恢复模式，卸载本次临时 App。撤销仅用于测试的后台例外；用户要求长期保留的开发配置应明确记录。不要更改用户自行授予的其他权限。

本技能提供开发方法和工具，不代表任意聊天模型都具有手机执行权限；没有 ADB、原生 App 或官方 MCP 执行器时，先产出项目文件与可复核代码。
