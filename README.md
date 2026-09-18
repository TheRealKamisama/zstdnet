# ZstdNet — Cleanroom 1.12.2 Fork

本仓库是 **ZstdNet 的向后移植（Backport）Fork**，在保留原有现代版本模块的基础上，将兼容范围向后扩展至 **Minecraft 1.12.2 / Cleanroom / Java 25**。

This is a **ZstdNet fork with a backport to Minecraft 1.12.2 on Cleanroom / Java 25**, alongside the existing modern-version modules.

Cleanroom 模块当前版本为 `1.4.7-cleanroom.4`。用户已确认实际进服与 Zstd 压缩正常；构建、29 项自动回归及压缩管线验证已完成。LAN、完整整合包和跨平台验收范围见下方验证记录。此移植需要 Cleanroom 和 Java 25，不是传统 Forge 1.12.2 / Java 8 版本，也不提供跨 Minecraft 版本联机。

- English: [README.en-US.md](README.en-US.md)
- 中文: [README.zh-CN.md](README.zh-CN.md)
- Cleanroom 1.12.2 Fork：[构建与使用](mods/1.12.2/zstdnet-cleanroom/README.zh-CN.md) · [迁移验证记录](docs/cleanroom-1.12.2-java25-validation.zh-CN.md) · [压缩修复验证](docs/cleanroom-1.12.2-compression-fix.zh-CN.md) · [客户端界面修复](docs/cleanroom-1.12.2-client-ui-fix.zh-CN.md)
- 下方 CurseForge 链接属于上游项目；本 Fork 的 Cleanroom 构建请按上述模块说明获取，不应视为上游已发布的 1.12.2 版本。
- The CurseForge links below refer to the upstream project. Build this fork's Cleanroom module using the instructions above; it is not an upstream 1.12.2 release.

## 省流版

### 当前支持版本

- **Cleanroom 1.12.2 / Java 25（本 Fork 新增）**
- Forge 1.20.1
- NeoForge 1.20.1
- NeoForge 1.21.1
- Fabric 1.20.1
- Fabric 1.21.1

下方配置和命令速览沿用现代版本说明。**Cleanroom 1.12.2 请使用[独立模块文档](mods/1.12.2/zstdnet-cleanroom/README.zh-CN.md)**，其配置路径、命令、权限和 LAN 验证状态与现代模块不同。

## Velocity版本

https://github.com/wish131400/zstdnet-Velocity
VC版本因为兼容性原因，需要下载velocity插件版本

## spigot的插件版
https://github.com/Meoyuta/ZstdNet-spigot
感谢 https://github.com/Meoyuta 移植的插件版本

### 客户端联机配置

安装好mod后，直接打开局域网即可。游戏端口通常可以留空，ZstdNet 会自动跟随本次实际 LAN 端口；Zstd 端口会优先使用配置里的端口，如果被占用会自动换到可用端口。开放成功后聊天框会提示实际 Zstd 端口，并且端口可以点击复制。如果使用高级联机 mod 完全替换界面导致看不到 Zstd UI，可以用 `/zstdport show` 查看当前端口；只有需要固定公网/隧道端口时，才用 `/zstdport zstd xxxxx` 手动指定。
有正版验证需求，可以额外搭配 [TrueUUID（正版离线共存）](https://www.curseforge.com/minecraft/mc-mods/trueuuid)。使用/zstdhud on指令可以查看压缩状态。

### 服务器配置

安装好 mod 后，最小配置只需要在 `server.properties` 里关闭正版验证：

```properties
online-mode=false
```

然后正常启动服务器即可体验带宽压缩。公网直连、局域网、虚拟局域网、FRP / 反代等详细配置请看 [中文完整文档](README.zh-CN.md)。
有正版验证需求，可以额外搭配 [TrueUUID（正版离线共存）](https://www.curseforge.com/minecraft/mc-mods/trueuuid)。使用/zstdhud on指令可以查看压缩状态。

### 本地流量统计面板

拥有 2 级命令权限的玩家可使用 `/zstdreport today|session|24h|7d|30d` 生成自包含 HTML 面板。面板支持时间筛选、深浅模式、10 套主题色以及 `KiB/s` / `Mbps` 带宽参考；详细说明见 [中文完整文档](README.zh-CN.md#本地流量统计面板) 和 [面板设计文档](docs/design/zstdnet-report-dashboard.md)。

# curseforge

- https://www.curseforge.com/minecraft/mc-mods/zstdnet

# 鸣谢
- [齿轮盛宴官方网站]( https://www.xn--dctt54dhmrbwo.com/ )
- [量子科技官方网站]( https://www.mcplay.cc/ )
- [本项目灵感来源]( https://github.com/MeguminKato )
