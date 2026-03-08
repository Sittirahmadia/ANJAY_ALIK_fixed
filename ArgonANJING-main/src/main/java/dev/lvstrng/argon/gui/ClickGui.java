package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

public final class ClickGui extends Screen {
    public List<Window> windows = new ArrayList<>();
    public Color currentColor;

    // ── Search state ──────────────────────────────────────────────────────
    public String  searchQuery   = "";
    public boolean searchFocused = false;

    private static final int SEARCH_W = 220;
    private static final int SEARCH_H = 20;

    // ── Save-config toast ─────────────────────────────────────────────────
    private long   saveNotifTime  = 0;       // ms timestamp when "Saved!" was triggered
    private static final long NOTIF_DURATION = 1800;

    // ── Save-button (small rect next to search bar) ───────────────────────
    private static final int SAVE_W = 52;
    private static final int SAVE_H = 20;
    private Color  saveButtonColor = new Color(14, 14, 17, 200);

    public ClickGui() {
        super(Text.empty());
        int offsetX = 30;
        for (Category category : Category.values()) {
            windows.add(new Window(offsetX, 50, 200, 28, category, this));
            offsetX += 218;
        }
    }

    public boolean isDraggingAlready() {
        for (Window window : windows)
            if (window.dragging) return true;
        return false;
    }

    @Override
    protected void setInitialFocus() {
        if (client == null) return;
        super.setInitialFocus();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen != this) return;

        if (Argon.INSTANCE.previousScreen != null)
            Argon.INSTANCE.previousScreen.render(context, 0, 0, delta);

        // Background dim
        if (currentColor == null) currentColor = new Color(0, 0, 0, 0);
        else currentColor = new Color(0, 0, 0, currentColor.getAlpha());
        int targetAlpha = ClickGUI.background.getValue() ? 160 : 0;
        if (currentColor.getAlpha() != targetAlpha)
            currentColor = ColorUtils.smoothAlphaTransition(0.05F, targetAlpha, currentColor);
        if (currentColor.getAlpha() > 0)
            context.fill(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight(), currentColor.getRGB());

        RenderUtils.unscaledProjection();
        mouseX *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
        mouseY *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
        super.render(context, mouseX, mouseY, delta);

        // ── Draw category windows ─────────────────────────────────────────
        for (Window window : windows) {
            window.render(context, mouseX, mouseY, delta);
            window.updatePosition(mouseX, mouseY, delta);
        }

        // ── Draw search bar ───────────────────────────────────────────────
        int fw = mc.getWindow().getFramebufferWidth();
        int fh = mc.getWindow().getFramebufferHeight();
        int sx = fw / 2 - (SEARCH_W + 6 + SAVE_W) / 2;
        int sy = 8;

