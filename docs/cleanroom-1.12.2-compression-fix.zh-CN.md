# Cleanroom 1.12.2 压缩与客户端入口修复验证

日期：2026-09-18。修复版本：`1.4.7-cleanroom.2`。运行环境：Windows 11 amd64、Temurin Java 25.0.4.1+1、Cleanroom 0.6.10-alpha。

## 根因与修复

`.1` 的 `LoginCompressionMixin` 只重定向了 `NetHandlerLoginServer.tryAcceptPlayer` 内的阈值读取。原版 `NetHandlerLoginServer$1.operationComplete(ChannelFuture)` 在发送登录压缩包完成后，另行读取 `server.properties` 的阈值，再调用 `NetworkManager.setCompressionThreshold`。因此代理默认宣告 1048576，但后端配置为 256 时，实际编码器和解码器仍使用 256。

`.2` 在 `NetworkManager.setCompressionThreshold` 入口覆盖参数，仅使用已通过本进程代理标记校验的连接所保存的阈值；普通连接没有这个覆盖值，继续使用传入的后端阈值。登录宣告和实际管线共用该连接在握手时保存的值。没有把两个配置强制改成 256，也没有在 PLAY 阶段改变协议。

旧配置缺少或无效填写 `network_compression_threshold` 时，保留默认 1048576，并记录明确警告，说明登录宣告与管线同时采用该默认值。新增权限等级 4 的 `/zstdnet-server compression [player]`，在该连接的 Netty 线程读取编码器/解码器私有阈值，输出 expected、encoder、decoder、proxied。压缩处理器不存在时输出 -1。

## 修正旧验证结论

旧协议探针使用普通 zlib 解压，未执行 Minecraft 的最低阈值检查，因此 `.1` 的“多阈值连接通过”是假阳性。旧记录已明确撤回这项结论；历史日志保留，但不能证明压缩协议正确。

现在探针通过 `EmbeddedChannel` 调用 Minecraft 实际 `NettyCompressionDecoder`，按登录宣告设置阈值。以下旧版负例已实际复现：

```text
LOGIN_COMPRESSION 1048576
io.netty.handler.codec.DecoderException:
Badly compressed packet - size of 910 is below server threshold of 1048576
```

负例测试服使用 `.1` 生产 JAR，后端阈值 256，代理配置省略阈值键。严格探针尚未进入 PLAY 就失败。证据：[before-strict-decoder.log](validation/cleanroom-1.12.2-compression-fix/before-strict-decoder.log)。

## `.2` 实际生产 JAR 验证

用户明确接受 EULA 后，在本工作树 `.tools/production-server` 的临时服验证。后端一直是 `127.0.0.1:25586`、`network-compression-threshold=256`、offline-mode；代理是 `127.0.0.1:35586`，测试期间只监听回环。使用重映射生产 JAR，不是开发 JAR。

| 连接与代理配置 | 登录宣告 | 实际编码器 | 实际解码器 | 结果 |
| --- | ---: | ---: | ---: | --- |
| 代理，省略阈值键，默认 1048576 | 1048576 | 1048576 | 1048576 | 三次成功，proxied=true |
| 代理，显式 -1 | 无压缩协商 | 不存在 | 不存在 | 三次成功，proxied=true |
| 普通后端直连；此时代理仍配置 -1 | 256 | 256 | 256 | 三次成功，proxied=false |

每次均完成协议 340 状态/ping、FML 网络协商和 11 组注册表消息；每轮收到 49 个区块包，并持续 PLAY 至少 5 秒。启用压缩时所有入站包经过实际 Minecraft 解码器；禁用时按原版无压缩帧读取。探针直接比较登录宣告与命令读取的服务端实际管线。默认阈值组还验证了 OP 报告获取，另两组跳过报告以避免 30 秒冷却。

证据：

- [默认 1048576](validation/cleanroom-1.12.2-compression-fix/after-default-1048576.log)
- [代理禁用压缩](validation/cleanroom-1.12.2-compression-fix/after-disabled.log)
- [普通后端 256](validation/cleanroom-1.12.2-compression-fix/after-raw-256.log)
- [修复后服务端日志](validation/cleanroom-1.12.2-compression-fix/after-server.log)
- [原版相关字节码](validation/cleanroom-1.12.2-compression-fix/minecraft-bytecode.txt)

测试结束正常执行 `stop`，保存世界并关闭代理。没有部署到远程服务器。

## 客户端入口

客户端专用 `GuiConnectingMixin` 拦截服务器列表和直接连接共用的 `connect(String,int)`，启动一次本地代理并取消原来的直连。内部回环 `GuiConnecting` 构造使用线程局部 `InternalConnectGuard`，在 `finally` 恢复状态，支持嵌套并隔离其他线程；不会按“localhost”一概跳过代理。

原版取消按钮按尝试编号使异步创建失效，过期代理句柄被关闭。内部界面构造异常时关闭代理并显示连接失败。`intercept_multiplayer=true` 默认接管普通连接；连接没有 Zstd 的服务器时可在 Config 关闭 **Multiplayer proxy**。显式 **Connect Zstd** 和命令入口保留。

该部分通过编译、生产 refmap 映射检查和重入保护单元测试。opt-in `ClientSmoke` 已更新并编译，依次走服务器列表构造器、直接连接构造器和显式 Zstd 入口；发行 JAR 不包含驱动。**当前环境先前在 GLFW 获取主显示器时失败，早于 Mod 初始化；本次没有声称真实 GUI 点击、取消、HUD、LAN 或客户端渲染验收通过。**

## 构建与单元回归

`build --offline` 成功。JUnit 共 29 项，零失败、零跳过：原迁移 25 项，加 2 项实际 Minecraft 压缩编解码测试和 2 项重入保护测试。

压缩负例使用编码器 256 / 解码器 1048576，必须拒绝 4096 字节报文；一致阈值测试覆盖 256、1048576，以及 128、256、4096、1048576 字节负载。重入测试覆盖嵌套、异常恢复和另一线程不继承绕过标记。

`verifyArtifact` 与 `verifyPackagedNative` 均通过：生产 refmap 存在、完整 JNI 子 JAR 与上游字节一致、测试驱动未打包、提取的 JNI 在独立 Java 25 进程中成功压缩/解压。证据：[构建日志](validation/cleanroom-1.12.2-compression-fix/build.log)、[JUnit 摘要](validation/cleanroom-1.12.2-compression-fix/junit-summary.json)。

生产产物：`mods/1.12.2/zstdnet-cleanroom/build/libs/zstdnet-1.12.2-cleanroom-1.4.7-cleanroom.2.jar`

SHA256：`85252330CBB645BD5B6D90C41475B973847E2BAB37227AD8EE3530B8999C7D93`

复现命令见 [模块 README](../mods/1.12.2/zstdnet-cleanroom/README.zh-CN.md)。协议探针不安装客户端注册表、不渲染世界，结果不等于真实整合包客户端验收；图形桌面、LAN、长时间负载和其他系统的剩余限制仍适用。
