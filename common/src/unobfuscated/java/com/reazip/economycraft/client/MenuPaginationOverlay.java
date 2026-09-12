package com.reazip.economycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reazip.economycraft.util.MenuUiSupport;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.hooks.client.screen.ScreenAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@Environment(EnvType.CLIENT)
public final class MenuPaginationOverlay {
    private static final int ARROW_WIDTH = 12;
    private static final int ARROW_HEIGHT = 17;
    private static final int BACK_X = 53;
    private static final int FORWARD_X = 108;
    private static final int TEXT_CENTER_X = 88;
    private static final int TEXT_Y_OFFSET = 4;
    private static final int MENU_BACK_WIDTH = 16;
    private static final int MENU_BACK_HEIGHT = 16;
    private static final int TITLE_X = 8;
    private static final int TITLE_GAP = 3;
    private static final int PAGINATION_GAP = MenuUiSupport.PAGINATION_GAP;
    private static final Identifier CONTAINER_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final WidgetSprites PAGE_FORWARD_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_forward"),
            Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted"));
    private static final WidgetSprites PAGE_BACKWARD_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_backward"),
            Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted"));
    private static final WidgetSprites MENU_BACK_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("transferable_list/unselect"),
            Identifier.withDefaultNamespace("transferable_list/unselect_highlighted"));
    private static final @Nullable Field TITLE_LABEL_X = intField(AbstractContainerScreen.class, "titleLabelX");
    private static final @Nullable Field INVENTORY_LABEL_Y = intField(AbstractContainerScreen.class, "inventoryLabelY");
    private static final @Nullable Field SLOT_Y = intField(Slot.class, "y");

    private static final @Nullable Field MOUSE_XPOS = doubleField(MouseHandler.class, "xpos");
    private static final @Nullable Field MOUSE_YPOS = doubleField(MouseHandler.class, "ypos");
    private static final long MOUSE_RESTORE_NS = 1_000_000_000L;

    private static boolean registered;
    private static @Nullable Screen boundScreen;
    private static @Nullable ImageButton backButton;
    private static @Nullable ImageButton forwardButton;
    private static @Nullable ImageButton menuBackButton;
    private static @Nullable GapPanel gapPanel;
    private static @Nullable StringWidget pageLabel;
    private static @Nullable PageState cached;
    private static @Nullable Integer cachedBackSlot;
    private static boolean shiftedSlots;
    private static double savedMouseX;
    private static double savedMouseY;
    private static long savedMouseAt;

    private MenuPaginationOverlay() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientGuiEvent.INIT_POST.register(MenuPaginationOverlay::onInit);
        ClientGuiEvent.RENDER_PRE.register((screen, graphics, mouseX, mouseY, delta) -> {
            if (screen instanceof AbstractContainerScreen<?>) rememberMouse();
            refresh(screen);
            return EventResult.pass();
        });
    }

    private static void onInit(Screen screen, ScreenAccess access) {
        if (screen instanceof AbstractContainerScreen<?>) restoreMouse();
        if (!(screen instanceof AbstractContainerScreen<?> container) || container.getMenu().slots.size() <= 36) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int rows = Math.max(1, (container.getMenu().slots.size() - 36) / 9);
        int imageHeight = 114 + rows * 18;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - imageHeight) / 2;
        int gapY = top + 17 + rows * 18;

        boundScreen = screen;
        cached = null;
        cachedBackSlot = null;
        shiftedSlots = false;

        gapPanel = new GapPanel(left, gapY);
        gapPanel.visible = false;
        access.addRenderableWidget(gapPanel);

        menuBackButton = new ImageButton(left + TITLE_X, top, MENU_BACK_WIDTH, MENU_BACK_HEIGHT, MENU_BACK_SPRITES,
                button -> clickBack(container),
                Component.translatable("gui.back")) {
            @Override
            public void playDownSound(SoundManager soundManager) {}
        };
        menuBackButton.setTooltip(Tooltip.create(Component.translatable("gui.back")));
        menuBackButton.visible = false;
        access.addRenderableWidget(menuBackButton);

        backButton = new ImageButton(left + BACK_X, gapY, ARROW_WIDTH, ARROW_HEIGHT, PAGE_BACKWARD_SPRITES,
                button -> click(container, true),
                Component.translatable("gui.recipebook.previous_page")) {
            @Override
            public void playDownSound(SoundManager soundManager) {}
        };
        backButton.setTooltip(Tooltip.create(Component.translatable("gui.recipebook.previous_page")));
        backButton.visible = false;
        access.addRenderableWidget(backButton);

        forwardButton = new ImageButton(left + FORWARD_X, gapY, ARROW_WIDTH, ARROW_HEIGHT, PAGE_FORWARD_SPRITES,
                button -> click(container, false),
                Component.translatable("gui.recipebook.next_page")) {
            @Override
            public void playDownSound(SoundManager soundManager) {}
        };
        forwardButton.setTooltip(Tooltip.create(Component.translatable("gui.recipebook.next_page")));
        forwardButton.visible = false;
        access.addRenderableWidget(forwardButton);

        pageLabel = new StringWidget(left + TEXT_CENTER_X, gapY + TEXT_Y_OFFSET, 0, 9, Component.empty(), minecraft.font);
        pageLabel.active = false;
        pageLabel.visible = false;
        access.addRenderableWidget(pageLabel);
    }

    private static void refresh(Screen screen) {
        if (screen != boundScreen || backButton == null || forwardButton == null || pageLabel == null
                || menuBackButton == null || gapPanel == null) {
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;

        refreshMenuBack(container);
        refreshPages(screen, container);
    }

    private static void refreshMenuBack(AbstractContainerScreen<?> container) {
        Integer found = readBackSlot(container);
        if (found != null) {
            cachedBackSlot = found;
            hideBackItem(container, found);
        } else if (cachedBackSlot != null && backSlotReplaced(container, cachedBackSlot)) {
            cachedBackSlot = null;
        } else if (cachedBackSlot != null) {
            hideBackItem(container, cachedBackSlot);
        }

        int rows = Math.max(1, (container.getMenu().slots.size() - 36) / 9);
        int imageHeight = 114 + rows * 18;
        int left = (container.width - 176) / 2;
        int top = (container.height - imageHeight) / 2;

        if (cachedBackSlot == null) {
            menuBackButton.visible = false;
            setInt(TITLE_LABEL_X, container, TITLE_X);
            return;
        }

        boolean shiftTitle = TITLE_LABEL_X != null;
        menuBackButton.setX(shiftTitle ? left + TITLE_X : left - MENU_BACK_WIDTH - 2);
        menuBackButton.setY(top);
        menuBackButton.visible = true;
        menuBackButton.active = true;
        if (shiftTitle) setInt(TITLE_LABEL_X, container, TITLE_X + MENU_BACK_WIDTH + TITLE_GAP);
    }

    private static void refreshPages(Screen screen, AbstractContainerScreen<?> container) {
        PageState found = readPage(container);
        if (found != null) {
            cached = found;
            hidePageItems(container, found);
        } else if (cached != null && hasReplacementItem(container, cached.paperSlot)) {
            cached = null;
        } else if (cached != null) {
            hidePageItems(container, cached);
        }

        int rows = Math.max(1, (container.getMenu().slots.size() - 36) / 9);
        int imageHeight = 114 + rows * 18;
        int left = (screen.width - 176) / 2;
        int top = (screen.height - imageHeight) / 2;
        int gapY = top + 17 + rows * 18;

        if (cached == null || cached.total <= 1) {
            backButton.visible = false;
            forwardButton.visible = false;
            pageLabel.visible = false;
            gapPanel.visible = false;
            if (shiftedSlots) {
                setInt(INVENTORY_LABEL_Y, container, imageHeight - 94);
                shiftPlayerSlots(container, 0);
                shiftedSlots = false;
            }
            return;
        }

        gapPanel.setX(left);
        gapPanel.setY(gapY);
        gapPanel.visible = true;

        int y = gapY + (PAGINATION_GAP - ARROW_HEIGHT) / 2;
        backButton.setX(left + BACK_X);
        backButton.setY(y);
        forwardButton.setX(left + FORWARD_X);
        forwardButton.setY(y);
        pageLabel.setY(y + TEXT_Y_OFFSET);

        Component text = Component.translatable("gui.recipebook.page", cached.current + 1, cached.total);
        int width = Minecraft.getInstance().font.width(text);
        pageLabel.setMessage(text);
        pageLabel.setWidth(width);
        pageLabel.setX(left + TEXT_CENTER_X - width / 2);
        pageLabel.visible = true;

        backButton.visible = cached.current > 0;
        forwardButton.visible = cached.current < cached.total - 1;
        backButton.active = backButton.visible;
        forwardButton.active = forwardButton.visible;
        setInt(INVENTORY_LABEL_Y, container, imageHeight - 94 + PAGINATION_GAP);
        shiftPlayerSlots(container, PAGINATION_GAP);
        shiftedSlots = true;
    }

    private static void shiftPlayerSlots(AbstractContainerScreen<?> container, int extra) {
        var slots = container.getMenu().slots;
        if (slots.size() <= 36) return;
        int rows = (slots.size() - 36) / 9;
        int baseY = 18 + rows * 18 + 14 + extra;
        int first = slots.size() - 36;
        for (int i = 0; i < 27; i++) {
            setInt(SLOT_Y, slots.get(first + i), baseY + (i / 9) * 18);
        }
        for (int i = 0; i < 9; i++) {
            setInt(SLOT_Y, slots.get(first + 27 + i), baseY + 58);
        }
    }

    private static void click(AbstractContainerScreen<?> screen, boolean previous) {
        if (cached == null) return;
        clickSlot(screen, previous ? cached.prevSlot : cached.nextSlot);
    }

    private static void clickBack(AbstractContainerScreen<?> screen) {
        if (cachedBackSlot == null) return;
        clickSlot(screen, cachedBackSlot);
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, int slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.player == null) return;
        rememberMouse();
        AbstractContainerMenu menu = screen.getMenu();
        minecraft.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, minecraft.player);
        restoreMouse();
    }

    private static void rememberMouse() {
        MouseHandler mouse = Minecraft.getInstance().mouseHandler;
        savedMouseX = mouse.xpos();
        savedMouseY = mouse.ypos();
        savedMouseAt = System.nanoTime();
    }

    private static void restoreMouse() {
        if (savedMouseAt == 0L || System.nanoTime() - savedMouseAt > MOUSE_RESTORE_NS) return;
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouse = minecraft.mouseHandler;
        setDouble(MOUSE_XPOS, mouse, savedMouseX);
        setDouble(MOUSE_YPOS, mouse, savedMouseY);
        InputConstants.grabOrReleaseMouse(minecraft.getWindow(), InputConstants.CURSOR_NORMAL, savedMouseX, savedMouseY);
        mouse.setIgnoreFirstMove();
    }

    private static @Nullable Integer readBackSlot(AbstractContainerScreen<?> screen) {
        int containerSlots = screen.getMenu().slots.size() - 36;
        if (containerSlots < 1) return null;
        for (int slot = 0; slot < containerSlots; slot++) {
            if ("Back".equals(screen.getMenu().getSlot(slot).getItem().getHoverName().getString())) {
                return slot;
            }
        }
        return null;
    }

    private static boolean backSlotReplaced(AbstractContainerScreen<?> screen, int slot) {
        ItemStack stack = screen.getMenu().getSlot(slot).getItem();
        if (stack.isEmpty()) return false;
        String name = stack.getHoverName().getString();
        return !"Back".equals(name) && !" ".equals(name);
    }

    private static void hideBackItem(AbstractContainerScreen<?> screen, int slot) {
        hideNamed(screen, slot, "Back");
    }

    private static @Nullable PageState readPage(AbstractContainerScreen<?> screen) {
        int containerSlots = screen.getMenu().slots.size() - 36;
        if (containerSlots < 9) return null;
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack paper = screen.getMenu().getSlot(slot).getItem();
            String name = paper.getHoverName().getString();
            if (!name.startsWith("Page ") || !name.contains("/")) continue;
            String[] parts = name.substring("Page ".length()).split("/", 2);
            if (parts.length != 2) continue;
            try {
                int current = Integer.parseInt(parts[0].trim()) - 1;
                int total = Integer.parseInt(parts[1].trim());
                if (current < 0 || total < 1) continue;
                int rowStart = (slot / 9) * 9;
                return new PageState(current, total, rowStart + 3, rowStart + 5, slot);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasReplacementItem(AbstractContainerScreen<?> screen, int slot) {
        ItemStack stack = screen.getMenu().getSlot(slot).getItem();
        if (stack.isEmpty()) return false;
        String name = stack.getHoverName().getString();
        return !name.startsWith("Page ") && !" ".equals(name)
                && !"Previous page".equals(name) && !"Next page".equals(name);
    }

    private static void hidePageItems(AbstractContainerScreen<?> screen, PageState state) {
        hideNamed(screen, state.prevSlot, "Previous page");
        hideNamed(screen, state.paperSlot, null);
        hideNamed(screen, state.nextSlot, "Next page");
    }

    private static void hideNamed(AbstractContainerScreen<?> screen, int slot, @Nullable String expected) {
        ItemStack stack = screen.getMenu().getSlot(slot).getItem();
        String name = stack.getHoverName().getString();
        if (expected != null && !expected.equals(name) && !name.startsWith("Page ")) return;
        if (expected == null && !name.startsWith("Page ") && !"Previous page".equals(name) && !"Next page".equals(name)) {
            return;
        }
        screen.getMenu().getSlot(slot).set(MenuUiSupport.filler());
    }

    private static void setInt(@Nullable Field field, Object target, int value) {
        if (field == null) return;
        try {
            field.setInt(target, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static void setDouble(@Nullable Field field, Object target, double value) {
        if (field == null) return;
        try {
            field.setDouble(target, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static @Nullable Field intField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable Field doubleField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException ignored) {
            return null;
        }
    }

    private record PageState(int current, int total, int prevSlot, int nextSlot, int paperSlot) {}

    private static final class GapPanel extends AbstractWidget {
        private GapPanel(int x, int y) {
            super(x, y, 176, PAGINATION_GAP, Component.empty());
            this.active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            int remaining = PAGINATION_GAP;
            int dy = getY();
            while (remaining > 0) {
                int h = Math.min(13, remaining);
                graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, getX(), dy, 0.0F, 4.0F, 176, h, 256, 256);
                dy += h;
                remaining -= h;
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, getX(), getY() + PAGINATION_GAP,
                    0.0F, 126.0F, 176, 96, 256, 256);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
}
