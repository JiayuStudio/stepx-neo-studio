# 项目与调用接口

`neo-matrix.json` 是动画唯一数据源：

- schema 为 `stepx-matrix/1`，width=17，height=23，loop为布尔值。
- frames为1–120帧，每帧 `durationMs` 是200–60000整数。
- `pixels` 恰好391个0–255整数，按 `pixels[y*17+x]` 排列；不接受小数、布尔值冒充整数。
- 文件UTF-8，不超过350000字符。建议2 FPS，最多5 FPS。

生成有效示例：`python3 scripts/matrix_json.py sample /path/to/neo-matrix.json`。

StepX Neo Studio 1.1.10 已移除工坊入口，现有项目与独立运行壳接口保留。新项目需自行提供 HTML 页面，使用下方接口。历史 1.1.9 的工坊会生成 index.html、matrix-core.js、matrix-player.js、MATRIX.md和动画JSON。AI通过已有项目修改接口更新文本文件，提供当前文件基线并保存快照。无需将模型密钥嵌入项目。上传Studio后按平台入口要求适配；上传成功不代表云端构建完成。

本机生成的运行壳只允许播放打包时的动画，更新后重新构建：

```js
NeoNative.matrix('permissions', '{}'); // 通知/标准后台申请与Neo设置入口
NeoNative.matrix('settings', '{}'); // 检测原生通道，查看备用授权
NeoNative.matrix('start', JSON.stringify({simulate:false, keepLit:true}));
NeoNative.matrix('stop', '{}');
const status = JSON.parse(NeoNative.matrixStatus());
// state/message/simulation/transport/frame/sent/time
```

`simulate:true` 只验证时钟，不发送物理帧。`sent` 表示调用返回成功的帧数，不能替代肉眼验收。运行壳bridge不是对互联网开放的HTTP端点；外部Agent应在授权的App/ADB/网页执行环境中操作。

官方网页壳中，先添加连接器 `ic_atomic_990001`、保留capsule.js，然后在前台使用：

```js
await window.mcp.connect();
const result = await window.mcp.callTool('set_back_screen_frame', {frame:pixels});
// 必须检查 result.success === false / result.isError === true
await window.mcp.callTool('clear_back_screen_frame', {});
```

官方能力授权绑定App包名与签名，不能复用其他App的key；模型API key无效。官方网页桥调用仍须按当前SDK实际返回结构验证。本轮成功的物理验收使用原生通道，不是上述MCP备用传输。
