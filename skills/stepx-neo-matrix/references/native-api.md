# Neo 原生显示接口

来源：2026-10-18 对用户设备固件接口的实机观察；实现源码见 [neo-studio shared-src](https://github.com/JiayuStudio/stepx-neo-studio/tree/main/shared-src/studio/jiayu/matrix)。这是固件接口，不是官方承诺稳定的公开 SDK。仓库不分发设备 APK、反编译源码或官方能力密钥。

可在 Android Activity / Service 中调用：

```java
Object display = context.getSystemService("display_ext");
if (display == null) throw new IllegalStateException("Neo display_ext unavailable");
Class<?> type = display.getClass();
boolean enabled = (Boolean) type.getMethod("isBackScreenEnabled").invoke(display);
if (!enabled) throw new IllegalStateException("请先开启系统背屏显示");
int originalMode = (Integer) type.getMethod("getBackScreenDisplayMode").invoke(display);
// 先持久保存 originalMode；0=自动，1=常亮，2=自定义时间。
type.getMethod("setBackScreenDisplayMode", int.class).invoke(display, 1);
int[] pixels = new int[17 * 23]; // 每个元素0..255，pixels[y*17+x]
pixels[11 * 17 + 8] = 255;
type.getMethod("setBackScreenAgentFrame", int[].class, android.os.Bundle.class)
    .invoke(display, pixels, null);
// 完成时由同一 App 停止发送并清帧：
type.getMethod("clearBackScreenAgentFrame").invoke(display);
// 若当前仍是本次设置的常亮，恢复；用户期间改成其他模式则保留用户选择。
int currentMode = (Integer) type.getMethod("getBackScreenDisplayMode").invoke(display);
if (currentMode == 1) type.getMethod("setBackScreenDisplayMode", int.class)
    .invoke(display, originalMode);
```

示例省略完整生命周期；正式实现参考 MatrixDevice / MatrixService：旧模式持久保存后再修改，服务停止时恢复，强制停止后的下次启动补恢复。一次只运行一个播放器，否则全局显示模式和帧所有权会相互影响。

发送成功只是系统接受；总开关、省电、温控、其他高优先级背屏内容仍可能影响物理显示。反射异常需给出原因，不能静默改成模拟后宣称成功。

后台使用 `specialUse` 前台服务、用户可见通知和停止操作，串行定时发送；以有超时且续期的 PARTIAL_WAKE_LOCK 支撑动画时钟，停止释放。无需屏幕常亮锁，也不应播放无声音频保活。用户强制停止/重启后不自动启动。

相关平台文档：[前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[WakeLock](https://developer.android.com/reference/android/os/PowerManager.WakeLock)。
