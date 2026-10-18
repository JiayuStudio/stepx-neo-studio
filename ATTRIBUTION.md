# 来源、实际复用与修改说明

本项目为面向 STEPX Neo 的独立第三方客户端，不是 Step 官方产品。以下按实际使用情况列出；“参考”不等于复制代码。

## ra1nzzz/stepstudio-apk：产品思路与实机对照

- 原仓库：https://github.com/ra1nzzz/stepstudio-apk
- 对照版本：1.0.0 APK；源代码核查基线 main `e7c2d422a5e7871dc53cfb41974122b185f71bea`。
- 具体参考：通过 Android WebView 打开 `https://studio.stepx.com/` 的客户端思路，以及官方 Studio 的登录和网页构建入口。本次安装原 APK，对照确认上下黑色系统栏、权限处理及浏览器功能缺口。
- **没有复用**：上游 Java 源文件、Manifest、应用图标、签名密钥、二进制代码或包名。Neo Studio 原生客户端和本地工作台为本项目独立实现。
- 原仓库核查时未发现 LICENSE，因此未将其代码作为可自由复制的依赖。上游 APK 只用于本次私有研究对照，不随源码包、Git 仓库或本项目发布包分发。
- 本项目增加：Neo Insets/键盘/旋转适配、文件与下载、本地项目、多个 AI API、模型检测、Token 统计、共享项目交接，以及离线 Web 项目 APK 打包和调试接口。后两项的完成与实机验证状态以开发日志为准。

## apksig-android 4.4.0：实际代码依赖

- 仓库：https://github.com/MuntashirAkon/apksig-android
- 上游：https://android.googlesource.com/platform/tools/apksig/
- 用途：在 Android 设备上为本地生成的 APK 签名，使用公开 ApkSigner API。官方 apksig 原项目面向设备外使用，本项目选用其 Android 移植版。
- 来源：JitPack 4.4.0 AAR 中的 classes.jar，保存在 `vendor/apksig-android-4.4.0.jar`；由本项目 D8 构建转为 DEX 并随客户端分发。
- 许可：Apache License 2.0，保留 `vendor/apksig-LICENSE.txt` 中的完整许可和版权信息。
- JAR SHA-256：`530fcc4384889108bf85b4c816cfe354604dd38654b30d81fc046a817e1c10e0`，也见 `vendor/SHA256SUMS`。
- 修改：未修改该库源代码。包装调用、项目 ZIP 生成、Manifest 字符串调整、签名密钥生命周期和界面均为本项目自己的实现。
- 维护核查：仓库最后推送为 2023-05-01；不能仅以依赖声明推断兼容性，设备验证单独记录。

## 评估但没有复制代码的候选

- https://github.com/delight-im/Android-AdvancedWebView ：MIT；作为 WebView 功能组件候选评估，当前未引入。
- https://github.com/intplayed/snake-game ：参考移动贪吃蛇的功能范围。README 自称 MIT，但 GitHub 未识别仓库许可证，因此没有复制代码或资源；贪吃蛇初稿由用户指定的 DeepSeek API 生成，本项目审查修正，Step 后续接续情况见验证记录。
- https://github.com/webrtc/samples ：BSD-3-Clause；作为摄像头/麦克风测试范式候选，当前没有复制源码。功能实验室使用标准 Web API 与本项目原生运行壳。

## 官方规范与接口资料

- Android edge-to-edge：https://developer.android.com/develop/ui/views/layout/edge-to-edge
- WebView 权限：https://developer.android.com/reference/android/webkit/PermissionRequest
- Android Keystore：https://developer.android.com/privacy-and-security/keystore
- Step 文档索引：https://platform.stepfun.com/docs/llms.txt
- Step Studio：https://studio.stepx.com/ （官方网页继续由官方服务提供，本项目未复制其前后端实现）
- DeepSeek API：https://api-docs.deepseek.com/zh-cn/
- DeepSeek 推理模式：https://api-docs.deepseek.com/guides/thinking_mode/

