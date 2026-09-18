package cn.tohsaka.factory.zstdnet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import java.util.Set;
public final class ConfigGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft minecraft) {}
    @Override public boolean hasConfigGui() { return true; }
    @Override public GuiScreen createConfigGui(GuiScreen parent) { return new ConnectGui(parent); }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return Set.of(); }
}
