# Cleanroom / Java 25 迁移实施与验证记录

> 修订：以下是 `.1` 的历史验证记录，不能作为压缩正确性的依据。旧探针只做 zlib 解压，没有执行 Minecraft 的最低阈值检查，漏掉了“宣告 1048576、实际管线 256”的错误；原第 4 项压缩结论撤回。`.2` 修复、原版解码器负例和实际管线比对见 [修复验证记录](cleanroom-1.12.2-compression-fix.zh-CN.md)。`.2` 也新增普通 Multiplayer/Direct Connect 接管，取代下文旧入口限制；真实 GUI 验收仍未完成。

日期：2026-09-18。源码基线 `c9161fbe7c90cd33f50b73fb4e73d4e9b179dd47`。新增模块 [zstdnet-cleanroom](../mods/1.12.2/zstdnet-cleanroom/README.zh-CN.md)，版本 `1.4.7-cleanroom.1`。评估报告已复制到本工作树，作为前置假设记录；本文件区分实现、实际运行结果与尚未验证的部分。

## 阶段状态

| 阶段 | 实施结果 | 验证状态 |
| --- | --- | --- |
| 1：工具链、构建、JNI | 官方 Java 25/Mixin 模板结构；固定 Cleanroom/Unimined/Gradle/JNI/JUnit；私有工具链脚本；原始完整 JNI 子 JAR；生产重映射及 refmap | Windows x64 Java 25 构建、测试、最终 JAR 原生加载通过。Linux 原生运行未测 |
| 2：基础代理和握手 | 复用 TCP、UDP、协议、限流和统计核心；旧 FML 标记；协议 340；客户端连接入口；专用服手动 listen/target | 生产服务端成功启动；状态/ping、真实服务器 FML 网络握手、注册表报文、区块包、三次重连通过。真实游戏客户端因显示器不可用未进入游戏 |
| 3：平台功能 | 旧 IMessage 报告通道、权限与分块；登录压缩阈值；提前捕获 host/真实 IP；PROXY v2；专用服自动端口接管；LAN 广播端口；UDP；HUD、客户端配置和命令、统计报告 | 服务端控制/报告权限、压缩组合和自动接管经过生产探针验证。HUD、配置 GUI、真实客户端取消/重连及 LAN 广播只完成实现与编译，不能视作 GUI 运行通过 |
| 4：回归与产物 | 移植 UDP/语音、IPv6 地址、统计与报告测试；增加协议、信任边界、分块、实际双向流和生命周期回归 | 25 项 JUnit 通过；最终子 JAR 完整性与独立 JVM JNI 往返通过。完整整合包、长时间压力、跨维度、跨平台仍待测 |
| 5：交付文档 | 构建/使用、认证和压缩前提、复现探针、功能边界、证据文件 | 已写入模块 README 与本记录；未发布、推送或合并 |

这是迁移预览产物，尚不满足原评估中全部发布验收条件。

## 工具链与产物

- Temurin Java `25.0.4.1+1`，Windows 11 amd64。游戏、编译与测试均使用此 JVM。
- 官方 MCP 合并步骤实际要求辅助 Java 8；下载并校验 Temurin `8u504-b01`。此需求来自实际首轮构建错误，评估报告未覆盖。
- 工具链与 Gradle 缓存位于工作树 `.tools/`，没有修改系统 JAVA_HOME/PATH。
- Gradle `9.7.0`、Unimined `1.4.36-kappa`、Cleanroom `0.6.10-alpha`、MCP `39-1.12`、zstd-jni `1.5.7-7`。
- 构建依赖中的 Gson `2.14.0`、SLF4J `2.0.18`由固定 Cleanroom 提供。JUnit 运行时另外显式声明，因为 Unimined 的游戏运行库并未自动进入测试进程。
- 生产产物：`mods/1.12.2/zstdnet-cleanroom/build/libs/zstdnet-1.12.2-cleanroom-1.4.7-cleanroom.1.jar`。
- `verifyArtifact` 检查 `ContainedDeps`、refmap、测试驱动未打包，且子 JAR 字节与 Maven 原件一致。`verifyPackagedNative` 只把提取的子 JAR 和探针类放入独立 Java 25 进程，进行 1 MiB 随机数据 Zstd 流往返，输出 `PACKAGED_JNI_OK`。
- [证据目录](validation/cleanroom-1.12.2-java25/) 保存最终摘要、构建记录、协议探针输出和 GUI 失败原因。构建产物和 `.tools` 未加入版本控制。

## 已执行的测试

JUnit 25 项、零失败：

| 测试组 | 数量 | 关键覆盖 |
| --- | ---: | --- |
| TrafficReportGenerator | 3 | HTML 内嵌资源、连接地址、各服务器归档保留 |
| TrafficStatisticsService | 1 | TCP/UDP 聚合及中断会话恢复 |
| ReportAssembly | 2 | UTF-8 跨块边界、乱序和超限拒绝 |
| Handshake | 2 | FML/FML2/FML3 转换、附加字段、协议/端口/status 保留 |
| LocalZstdNetUdpPassthrough | 2 | UDP 往返及不同客户端会话隔离 |
| ForwardedAddress | 1 | 本进程校验标记、回环来源、拒绝远程伪造和域名地址 |
| ProxyBoundary | 2 | 公网入口占用/释放重启；PROXY v2 有效头、可信/不可信来源 |
| ProxyIntegration | 2 | IPv4 和 IPv6 真实 Socket、JNI Zstd 双向 2 MiB+317 字节随机内容、各三次重连、TCP 端口释放 |
| ServerProxyRuntimeVoiceChat | 10 | 复用原语音路由、IPv6/地址解析与端口冲突策略 |

