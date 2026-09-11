package com.reazip.economycraft.client;

import com.reazip.economycraft.shop.ShopUi;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class ShopSearchOverlay {
    private static final int FIELD_WIDTH = 81;
    private static final int FIELD_HEIGHT = 14;
    private static final int ICON_SIZE = 12;
    private static final int ICON_TO_FIELD = 17;
    private static final Component SEARCH_HINT = Component.literal("Search").withStyle(EditBox.SEARCH_HINT_STYLE);

    private static boolean registered;

    private ShopSearchOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register(ShopSearchOverlay::onInit);
    }

    private static void onInit(Screen screen, ScreenAccess access) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || !isShopScreen(container)) return;

        Minecraft minecraft = Minecraft.getInstance();
        int rows = Math.max(1, (container.getMenu().slots.size() - 36) / 9);
        int imageHeight = 114 + rows * 18;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - imageHeight) / 2;
        int groupWidth = ICON_TO_FIELD + FIELD_WIDTH;
        String title = screen.getTitle().getString();
        int x = left + 176 - 7 - groupWidth;
        int y = top + 2;

        String initial = title.startsWith("Search:") ? title.substring("Search:".length()).trim() : "";

        ImageWidget icon = ImageWidget.sprite(ICON_SIZE, ICON_SIZE, Identifier.withDefaultNamespace("icon/search"));
        icon.setX(x);
        icon.setY(y + 1);
        access.addRenderableWidget(icon);

        EditBox box = new EditBox(minecraft.font, x + ICON_TO_FIELD, y, FIELD_WIDTH, FIELD_HEIGHT, SEARCH_HINT) {
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (super.keyPressed(event)) return true;
                return this.canConsumeInput() && !event.isEscape();
            }
        };
        box.setHint(SEARCH_HINT);
        box.setMaxLength(ShopUi.SEARCH_QUERY_MAX_LENGTH);
        box.setVisible(true);
        box.setTextColor(-1);
        box.setValue(initial);
        box.setResponder(ShopSearchOverlay::sendSearch);
        access.addRenderableWidget(box);
    }

    private static void sendSearch(String query) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            connection.sendCommand("shop search");
        } else {
            connection.sendCommand("shop search " + trimmed);
        }
    }

    private static boolean isShopScreen(AbstractContainerScreen<?> screen) {
        String title = screen.getTitle().getString();
        if ("Shop".equals(title) || title.startsWith("Search:")) return true;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            if ("Search".equals(name) || "Clear search".equals(name)) return true;
        }
        return false;
    }
}
