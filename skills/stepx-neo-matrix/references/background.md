# 熄屏动画与 Neo 冻结机制

2026-10-18 PZ261A / Android16 实测：

- 临时 `setBackScreenDisplayMode(1)` 解决30秒后背屏自动熄灭。
- Android电池优化豁免、Neo“耗电和后台 → 手动管理 → 允许后台运行”仍未阻止冻结。
- 系统日志出现 `PARTIAL_WAKE_LOCK ... DISABLED ... reason=FROZEN`，服务存在但帧数不再增加。
- 通过ADB给**当前App**加入Neo冻结白名单并解冻其UID后，熄屏超过30秒帧数31→69，唤醒锁不再禁用；用户确认方框/X持续交替。

外接摄像头通过QuickTime复核独立APK：前台、返回桌面后104秒、熄屏超过2分钟均观察到方框/X切换；熄屏阶段系统Dozing且唤醒锁未禁用。后来设备已唤醒，不把后续时长计入熄屏连续验收。测试停止后恢复auto、清帧，撤销临时冻结例外并卸载独立测试App；QuickTime原有预览保留。

## 开发调试工具

ADB来自Android platform-tools。指定唯一设备序列号及真实安装包名，不硬编码示例UID：

```sh
python3 scripts/neo_background.py --serial SERIAL --package studio.jiayu.neo status
python3 scripts/neo_background.py --serial SERIAL --package studio.jiayu.neo enable
python3 scripts/neo_background.py --serial SERIAL --package studio.jiayu.neo disable
```

可用 `--adb /path/to/adb` 指定工具。`enable`/`disable`是显式操作；脚本拒绝系统UID和不存在的包，不关闭全局冻结，不改签名权限、不需要root。放行属于开发调试配置，普通App无权自行执行。固件重启、云端策略更新后的保留情况未验证；每次使用先status。不要承诺永久生效。

实际执行的固件命令为 `dumpsys activity freeze whitelist`、`whiteAdd PACKAGE`、`doUnfreeze UID`、`whiteRemove PACKAGE`。doUnfreeze参数是数字UID，不能传包名。如果返回未知命令、拒绝或缺少预期回显，停止并保留诊断；不要更改全局freeze开关。

## 验收与清理

记录放行前是否已有该例外，不撤销原有配置。开始播放后切到桌面并熄屏，至少观察45–60秒；期间不要唤醒或通过前台WebView查询，否则会污染后台证据。debug构建可读取自身私有matrix-status.xml；正式包使用App状态页，在唤醒前先完成系统诊断。`dumpsys display_ext back_screen`中的writes是状态持久化次数，不是物理帧数。

停止后核对显示模式恢复、appUsesCustomFrame=false、服务和唤醒锁退出。测试临时白名单用disable撤销，临时App卸载前也先撤销；不要撤销用户原有或明确要求保留的配置。强制停止无法执行清理回调，需重新打开App补恢复。
