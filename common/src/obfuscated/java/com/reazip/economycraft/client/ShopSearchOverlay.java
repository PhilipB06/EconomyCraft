package com.reazip.economycraft.client;

import com.reazip.economycraft.util.MenuUiSupport;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

@Environment(EnvType.CLIENT)
public final class ShopSearchOverlay {
    private static final int FIELD_WIDTH = 81;
    private static final int FIELD_HEIGHT = 14;
    private static final int ICON_SIZE = 12;
    private static final int ICON_TO_FIELD = 17;
    private static final Component ITEMS_HINT = hint("Search items");
    private static final Component PLAYERS_HINT = hint("Search players");

    private static boolean registered;

    private ShopSearchOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register(ShopSearchOverlay::onInit);
        ClientGuiEvent.RENDER_PRE.register((screen, graphics, mouseX, mouseY, delta) -> {
            if (screen instanceof AbstractContainerScreen<?> container) hideSearchSlots(container);
            return EventResult.pass();
        });
    }

    private static void onInit(Screen screen, ScreenAccess access) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        SearchScreen search = findSearch(container);
        if (search == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        int rows = Math.max(1, (container.getMenu().slots.size() - 36) / 9);
        int imageHeight = 114 + rows * 18;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - imageHeight) / 2;
        int groupWidth = ICON_TO_FIELD + FIELD_WIDTH;
        int x = left + 176 - 7 - groupWidth;
        int y = top + 2;

        ImageWidget icon = ImageWidget.sprite(ICON_SIZE, ICON_SIZE, ResourceLocation.withDefaultNamespace("icon/search"));
        icon.setX(x);
        icon.setY(y + 1);
        access.addRenderableWidget(icon);

        EditBox box = new EditBox(minecraft.font, x + ICON_TO_FIELD, y, FIELD_WIDTH, FIELD_HEIGHT, search.hint) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
                return this.canConsumeInput() && keyCode != 256;
            }
        };
        box.setHint(search.hint);
        box.setMaxLength(MenuUiSupport.SEARCH_QUERY_MAX_LENGTH);
        box.setVisible(true);
        box.setTextColor(-1);
        box.setValue(search.query);
        box.setResponder(ShopSearchOverlay::sendSearch);
        access.addRenderableWidget(box);
    }

    private static void sendSearch(String query) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            connection.sendCommand("eco search");
        } else {
            connection.sendCommand("eco search " + trimmed);
        }
    }

    private static void hideSearchSlots(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (isSearchSlot(slot.getItem().getHoverName().getString())) {
                slot.set(ItemStack.EMPTY);
            }
        }
    }

    private static SearchScreen findSearch(AbstractContainerScreen<?> screen) {
        String title = screen.getTitle().getString();
        String query = title.startsWith("Search:") ? title.substring("Search:".length()).trim() : "";
        String hintName = null;
        boolean found = "Shop".equals(title) || title.startsWith("Search:");
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString();
            if ("Clear search".equals(name)) {
                found = true;
                if (query.isEmpty()) query = showingQuery(stack);
            } else if (name.startsWith("Search")) {
                found = true;
                hintName = name;
            }
        }
        if (!found) return null;
        Component hint;
        if (hintName != null && hintName.toLowerCase().contains("player")) {
            hint = PLAYERS_HINT;
        } else if (hintName != null) {
            hint = hint(hintName);
        } else if (isPlayerTitle(title)) {
            hint = PLAYERS_HINT;
        } else {
            hint = ITEMS_HINT;
        }
        return new SearchScreen(hint, query);
    }

    private static boolean isPlayerTitle(String title) {
        String lower = title.toLowerCase();
        return lower.contains("player") || lower.contains("pay who");
    }

    private static boolean isSearchSlot(String name) {
        return "Clear search".equals(name)
                || "Search".equals(name)
                || "Search items".equals(name)
                || "Search players".equals(name);
    }

    private static String showingQuery(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";
        for (Component line : lore.lines()) {
            String text = line.getString();
            if (text.startsWith("Showing: ")) return text.substring("Showing: ".length());
        }
        return "";
    }

    private static Component hint(String text) {
        return Component.literal(text).withStyle(EditBox.SEARCH_HINT_STYLE);
    }

    private record SearchScreen(Component hint, String query) {}
}