旧报告测试包含很多对历史装饰 CSS 的字符串断言，与当前共享模板不一致；新模块保留报告数据/离线资源/归档契约测试，移除了这些样式断言，没有修改共享 HTML 或其他版本的测试。

### 实际生产服务器

使用官方安装器在 `.tools/production-server` 安装 Cleanroom，放入**重映射生产 JAR**；用户明确授权后设置临时服务器 `eula=true`。只绑定本机回环，不向公网公开测试服。

实际结果：

1. Cleanroom 识别 Mod，提取 `zstd-jni-1.5.7-7.jar`，JNI 初始化往返成功，服务器到达 `Done`；专用服未加载本模块 GUI 类。
2. 手动入口 `127.0.0.1:35586 → 127.0.0.1:25586` 可返回状态 JSON、正确的 `protocol:340` 和 ping 回包。
3. 协议探针经过**客户端本地代理 → Zstd 流 → 服务端代理 → Cleanroom 后端**，完成 FML hello、Mod 列表、11 组注册表消息与确认序列。服务端记录 `Server side modded connection established`、`logged in`、`joined the game`；探针接收到实际区块包。
4. **历史假阳性，结论撤回：** 旧探针在 `1048576`、`256`、`-1` 三组均完成三次连接，但只解压数据，未检查压缩包是否低于宣告阈值，也未读取服务端实际编码器/解码器阈值。这不能证明真实 Minecraft 客户端能成功连接。新严格探针对 `.1` 明确失败。
5. 非 OP 报告请求收到权限拒绝；OP 请求经旧式 IMessage 编解码成功返回有效 schema 1 报告（当时约 19 KiB）。实际返回一块；多块边界有单元测试，但尚未在大整合包真实流量报告中验收。
6. `/zstdnet-server report 30d` 在服务器文件系统生成 HTML，证明共享历史统计/报告生成已接入生命周期。
7. 自动模式公网 `127.0.0.1:25586`、后端 `127.0.0.1:25587`，启动日志确认接管；`server.properties` 仍保留公网端口；自动模式下 -1 阈值的报告、FML 协商和三次重连通过。
8. 多次正常执行 `stop`，日志确认代理停止和世界保存。单元测试另外验证 TCP 监听端口能够重新绑定。

探针复用了服务端状态公开的 Mod 列表并确认注册表消息，**没有在 Minecraft 客户端安装注册表或验证各 Mod 的客户端状态**。因此上述结果是完整服务端网络状态机与传输证据，不等价于完整整合包客户端验收。探针关闭连接时，部分轮次服务端出现 Windows socket 已被中止的日志；发生在成功接收区块后主动结束连接阶段，并未造成下一次重连失败。

### 客户端实际启动尝试

执行了 `runClient -PsmokeHost=127.0.0.1:35586`，使用 Java 25 和官方客户端依赖/资源。失败于：

```text
Minecraft.setWindowIcon → org.lwjgl.opengl.Display.<clinit>
Caused by: NullPointerException
org.lwjgl.glfw.GLFW.glfwGetVideoMode
```

该环境无法得到 GLFW 主显示器，失败早于 Mod 初始化。不能由生产服或源码检查推导 GUI、实际进服、HUD、客户端配置页面、LAN 或跨维度运行成功。保留了可在真实图形桌面重跑的 opt-in `ClientSmoke`，发行 JAR 明确排除此驱动。

## 关键设计与剩余限制

- Cleanroom 在 `C00Handshake.readPacketData` 末尾截断 host；Mixin 在 `PacketBuffer.readString` 返回时保存完整字段，实际生产网络握手已通过。
- 真实 IP 只信任本进程的回环代理，附带进程随机标记并清理用户已有地址断言。跨进程/远程后端不支持本机制；PROXY v2 的数字地址信任配置见 README。
- 原版压缩阈值在登录时一次协商。**没有移植 PLAY 阶段在线切换压缩**；改配置后新连接生效，避免拆装 Netty 管线时序问题。LAN 也使用标准登录压缩协商。
- 原版服务器列表查询可用，但不会自动识别并接管所有普通服务器列表连接。客户端须使用 ZstdNet 连接界面/命令；UI 布局与现代模块不同。
- LAN Mixin/广播和集成服生命周期已实现，真实开房、重复开放、多人进入、广播发现、GUI 点击与取消仍需图形桌面验证。
- Windows x64 已测；Linux x64、macOS、ARM、Android 原生加载没有运行证据，不作兼容承诺。
- 未进行小时级持续传输、代表性整合包、多并发性能、CPU/堆外内存、延迟对比、外部 frp 端到端 PROXY v2、跨维度和公网网络测试。
- 正版认证没有桥接实现。测试服使用 offline-mode 且仅绑定回环；正式部署需遵循 README 的后端隔离及认证前提。
- 官方模板/Unimined 会输出 Gradle 10 弃用提示；当前固定 Gradle 9.7 构建成功，不承诺升级 Gradle 10。
- 官方 Cleanroom 在仅装 ZstdNet 时提示缺少可选兼容补丁 Fugue/Scalar；测试用 `-Dfml.queryResult=confirm` 继续。这不是整合包兼容性验证，也没有更改全局 Cleanroom 配置。

后续优先在有显示器的 Java 25 客户端运行保留的驱动，再做真实 LAN、代表性整合包和 Linux x64 验收；在这些通过前维持预览状态。
