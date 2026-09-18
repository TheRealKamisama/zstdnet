package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.server.ServerProxyBootstrap;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    // Backend is already listening; announce the proxy port in Minecraft's LAN beacon.
    @ModifyArg(method = "shareToLAN", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ThreadLanServerPing;<init>(Ljava/lang/String;Ljava/lang/String;)V"), index = 1)
    private String zstdnet$announce(String port) { return Integer.toString(ServerProxyBootstrap.startLan(Integer.parseInt(port))); }
    @Inject(method = "shareToLAN", at = @At("RETURN"), cancellable = true)
    private void zstdnet$publishedPort(net.minecraft.world.GameType mode, boolean cheats, CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null && ServerProxyBootstrap.running()) cir.setReturnValue(Integer.toString(ServerProxyBootstrap.listenPort(0)));
    }
}
