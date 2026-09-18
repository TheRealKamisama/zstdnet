package cn.tohsaka.factory.zstdnet.mixin;
import cn.tohsaka.factory.zstdnet.platform.RawHandshake;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.handshake.client.C00Handshake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(C00Handshake.class)
public abstract class HandshakeMixin implements RawHandshake {
    @Unique private String zstdnet$host;
    @Redirect(method = "readPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;readString(I)Ljava/lang/String;"))
    private String zstdnet$capture(PacketBuffer buffer, int limit) { return zstdnet$host = buffer.readString(limit); }
    @Override public String zstdnet$rawHost() { return zstdnet$host; }
}
