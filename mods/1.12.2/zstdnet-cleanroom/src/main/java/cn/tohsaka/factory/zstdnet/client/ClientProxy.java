package cn.tohsaka.factory.zstdnet.client;

import cn.tohsaka.factory.zstdnet.platform.CommonProxy;
import cn.tohsaka.factory.zstdnet.proxy.LocalZstdNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientProxy extends CommonProxy {
    private static volatile LocalZstdNet.ProxyHandle connection;
    private static GuiScreen returnScreen;
    private static boolean configScreenRequested;
    private static final AtomicLong generation = new AtomicLong();
    private static final cn.tohsaka.factory.zstdnet.network.ReportAssembly reports = new cn.tohsaka.factory.zstdnet.network.ReportAssembly();
    @Override public void receiveReport(int sequence, int count, byte[] data) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            try {
                String json = reports.accept(sequence, count, data);
                if (json == null) return;
                var generated = TrafficReportGenerator.generate(cn.tohsaka.factory.zstdnet.platform.Platform.gameDir().resolve("zstdnet-reports"), json);
                if (Minecraft.getMinecraft().player != null) Minecraft.getMinecraft().player.sendMessage(new TextComponentString("ZstdNet report: " + generated.latest()));
            } catch (Exception e) {
                reports.reset();
                org.slf4j.LoggerFactory.getLogger(ClientProxy.class).warn("Report rejected", e);
            }
        });
    }
    @Override public void initialize() {
        ClientSettings.load();
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new ClientCommand());
    }
    public static void connect(String destination, GuiScreen parent) {
        ServerAddress address = ServerAddress.fromString(destination);
        startConnection(address.getIP(), address.getPort(), destination, parent);
    }
    public static boolean interceptMultiplayer() { return ClientSettings.interceptMultiplayer; }
    public static long currentAttempt() { return generation.get(); }
    public static void cancelAttempt(long attempt) { if (generation.get() == attempt) close(); }
    public static long connectFromGui(String host, int port, GuiScreen parent) {
        String destination = (host.indexOf(':') >= 0 ? "[" + host + "]" : host) + ":" + port;
        return startConnection(host, port, destination, parent);
    }
    private static long startConnection(String host, int port, String destination, GuiScreen parent) {
        close();
        returnScreen = parent;
        ClientSettings.target = destination;
        ClientSettings.save();
        long attempt = generation.incrementAndGet();
        Thread.ofPlatform().daemon().name("zstdnet-connect").start(() -> {
            try {
                var handle = LocalZstdNet.start(host, port, ClientSettings.level, LocalZstdNet.Mode.ZSTD);
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (generation.get() != attempt) { handle.close(); return; }
                    connection = handle;
                    try {
                        GuiConnecting internal = InternalConnectGuard.construct(() -> new GuiConnecting(parent, Minecraft.getMinecraft(), "127.0.0.1", handle.localPort()));
                        Minecraft.getMinecraft().displayGuiScreen(internal);
                    } catch (RuntimeException e) {
                        close();
                        Minecraft.getMinecraft().displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(parent, "connect.failed", new TextComponentString(e.toString())));
                    }
                });
            } catch (Exception e) {
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (generation.get() == attempt) Minecraft.getMinecraft().displayGuiScreen(new net.minecraft.client.gui.GuiDisconnected(parent, "connect.failed", new TextComponentString(e.toString())));
                });
            }
        });
        return attempt;
    }
    public static void close() {
        reports.reset();
        generation.incrementAndGet();
        var previous = connection;
        connection = null;
        if (previous != null) previous.close();
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        var ending = connection;
        Minecraft.getMinecraft().addScheduledTask(() -> { if (connection == ending) close(); });
    }
    @SubscribeEvent public void guiOpened(net.minecraftforge.client.event.GuiOpenEvent event) {
        var mc = Minecraft.getMinecraft();
        if (connection != null && (event.getGui() instanceof net.minecraft.client.gui.GuiDisconnected ||
            (mc.currentScreen instanceof GuiConnecting && event.getGui() == returnScreen))) close();
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Text event) {
        var handle = connection;
        if (!ClientSettings.hud || handle == null) return;
        var stats = handle.statsSnapshot();
        event.getLeft().add(String.format(java.util.Locale.ROOT, "ZstdNet %.1f%% | wire up %s down %s", stats.ratioPercent(), formatBandwidth(stats.wireUpRate()), formatBandwidth(stats.wireDownRate())));
    }
    private static String formatBandwidth(long bytesPerSecond) {
        if (bytesPerSecond < 1024) return bytesPerSecond + " B/s";
        String[] units = {"KB/s", "MB/s", "GB/s", "TB/s", "PB/s", "EB/s"};
        double value = bytesPerSecond / 1024.0;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }
    @SubscribeEvent public void clientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START || !configScreenRequested) return;
        configScreenRequested = false;
        var mc = Minecraft.getMinecraft();
        // GuiChat closes the current screen after executing a command. Open on the next
        // tick; addScheduledTask would run immediately when already on the client thread.
        if (mc.player != null) mc.displayGuiScreen(new ConnectGui(null));
    }
    public static final class ClientCommand extends CommandBase {
        @Override public String getName() { return "zstdnet"; }
        @Override public String getUsage(ICommandSender sender) { return "/zstdnet connect <host:port> | stop | hud | config | report [session|today|24h|7d|30d]"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length == 2 && args[0].equals("connect")) connect(args[1], Minecraft.getMinecraft().currentScreen);
            else if (args.length == 1 && args[0].equals("stop")) close();
            else if (args.length == 1 && args[0].equals("hud")) { ClientSettings.hud = !ClientSettings.hud; ClientSettings.save(); }
            else if (args.length == 1 && args[0].equals("config")) configScreenRequested = true;
            else if (args.length >= 1 && args[0].equals("report")) {
                int range = java.util.List.of("session", "today", "24h", "7d", "30d").indexOf(args.length > 1 ? args[1] : "session");
                if (range < 0) throw new WrongUsageException(getUsage(sender));
                cn.tohsaka.factory.zstdnet.network.ControlNetwork.requestReport(range);
            }
            else throw new WrongUsageException(getUsage(sender));
        }
    }
}
