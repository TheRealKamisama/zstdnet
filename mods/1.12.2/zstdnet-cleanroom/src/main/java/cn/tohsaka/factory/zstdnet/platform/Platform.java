package cn.tohsaka.factory.zstdnet.platform;

import java.nio.file.Path;

/** No Minecraft linkage: the transport is also exercised outside the game. */
public final class Platform {
    private static volatile Path gameDir = Path.of(System.getProperty("zstdnet.gameDir", ".")).toAbsolutePath();
    private static volatile boolean client;
    private Platform() {}
    public static void initialize(Path directory, boolean clientSide) { gameDir = directory; client = clientSide; }
    public static Path gameDir() { return gameDir; }
    public static boolean isClient() { return client; }
}
