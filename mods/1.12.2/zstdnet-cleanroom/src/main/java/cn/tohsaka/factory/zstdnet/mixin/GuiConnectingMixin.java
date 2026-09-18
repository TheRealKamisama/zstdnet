package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.client.ClientProxy;
import cn.tohsaka.factory.zstdnet.client.InternalConnectGuard;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConnecting.class)
public abstract class GuiConnectingMixin {
    @Shadow @Final private GuiScreen previousGuiScreen;
    @Unique private long zstdnet$attempt;
    // Both vanilla constructors (server list and direct host/port) call this method.
    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private void zstdnet$connect(String host, int port, CallbackInfo ci) {
        if (InternalConnectGuard.active()) { zstdnet$attempt = ClientProxy.currentAttempt(); return; }
        if (!ClientProxy.interceptMultiplayer()) return;
        zstdnet$attempt = ClientProxy.connectFromGui(host, port, previousGuiScreen);
        ci.cancel();
    }
    @Inject(method = "actionPerformed", at = @At("HEAD"))
    private void zstdnet$cancel(GuiButton button, CallbackInfo ci) {
        if (button.id == 0 && zstdnet$attempt != 0) ClientProxy.cancelAttempt(zstdnet$attempt);
    }
}
