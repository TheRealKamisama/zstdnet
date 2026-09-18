package cn.tohsaka.factory.zstdnet.server;

import cn.tohsaka.factory.zstdnet.core.stats.*;
import cn.tohsaka.factory.zstdnet.platform.Platform;
import net.minecraftforge.fml.common.FMLCommonHandler;
import java.time.ZoneId;

public final class ServerProxyBootstrap {
    private static ServerProxyRuntime runtime;
    private static TrafficStatisticsService history;
    private ServerProxyBootstrap() {}
    public static synchronized void startDedicated() {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && server.isDedicatedServer()) start(server.getServerPort(), false);
    }
    public static synchronized int startLan(int port) { start(port, true); return listenPort(port); }
    private static void start(int port, boolean lan) {
        stop();
        TrafficStats stats = new TrafficStats();
        history = new TrafficStatisticsService(Platform.gameDir().resolve("zstdnet-stats"), ZoneId.systemDefault(), stats);
        history.startSession();
        runtime = new ServerProxyRuntime(stats);
        if (lan) runtime.startLan(port); else runtime.start(port);
    }
    public static synchronized int listenPort(int fallback) {
        var snapshot = runtime == null ? null : runtime.hudSnapshot();
        return snapshot == null ? fallback : snapshot.listenPort();
    }
    public static synchronized boolean running() { return runtime != null && runtime.isRunning(); }
    public static synchronized String report(String range) {
        if (history == null) throw new IllegalStateException("Proxy statistics are not active");
        return history.buildReportJson(range, runtime.isLanMode() ? "lan" : "dedicated", "port " + listenPort(0));
    }
    public static synchronized void stop() {
        if (runtime != null) runtime.stop();
        if (history != null) history.stopSession();
        runtime = null;
        history = null;
        cn.tohsaka.factory.zstdnet.network.ControlNetwork.clear();
    }
}