云端 API 按文档独立实现协议适配。端侧推理使用下文列出的 LiteRT-LM 运行时。更多开发入口与验证边界见 `官方开发资料.md`。

## 图形与测试资产

- Neo Studio 主图标由本任务的 imagegen 工具生成，提示保存在 `artwork/prompt.txt`；没有复用 Step 商标或上游图标。
- 离线生成 App 的默认图标由本项目 Android Canvas 代码绘制；项目名称参与生成。它是占位开发图标，用户可在后续版本中提供正式品牌资源。
- 本地验证项目中的外部 AI 输出和 Step 云端生成结果，分别记录生成者、后续修改与实测结果，不伪称为原仓库已有功能。

## Apple Liquid Glass：设计参考

- [Materials](https://developer.apple.com/design/human-interface-guidelines/materials)、[Adopting Liquid Glass](https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass)、[Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)：参考控件和导航层、内容可读性、适度材质的原则。
- 实际实现为本项目自己的 CSS backdrop-filter、渐变、描边，以及 Android GradientDrawable 胶囊导航；原生导航不是实时折射/模糊，也没有移植 UIKit/SwiftUI/AppKit。没有复制 Apple 图标、SF 字体、素材或框架代码，不宣称 Apple 官方认证。

## Amoo 系统分享交接

- 使用 Android 标准 ACTION_SEND / text/plain，指定设备已安装的 com.stepos.ailauncher；经原生预览后打开其公开分享入口。没有复制 Amoo 源码或引入私有 SDK。
- 2026-10-18 实机通过可见会话发送只读飞行模式查询，Amoo 真实执行并返回关闭；回执由人工核对后写回项目。没有验证后台自动执行或结果回调协议。

## 阶跃 AI 与 Studio 手机布局：1.1.1 参考范围

- 实机参考：Neo 已安装的阶跃 AI（包名 cn.yuewen.ywapp），只读打开首页，观察炭黑背景、灰色输入区、橙色强调和浅色文字。未复制其布局代码、图标、字体文件或图片。产品入口：https://chat.stepfun.com/ 。
- Studio 的公开已渲染 DOM 用于识别开发对话、消息输入、发送与完成回执；`studio-link.js` 为本项目独立实现，不调用私有接口。网页结构变化可能导致停止。
- `studio-adapt.js` 仅对手机宽度下的已展开对话设置满宽；保留官方遮罩与对话关闭机制，避免原布局预留16px露出磨砂条。未复制官方前端包。
- 本轮没有新增第三方代码依赖。仍保留上述原仓库、Android、Apple、DeepSeek及apksig来源说明。

## 1.1.2 图标更新

使用内置 image_gen 编辑本项目自有旧图标，保留代码括号轮廓，移除金属硬件元素，采用石墨灰和橙色浅浮雕；未使用Step官方商标或图形资源。提示与资产部署说明见artwork/prompt.txt。旧图标保留在历史版本。界面和图标仅参考此前已在Neo实际查看的阶跃AI配色与简洁层级，不宣称官方设计认证。

## 1.1.3 更新器

- GitHub官方Release/资产REST API、Android PackageManager与安装来源授权：协议与平台接口参考；原生更新器独立实现。见更新说明链接。
- [XUpdate](https://github.com/xuexiangjys/XUpdate)，Apache 2.0：评估其Android更新能力，本项目未复制代码、未引入依赖；现有小型原生客户端使用固定仓库和SHA-256/签名校验。
- 既有apksig-android 4.4.0现同时用于下载APK密码学验签，许可证保持原声明。

## 1.1.4 项目最近删除

评估 [TeamAmaze/AmazeTrashBin](https://github.com/TeamAmaze/AmazeTrashBin)（Apache-2.0）的移动、恢复、彻底删除设计。该库面向文件系统与独立元数据，本项目是AtomicFile内JSON工作区，为保留项目协作对象的一致性而独立实现，不引入或复制该库代码。维护状态与评估日期记录于开发日志。

## 1.1.5 项目写入与 Studio 用量

- [Aider](https://github.com/Aider-AI/aider) 的 [edit formats](https://github.com/Aider-AI/aider/blob/main/aider/website/docs/more/edit-formats.md) 用于评估整文件与增量修改协议。Apache-2.0，2026-10-18 查询时未归档、最近推送2026-05-22。本项目选用小型根目录 Web 项目的整文件 JSON，原生目标校验、冲突检测、快照和保存均独立实现，未复制 Aider 代码、未引入 Python 依赖。
- Studio 用量检查参考设备上官方前端实际发起的 `/api/projects/{id}`、`/sessions`、`/history?beforeSeq=...&sessionId=...` 只读请求及 `payload.subagentUpdate.totalTokens` 字段。未复制前端 bundle、未提取或导出 Cookie/登录密钥。该站内协议不是公开稳定 SDK，仅在用户已登录的官方 WebView 同源执行；只保存项目名称、数字字段、检查时间和状态。0 字段不作为可确认消耗，部分子任务数不能视作总账。

## 1.1.6 项目日志用量参考

- [ccusage/ccusage](https://github.com/ccusage/ccusage)：2026-10-18 查阅活跃仓库与 [MIT 许可](https://github.com/ccusage/ccusage/blob/main/apps/ccusage/LICENSE)，参考“分析 SDK JSONL 使用记录、分离缓存字段”的思路。未复制、打包或安装其代码；本项目独立实现受限目录扫描、流式去重、工具结果校验和 Android 读取流程，避免引入手机不需要的 CLI 与依赖。
- [Anthropic prompt caching 字段定义](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)：用于理解兼容 SDK 中 input_tokens / cache_read_input_tokens / cache_creation_input_tokens 的分离关系。实际数据来自 Studio 项目自己的日志，不代表 Anthropic 为 Studio 计费，也不据此推断阶跃套餐扣减。
- Studio 站内项目/会话/工具结果结构与 SDK 日志路径来自当前账号授权范围内的实机观察，非官方公开稳定 API 合约。只保留统计数字和范围，不将账号导出、系统 APK、密钥或原始对话打包到公开仓库。


## 1.1.7 源码同步与功能开关

- [Step Studio 官方网页](https://studio.stepx.com/) 的项目列表、文件读写、沙箱就绪和只读工作区状态请求是接口适配依据。独立实现传输、SHA-256 回读、文件级三方比较、二进制保留与 Android 设置；没有复制官方前端代码或打包其 bundle，不导出登录凭据。这些站内接口不是官方公开稳定 SDK。
- 评估 [bhousel/node-diff3](https://github.com/bhousel/node-diff3)（[MIT](https://github.com/bhousel/node-diff3/blob/main/LICENSE.md)）的文本三方合并方式。本版选择文件级哈希比较：两边同时改同一文件即提示冲突，不进行行级自动合并。未复制或引入该库，无新增二进制依赖。

## 1.1.8 本地运行的实际复用

- [esbuild](https://github.com/evanw/esbuild) / esbuild-wasm 0.28.2（MIT）：实际内置浏览器 JS/WASM 编译器，在 Neo 编译项目。依据[浏览器 API](https://esbuild.github.io/api/#browser)和插件 API 独立实现虚拟文件系统，未复制第三方沙盒应用代码。
- [jit-browser-tailwindcss](https://github.com/mhsdesign/jit-browser-tailwindcss) 0.4.2（发布包声明 MIT，作者 Marc Henry Schultz）：实际复用浏览器 Tailwind 3 处理器。上游包和仓库根未附独立 LICENSE 文本，保留其许可声明与已有 Tailwind 通知。并非 Tailwind 4 或任意 PostCSS/Vite 插件执行器。
- 实际内置 React/ReactDOM、React Router、Lucide、clsx、tailwind-merge、Supabase JS、xgplayer，以及编译器所需的 PostCSS/semver 等依赖。版本、锁定下载源及完整第三方许可见 [构建依赖](tooling/local-runner/package.json)、[锁文件](tooling/local-runner/package-lock.json)和[随包许可](assets/local-vendor/LICENSES.txt)。Lucide 为 ISC，其余按各包声明保留 MIT 等许可，未使用这些项目的品牌图标作为本 App 图标。
- `tooling/local-runner/build.mjs` 可重建预打包依赖；用户项目在手机上编译。postcss-selector-parser 固定到修复版本 7.1.6；npm 安装树剩余 braces 告警位于 Tailwind 的构建文件扫描链，该链未进入浏览器 bundle（输入清单随包保留）。

## 1.1.9 背屏点阵开发

- [Step Studio 背屏连接器](https://studio.stepx.com/connectors) `ic_atomic_990001`：17×23像素、亮度范围、帧和清帧工具与官方Web桥调用约定。未复制官方前端实现。
- Neo已安装系统/官方壳中的设备MCP交互用于核对握手provider、loopback JSON-RPC、响应nonce、包名及签名授权限制；客户端代码独立实现。系统APK、反编译源码、官方SDK二进制、应用能力凭据均不随仓库或交付包分发。这些观察接口并非官方公开稳定SDK合约。
- [Android前台服务](https://developer.android.com/develop/background-work/services/fgs/service-types)和[Doze说明](https://developer.android.com/training/monitoring-device-state/doze-standby)：实际使用平台Service/Notification/WakeLock API，没有复用第三方后台保活库。
- 本轮检索Neo背屏相关开源实现，未找到可确认许可且适配官方授权的现成原生后台方案。画布、动画协议、校验器和播放器均为本项目新增代码。

- Neo固件 `display_ext` / `StepOSDisplayManager` 及单App冻结诊断命令来自用户设备上的接口观察。MatrixDevice等适配代码独立实现；已验证普通App原生显示，不需要复用官方MCP能力凭据。AI技能与工具脚本为本项目编写，相关实测条件见 skills/stepx-neo-matrix。


## 1.1.10 端侧模型与官方连接器

- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) 0.18.0 / [Android 接入文档](https://developers.google.com/edge/litert-lm/android)：实际复用 Google 的 Android 运行时、Engine/Conversation API、arm64 JNI 和 BenchmarkInfo。模型管理、文件校验、采样参数、工具循环、UI和项目暂存由本项目实现。
- [Gemma 4 E4B LiteRT-LM 模型卡](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) 与 [官方生成参数](https://huggingface.co/google/gemma-4-E4B-it/blob/main/generation_config.json)：用于下载来源、Android包32K上限和温度1/Top-K64/Top-P0.95参数。模型权重单独下载，不随APK或源码分发；模型卡声明Apache-2.0，实际使用仍需核对所选文件的许可。
- 实际运行依赖：Gson 2.14.0、Kotlin stdlib/reflect 2.4.0、kotlinx-coroutines Android/core 1.11.0、JetBrains annotations 23.0.0；许可文本见 [随包许可](assets/licenses-local-ai.txt)。来源和SHA-256锁定于 [依赖锁](vendor/local-ai.lock.json)，`prepare-local-ai.py`校验后下载。R8/D8 9.5.26仅作构建工具，不随APK分发。
- [GitHub REST 搜索与内容API](https://docs.github.com/en/rest/search/search#search-repositories)：实际用于开发前检索候选仓库、许可证和少量相关源码；检索结果不等于已复用。生成项目如采用参考代码，应保留对应版权、许可、文件与固定提交来源。
- [Step Studio 连接器目录](https://studio.stepx.com/connectors)：实际纳入公开连接器名称、用途、能力说明、适用构建器和配置要求，来源保留到每项。查询与同步客户端独立实现，未复用官方网页代码、图标或账号连接资料。该站内公开接口可能变化；完整说明见 [连接器目录](连接器目录.md)。
