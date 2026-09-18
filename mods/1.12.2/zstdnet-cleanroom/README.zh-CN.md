# ZstdNet · Cleanroom 1.12.2 / Java 25

本模块是 ZstdNet Fork 的向后移植，将兼容范围扩展至 **Minecraft 1.12.2 / Cleanroom / Java 25**。它需要 Cleanroom，不适用于传统 Forge 1.12.2 / Java 8；不同 Minecraft 版本仍不能互相联机。

新增的独立迁移模块，版本 `1.4.7-cleanroom-alpha.1`，整合此前 `.1`–`.4` 的迁移与修复。现有 1.20/1.21 平台代码未修改；共享报表模板将两处“线路／原始”改为“压缩后剩余”，绿色百分比标为“节省流量”。用户已确认实际进入服务器且 Zstd 压缩率正常；LAN 和完整整合包验收仍待完成。详见 [迁移记录](../../../docs/cleanroom-1.12.2-java25-validation.zh-CN.md)、[压缩修复验证](../../../docs/cleanroom-1.12.2-compression-fix.zh-CN.md) 和 [客户端界面修复](../../../docs/cleanroom-1.12.2-client-ui-fix.zh-CN.md)。

下载：[GitHub Releases](https://github.com/TheRealKamisama/zstdnet-cleanroom/releases)。请选择生产 JAR，升级时替换旧版 ZstdNet 文件；alpha 版本会标记为 Pre-release。

## 构建

固定官方 CleanroomModTemplate 的 Java 25/Mixin 构建方式：Gradle `9.7.0`、Unimined `1.4.36-kappa`、Cleanroom `0.6.10-alpha`、MCP `stable 39-1.12`、zstd-jni `1.5.7-7`、JUnit `6.0.3`。模板评估快照为 `935558879c66eede20591e0b21793cabcff3363b`；Mixin 配置遵循官方 mixin 分支的 `crl.dev.mixin`、`MixinConfigs` 与 `mixinRemap`。

Windows，在仓库根目录执行：

```powershell
./mods/1.12.2/zstdnet-cleanroom/build.ps1
# 如果网络必须经过 HTTP 代理：
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Proxy http://127.0.0.1:7897
```

脚本把 Temurin `25.0.4.1+1` 和辅助构建 JDK `8u504-b01` 下载到仓库 `.tools/`，校验 SHA256；仅本次进程设置 JAVA_HOME、Gradle 缓存和代理，退出时恢复。**Java 8 只供官方 MCP 合并工具使用，Mod、测试和游戏均使用 Java 25。** 不需要安装全局 Java。Gradle 分发也固定 SHA256。

其他系统自行提供 JDK 25 和 JDK 8，在模块目录执行：

```sh
JAVA_HOME=/path/to/jdk25 sh ./gradlew -Porg.gradle.java.installations.paths=/path/to/jdk25,/path/to/jdk8 build
```

产物：`build/libs/zstdnet-1.12.2-cleanroom-1.4.7-cleanroom-alpha.1.jar`。不要把 `-dev.jar` 放进生产环境。`build` 包括 JUnit、生产重映射、内嵌 JNI JAR 完整性校验和独立 JVM 原生压缩往返。测试结果在 `build/reports/tests/test/index.html`。

zstd-jni 使用 `ContainedDeps` 内嵌原始完整 JAR，**不 relocate、不最小化、不解包重组 JNI 类**。Cleanroom 提供 Gson/SLF4J/Netty，不重复内嵌。启动游戏建议加 `--enable-native-access=ALL-UNNAMED`。

## GitHub Actions 与发布

工作流为 [cleanroom.yml](../../../.github/workflows/cleanroom.yml)。`cleanroom-1.12.2` 分支推送、面向该分支的 PR，以及手动运行都会在 Windows runner 上执行完整构建、JUnit、重映射和 JNI 校验，并上传构建产物及测试报告。

发布时同步更新 `build.gradle`、`Zstdnet.java` 的 `@Mod` 和 `mcmod.info` 版本，在 `docs/releases/<版本>.md` 编写发布说明，再推送与版本一致的 `v<版本>` 标签，例如 `v1.4.7-cleanroom-alpha.1`。标签触发的构建通过后，独立发布任务从该次构建取得生产 JAR，校验 SHA256，再上传到 GitHub Releases。alpha、beta、rc 自动标记为预发布；开发 JAR 与测试驱动不会作为 Release 资产发布。

普通构建只有仓库读取权限；仅标签的发布任务获得 `contents: write`。发布使用内置 `GITHUB_TOKEN`，无需额外个人访问令牌。CI 不会启动 Minecraft 服务器或代替用户接受 EULA。

## 手动专用服

把生产 JAR 放进客户端和服务器的 `mods`。先设置后端 `server.properties`：

```properties
server-ip=127.0.0.1
server-port=25565
online-mode=false
network-compression-threshold=256
```

首次启动生成 `config/zstdnet-server.properties`，默认手动入口为 35565：

```properties
enabled=true
auto_takeover=false
listen=0.0.0.0:35565
target=127.0.0.1:25565
level=3
voice_chat_passthrough=false
network_compression_threshold=1048576
trust_proxy_protocol=false
```

防火墙只公开代理端口，后端保留回环监听。`online-mode=false` **不验证正版身份**；需要身份验证的部署必须另配可信认证方案。保留 `online-mode=true` 的加密流可以透传，但本次不提供认证桥接，也不承诺加密流的压缩收益。

代理入口支持原版状态查询/ping，协议号为 340；原始未压缩登录会被明确拒绝。客户端默认接管 Multiplayer 服务器列表和 Direct Connect，将目标指向 Zstd 入口即可；状态查询仍走原版逻辑。连接普通、无 Zstd 代理的服务器前，在 **Mods → ZstdNet → Config** 将 **Multiplayer proxy** 关闭（`config/zstdnet-client.properties` 的 `intercept_multiplayer=false`）。该开关只影响普通连接入口；Config 的 **Connect Zstd** 和 `/zstdnet connect host:port` 始终明确使用代理。IPv6 格式为 `[::1]:35565`。内部回环连接使用线程局部重入保护，不会递归创建代理；取消连接会使待完成的代理创建失效。

1.12.2 登录保留 `\0FML\0` 和额外 host 字段，转换旧迁移源中错误的 FML2/FML3 标记。客户端和后端仍须运行兼容的 1.12.2 Mod 集合；不支持跨 Minecraft 版本连接。

## 压缩、控制与统计

`network_compression_threshold` 是 Zstd 连接的**原版**压缩阈值，默认 1048576，`-1` 禁用。修改后从下一次登录开始应用；已连接玩家维持原阈值。通过原版登录压缩包及其 Netty 完成回调协商，不在 PLAY 阶段直接重建压缩管线。普通后端连接仍遵从 `server.properties`。

`.2` 同时固定登录宣告和 `NetworkManager.setCompressionThreshold` 的参数，覆盖原版匿名完成回调重新读取 `server.properties` 的路径。两个配置可以不同，无需都改成 256。旧文件缺少或错误填写 `network_compression_threshold` 时，日志明确警告采用 1048576，并指出宣告和管线使用同一值；不会为此改写旧文件。

- `/zstdnet hud`：切换本地代理统计 HUD；上下行各自按 1024 进位显示 B/s、KB/s、MB/s 等单位。
- `/zstdnet stop`：关闭本地代理及其 TCP/UDP 会话。
- `/zstdnet config`：聊天提交结束后的下一次客户端 tick 打开设置界面。设置即时保存，Done 或 Esc 返回游戏且保留当前连接；只有从此页面发起的新连接尝试才会被取消。
- `/zstdnet report [session|today|24h|7d|30d]`：通过 FML `zstdnet` 通道请求报告，需要服务端权限等级 4；每玩家至少间隔 30 秒。
- `/zstdnet-server status`、`/zstdnet-server report 30d`：服务端命令或控制台，权限等级 4。报告保存在命令执行侧的 `zstdnet-reports`，历史数据在服务端 `zstdnet-stats`。
- `/zstdnet-server compression [player]`：权限等级 4，在 Netty 线程读取该玩家实际编码器/解码器阈值，并显示预期值和是否经过本机代理；`-1` 表示压缩处理器不存在。控制台须填写玩家名。

报告最多 1 MiB，每块不超过 24 KiB，客户端检查块序号、总量及 30 秒超时。旧式 IMessage 均有公开无参构造；消息处理切回相应游戏线程。客户端报告 HTML 生成逻辑复用共享源码；历史统计包含原始/线上流量和 UDP 流量。

报表的“压缩后剩余”是 TCP 线路字节占原始字节的比例，“节省流量”百分比表示省去的部分；例如剩余 11% 对应节省 89%。`.4` 只更新这些说明文字，计算口径不变。已生成的 HTML 是静态快照，更新生成报告的一端后需要重新导出报告才能看到新文案。

## 真实 IP、自动接管、LAN 与 UDP

真实 IP 在 Cleanroom 截断握手 host **之前**捕获。仅接受本机回环连接附带的本进程随机校验标记，转发端移除客户端原有地址断言；远程独立后端不会信任另一 JVM 的标记。此功能不是身份认证。

外部代理发来 PROXY v2 时，开启 `trust_proxy_protocol=true` 并设置 `trusted_proxy_ips` 为明确的来源 IP。开启后缺失/无效 PROXY 头或不可信来源被拒绝，不适用于普通直连。上游必须使用 TCP PROXY v2；UDP 仍是原样转发。受信来源地址应填写数字 IP，IPv6 信任列表使用该系统 `InetAddress.getHostAddress()` 的规范形式。

`auto_takeover=true` 时，在 DedicatedServer 调用网络 bind 前把后端改到回环 `target` 端口，原 `server.properties` 的端口作为代理入口，原配置不改写。后端端口与公网端口相同则选择临时空闲端口；后端端口冲突会令启动失败，公网端口冲突会使代理启动失败并记录日志。端口预检查与 Netty bind 之间存在系统级竞争窗口，失败时应修正配置后重启。

LAN 使用 `IntegratedServer.shareToLAN` Mixin，在原后端已监听后启动代理，将广播端口和返回给界面的端口替换为代理端口。每次开放强制使用当前后端端口，关闭世界时停止代理。**此代码已编译，当前环境未完成真实 GUI/LAN 开房验收。** LAN 对端通过明确的 Zstd 连接入口加入。

TCP 代理端口也提供原样 UDP 转发。独立语音端口通过 `voice_chat_passthrough`、`voice_chat_listen`、`voice_chat_target` 配置，复用原版本路由/冲突判断。UDP 不经过 Zstd。限流和连接上限沿用现有代理配置。修改 listen/target/UDP 端口后重启服务器或重新开放 LAN。

## 复现运行验证

先在独立目录用官方 `cleanroom-0.6.10-alpha-installer.jar --installServer <目录>` 安装服务器，放入生产 JAR，接受 EULA 并自行启动。测试服建议只监听回环。验证脚本不会替你接受 EULA。

```powershell
# 默认连接回环 35586；测试用户为 ZstdProbe，首先应为非 OP。
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Tasks @('productionProbe','-PprobePort=35586')
# 临时测试服 op ZstdProbe 后验证报告获取：
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Tasks @('productionProbe','-PprobePort=35586','-PprobeReportAllowed=true')
# OP 测试用户：严格校验宣告与实际服务端管线；跳过报告冷却。
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Tasks @('productionProbe','-PprobePort=35586','-PprobeSkipReport=true','-PprobeCheckPipeline=true','-PprobeExpectedThreshold=1048576')
# 普通后端直连，应保持 server.properties 的 256：
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Tasks @('productionProbe','-PprobePort=25586','-PprobeRaw=true','-PprobeSkipReport=true','-PprobeCheckPipeline=true','-PprobeExpectedThreshold=256')
# 有真实图形桌面的机器上执行客户端驱动：
./mods/1.12.2/zstdnet-cleanroom/build.ps1 -Tasks @('runClient','-PsmokeHost=127.0.0.1:35586')
```

`productionProbe` 是协议测试程序：发送服务端公布的 Mod 列表、确认 FML 注册表消息、使用 Minecraft 实际 `NettyCompressionDecoder` 检查报文，接收区块并重连三次，每轮持续 PLAY 至少 5 秒；它**不会在真实 Minecraft 客户端安装注册表或渲染世界**。`runClient` 的 opt-in 驱动依次覆盖服务器列表构造器、直接连接构造器、显式 Zstd 入口，并等待真实世界区块 tick；当前环境仍因 GLFW 无主显示器而无法运行 GUI 验收。测试驱动从发行 JAR 排除。不要把探针用于实际玩家服务器。