        // Search box shadow
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(0, 0, 0, 60),
                sx - 2, sy - 2, sx + SEARCH_W + 2, sy + SEARCH_H + 2,
                5, 5, 5, 5, 8);

        // Search box bg
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(14, 14, 17, 210),
                sx, sy, sx + SEARCH_W, sy + SEARCH_H,
                4, 4, 4, 4, 10);

        // Accent border — glows when focused
        if (searchFocused) {
            RenderUtils.renderRoundedOutline(context,
                    Utils.getMainColor(180, 0),
                    sx, sy, sx + SEARCH_W, sy + SEARCH_H,
                    4, 4, 4, 4, 1.2, 10);
        } else {
            RenderUtils.renderRoundedOutline(context,
                    new Color(40, 40, 45, 140),
                    sx, sy, sx + SEARCH_W, sy + SEARCH_H,
                    4, 4, 4, 4, 1.0, 8);
        }

        // Search icon label (left side)
        CharSequence iconLabel = EncryptedString.of("\u2315 ");
        TextRenderer.drawString(iconLabel, context,
                sx + 6, sy + SEARCH_H / 2 + 3,
                new Color(80, 80, 90).getRGB());

        // Search text or placeholder
        boolean showCursor = searchFocused && (System.currentTimeMillis() % 900 < 450);
        String displayRaw = searchQuery + (showCursor ? "|" : "");
        CharSequence displayText = searchQuery.isEmpty() && !searchFocused
                ? EncryptedString.of("Search modules...")
                : EncryptedString.of(displayRaw);
        int textColor = searchQuery.isEmpty() && !searchFocused
                ? new Color(75, 75, 85).getRGB()
                : Color.WHITE.getRGB();
        int iconW = TextRenderer.getWidth(iconLabel);
        TextRenderer.drawString(displayText, context,
                sx + 6 + iconW, sy + SEARCH_H / 2 + 3, textColor);

        // ── Draw Save-Config button ───────────────────────────────────────
        int bx = sx + SEARCH_W + 6;
        boolean saveBtnHovered = mouseX >= bx && mouseX <= bx + SAVE_W
                && mouseY >= sy && mouseY <= sy + SAVE_H;

        Color targetSaveColor = saveBtnHovered
                ? new Color(28, 28, 33, 230)
                : new Color(14, 14, 17, 210);
        saveButtonColor = ColorUtils.smoothColorTransition(0.12f, targetSaveColor, saveButtonColor);

        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(0, 0, 0, 60),
                bx - 2, sy - 2, bx + SAVE_W + 2, sy + SAVE_H + 2,
                5, 5, 5, 5, 8);
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                saveButtonColor,
                bx, sy, bx + SAVE_W, sy + SAVE_H,
                4, 4, 4, 4, 10);
        RenderUtils.renderRoundedOutline(context,
                saveBtnHovered ? Utils.getMainColor(200, 5) : new Color(40, 40, 45, 140),
                bx, sy, bx + SAVE_W, sy + SAVE_H,
                4, 4, 4, 4, 1.0, 8);

        CharSequence saveLabel = EncryptedString.of("Save");
        int saveLabelW = TextRenderer.getWidth(saveLabel);
        TextRenderer.drawString(saveLabel, context,
                bx + SAVE_W / 2 - saveLabelW / 2,
                sy + SAVE_H / 2 + 3,
                saveBtnHovered ? Utils.getMainColor(230, 3).getRGB() : new Color(180, 180, 190).getRGB());

        // ── Save-config toast notification ────────────────────────────────
        long elapsed = System.currentTimeMillis() - saveNotifTime;
        if (saveNotifTime > 0 && elapsed < NOTIF_DURATION) {
            float progress = (float) elapsed / NOTIF_DURATION;
            int notifAlpha = progress < 0.15f
                    ? (int)(progress / 0.15f * 200)
                    : progress > 0.75f
                        ? (int)((1f - (progress - 0.75f) / 0.25f) * 200)
                        : 200;

            CharSequence saved = EncryptedString.of("\u2713  Config Saved");
            int nw = TextRenderer.getWidth(saved) + 20;
            int nh = 22;
            int nx = fw / 2 - nw / 2;
            int ny = fh - 40;

            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(14, 14, 17, Math.min(notifAlpha, 200)),
                    nx, ny, nx + nw, ny + nh,
                    4, 4, 4, 4, 10);
            // Accent top line
            context.fillGradient(nx + 4, ny, nx + nw - 4, ny + 2,
                    Utils.getMainColor(notifAlpha, 0).getRGB(),
                    Utils.getMainColor(notifAlpha, 3).getRGB());
            TextRenderer.drawString(saved, context,
                    nx + nw / 2 - TextRenderer.getWidth(saved) / 2,
                    ny + nh / 2 + 3,
                    new Color(200, 200, 210, notifAlpha).getRGB());
        }

        // ── Search results count (when searching) ─────────────────────────
        if (!searchQuery.isEmpty()) {
            int total = windows.stream()
                    .mapToInt(w -> (int) w.moduleButtons.stream()
                            .filter(mb -> mb.matchesSearch(searchQuery)).count())
                    .sum();
            CharSequence countLabel = EncryptedString.of(total + " result" + (total != 1 ? "s" : ""));
            int clW = TextRenderer.getWidth(countLabel);
            TextRenderer.drawString(countLabel, context,
                    fw / 2 - clW / 2,
                    sy + SEARCH_H + 5,
                    new Color(90, 90, 100, 200).getRGB());
        }

        RenderUtils.scaledProjection();
    }

    /** Save config and show toast. */
    private void saveConfig() {
        try {
            Argon.INSTANCE.getProfileManager().saveProfile();
            saveNotifTime = System.currentTimeMillis();
        } catch (Exception ignored) {}
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        // Auto-save on GUI close
        saveConfig();
        Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
        onGuiClose();
    }

    public void onGuiClose() {
        mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
        currentColor = null;
        searchQuery = "";
        searchFocused = false;
        for (Window window : windows) window.onGuiClose();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && chr >= 32 && chr != 127) {
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+S  →  save config
        if (keyCode == GLFW.GLFW_KEY_S
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            saveConfig();
            return true;
        }

        // ESC  →  clear search if focused, otherwise close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused && !searchQuery.isEmpty()) {
                searchQuery = "";
                return true;
            }
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
        }

        // Backspace  →  delete last search char
        if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchQuery.isEmpty())
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }

        for (Window w : windows) w.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double scaledX = mouseX * mc.getWindow().getScaleFactor();
        double scaledY = mouseY * mc.getWindow().getScaleFactor();

        int fw = mc.getWindow().getFramebufferWidth();
        int sx = fw / 2 - (SEARCH_W + 6 + SAVE_W) / 2;
        int sy = 8;
        int bx = sx + SEARCH_W + 6;

        // Click on search bar
        if (scaledX >= sx && scaledX <= sx + SEARCH_W
                && scaledY >= sy && scaledY <= sy + SEARCH_H) {
            searchFocused = true;
            return true;
        }

        // Click on Save button
        if (scaledX >= bx && scaledX <= bx + SAVE_W
                && scaledY >= sy && scaledY <= sy + SAVE_H) {
            saveConfig();
            return true;
        }

        // Click anywhere else defocuses search
        searchFocused = false;

        double mx = mouseX * (int) mc.getWindow().getScaleFactor();
        double my = mouseY * (int) mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseClicked(mx, my, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        mouseX *= (int) mc.getWindow().getScaleFactor();
        mouseY *= (int) mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseDragged(mouseX, mouseY, button, dX, dY);
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        mouseY *= mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX *= (int) mc.getWindow().getScaleFactor();
        mouseY *= (int) mc.getWindow().getScaleFactor();
        for (Window w : windows) w.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
