package cn.tohsaka.factory.zstdnet.mixin;
import net.minecraft.network.NettyCompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(NettyCompressionDecoder.class)
public interface CompressionDecoderAccessor {
    @Accessor("threshold") int zstdnet$threshold();
}
