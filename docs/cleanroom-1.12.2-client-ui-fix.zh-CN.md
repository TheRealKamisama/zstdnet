# Cleanroom `.3` 客户端界面修复

日期：2026-09-19。版本：`1.4.7-cleanroom.3`。

用户已确认 `.2` 实际进服成功且 Zstd 压缩率正常。本次修改两项客户端体验：

- HUD 上下行带宽各自按 1024 进位，从 B/s 自动缩放到 KB/s、MB/s 等单位；B/s 保留整数，较大单位保留一位小数。
- `/zstdnet config` 延后到下一次客户端 tick 打开配置页。Cleanroom 的 `GuiChat.keyTyped` 在 `sendChatMessage` 返回后无条件调用 `displayGuiScreen(null)`，原先同步打开的配置页被立即关闭；已从实际游戏字节码确认这一顺序。不能单纯换成 `addScheduledTask`，因为在客户端主线程调用时可能立即执行。

同时修正配置页退出行为：Done 或 Esc 返回游戏，保留已有代理连接。只有该页面主动发起的新连接尝试才会被取消，避免退出设置时意外断线。

构建命令：

```powershell
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Tasks @('build','--offline')
```

产物：`mods/1.12.2/zstdnet-cleanroom/build/libs/zstdnet-1.12.2-cleanroom-1.4.7-cleanroom.3.jar`。

构建成功，既有 29 项 JUnit 回归全部通过，生产重映射、产物完整性与独立 JVM JNI 验证通过。构建日志见 [build.log](validation/cleanroom-1.12.2-client-ui-fix/build.log)。

SHA256：`93F738CD80FFA5440B6763F60502ECDAFBFE96630A11304A4899CC97A4D13355`。

这些改动位于客户端，使用 `.2` 的服务器无需因此同步升级。替换客户端旧 ZstdNet JAR 后重启游戏，进入服务器执行 `/zstdnet config`，退出设置应继续保持连接。当前工具运行环境没有完成图形界面实测；编译和自动回归结果不等于 GUI 点击验收。
