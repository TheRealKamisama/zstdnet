package cn.tohsaka.factory.zstdnet.client;
import net.minecraft.client.gui.*;
import java.io.IOException;

/** Available from Mods > ZstdNet > Config, including before joining a world. */
public final class ConnectGui extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField address;
    private long connectionAttempt;
    public ConnectGui(GuiScreen parent) { this.parent = parent; }
    @Override public void initGui() {
        address = new GuiTextField(0, fontRenderer, width / 2 - 120, height / 2 - 35, 240, 20);
        address.setMaxStringLength(255);
        address.setText(ClientSettings.target);
        address.setFocused(true);
        addButton(new GuiButton(1, width / 2 - 120, height / 2, 116, 20, "Connect Zstd"));
        addButton(new GuiButton(2, width / 2 + 4, height / 2, 116, 20, "Done"));
        addButton(new GuiButton(3, width / 2 - 120, height / 2 + 25, 116, 20, "Level: " + ClientSettings.level));
        addButton(new GuiButton(4, width / 2 + 4, height / 2 + 25, 116, 20, "HUD: " + ClientSettings.hud));
        addButton(new GuiButton(5, width / 2 - 120, height / 2 + 50, 240, 20, "Multiplayer proxy: " + ClientSettings.interceptMultiplayer));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1 && !address.getText().isBlank()) {
            ClientProxy.connect(address.getText().trim(), parent);
            connectionAttempt = ClientProxy.currentAttempt();
        }
        if (button.id == 2) { cancelOwnAttempt(); mc.displayGuiScreen(parent); }
        if (button.id == 3) { ClientSettings.level = ClientSettings.level % 22 + 1; button.displayString = "Level: " + ClientSettings.level; ClientSettings.save(); }
        if (button.id == 4) { ClientSettings.hud = !ClientSettings.hud; button.displayString = "HUD: " + ClientSettings.hud; ClientSettings.save(); }
        if (button.id == 5) { ClientSettings.interceptMultiplayer = !ClientSettings.interceptMultiplayer; button.displayString = "Multiplayer proxy: " + ClientSettings.interceptMultiplayer; ClientSettings.save(); }
    }
    private void cancelOwnAttempt() {
        if (connectionAttempt != 0) { ClientProxy.cancelAttempt(connectionAttempt); connectionAttempt = 0; }
    }
    @Override protected void keyTyped(char c, int key) throws IOException {
        if (key == 1) { cancelOwnAttempt(); mc.displayGuiScreen(parent); return; }
        if (!address.textboxKeyTyped(c, key)) super.keyTyped(c, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException { address.mouseClicked(x, y, button); super.mouseClicked(x, y, button); }
    @Override public void updateScreen() { address.updateCursorCounter(); }
    @Override public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "ZstdNet: proxy host:port (IPv6: [::1]:35565)", width / 2, height / 2 - 60, 0xffffff);
        address.drawTextBox();
        super.drawScreen(x, y, partialTicks);
    }
}
