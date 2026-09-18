package cn.tohsaka.factory.zstdnet.server;

import cn.tohsaka.factory.zstdnet.client.TrafficReportGenerator;
import cn.tohsaka.factory.zstdnet.platform.Platform;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class ServerCommand extends CommandBase {
    @Override public String getName() { return "zstdnet-server"; }
    @Override public String getUsage(ICommandSender sender) { return "/zstdnet-server status|report [session|today|7d|30d]|compression [player]"; }
    @Override public int getRequiredPermissionLevel() { return 4; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || args[0].equals("status")) {
            sender.sendMessage(new TextComponentString("ZstdNet running=" + ServerProxyBootstrap.running() + " port=" + ServerProxyBootstrap.listenPort(0)));
        } else if (args[0].equals("compression")) {
            var player = args.length > 1 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
            var network = player.connection.netManager;
            Integer pinned = ((cn.tohsaka.factory.zstdnet.platform.ProxiedConnection) network).zstdnet$compression();
            int expected = pinned == null ? server.getNetworkCompressionThreshold() : pinned;
            network.channel().eventLoop().execute(() -> {
                var pipeline = network.channel().pipeline();
                var encoder = pipeline.get("compress");
                var decoder = pipeline.get("decompress");
                int encoded = encoder instanceof cn.tohsaka.factory.zstdnet.mixin.CompressionEncoderAccessor view ? view.zstdnet$threshold() : -1;
                int decoded = decoder instanceof cn.tohsaka.factory.zstdnet.mixin.CompressionDecoderAccessor view ? view.zstdnet$threshold() : -1;
                String message = "ZSTDNET_COMPRESSION expected=" + expected + " encoder=" + encoded + " decoder=" + decoded + " proxied=" + (pinned != null);
                server.addScheduledTask(() -> sender.sendMessage(new TextComponentString(message)));
            });
        } else if (args[0].equals("report")) {
            try {
                var result = TrafficReportGenerator.generate(Platform.gameDir().resolve("zstdnet-reports"), ServerProxyBootstrap.report(args.length > 1 ? args[1] : "session"));
                sender.sendMessage(new TextComponentString("Report: " + result.latest()));
            } catch (Exception e) { throw new CommandException("Report failed: %s", e.getMessage()); }
        } else throw new WrongUsageException(getUsage(sender));
    }
}
