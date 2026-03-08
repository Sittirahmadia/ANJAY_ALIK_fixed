package dev.lvstrng.argon.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.gui.components.settings.MinMaxSlider;
import dev.lvstrng.argon.gui.components.settings.Slider;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Window — module list panel for one category.
 * Positioned inside the right content area of ClickGui.
 * Supports smooth scrolling and search filtering.
 */
public final class Window {

    // ── Module button list ────────────────────────────────────────────────────
    public final ArrayList<ModuleButton> moduleButtons = new ArrayList<>();

    // ── Position (set each frame by ClickGui) ─────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private static final int ROW_H = 28;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private double scrollOffset    = 0;
    private double targetScroll    = 0;
    private static final double MAX_SCROLL_SPEED = 40;

    // ── Misc ──────────────────────────────────────────────────────────────────
    public boolean dragging = false;   // kept for compat with RenderableSetting
    private final Category category;
    public ClickGui parent;

    public Window(Category category, ClickGui parent) {
        this.category = category;
        this.parent   = parent;
        int offset = 0;
        for (Module mod : new ArrayList<>(Argon.INSTANCE.getModuleManager().getModulesInCategory(category))) {
            moduleButtons.add(new ModuleButton(this, mod, offset));
            offset += ROW_H;
        }
    }

    // ── Position setters (called every frame by ClickGui) ────────────────────

    public void setPanel(int x, int y, int w, int h) {
        panelX = x; panelY = y; panelW = w; panelH = h;
    }

    // ── Coordinate API used by ModuleButton / RenderableSetting ──────────────

    public int getX()      { return panelX; }
    public int getY()      { return panelY - (int) scrollOffset; }
    public int getWidth()  { return panelW; }
    public int getHeight() { return ROW_H; }

    // ── Content height ────────────────────────────────────────────────────────

    private int totalContentH(List<ModuleButton> visible) {
        int h = 0;
        for (ModuleButton mb : visible) h += (int) mb.animation.getValue();
        return h;
    }

    // ── Search filter ─────────────────────────────────────────────────────────

    private List<ModuleButton> getVisible() {
        String q = parent.searchQuery;
        if (q == null || q.isEmpty()) return moduleButtons;
        return moduleButtons.stream()
                .filter(mb -> mb.matchesSearch(q))
                .collect(Collectors.toList());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        List<ModuleButton> visible = getVisible();

        // Smooth scroll
        scrollOffset += (targetScroll - scrollOffset) * Math.min(0.3 * delta, 1.0);

        // Clamp scroll
        int contentH = totalContentH(visible);
        int maxScroll = Math.max(0, contentH - panelH);
        targetScroll  = Math.max(0, Math.min(targetScroll, maxScroll));
        scrollOffset  = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Scissor to content area
        int scissorY = MinecraftClient.getInstance().getWindow().getHeight() - (panelY + panelH);
        RenderSystem.enableScissor(panelX, Math.max(0, scissorY), panelW, panelH);

        // Update module button animations and offsets
        int offset = 0;
        for (ModuleButton mb : visible) {
            mb.animation.animate(0.5 * delta,
                    mb.extended ? ROW_H * (mb.settings.size() + 1) : ROW_H);
            mb.offset = offset;
            offset += (int) mb.animation.getValue();
        }

        // Render each visible button
        for (ModuleButton mb : visible) {
            int absY = panelY + mb.offset - (int) scrollOffset;
            if (absY > panelY + panelH) continue;  // below viewport
            if (absY + (int) mb.animation.getValue() < panelY) continue;  // above viewport
            mb.render(context, mouseX, mouseY, delta);
        }

        // Scroll indicator (thin bar on right edge)
        if (contentH > panelH) {
            float ratio = (float) scrollOffset / (contentH - panelH);
            int barH    = Math.max(24, (int)(panelH * ((float) panelH / contentH)));
            int barY    = panelY + (int)((panelH - barH) * ratio);
            context.fill(panelX + panelW - 3, barY,
                         panelX + panelW,     barY + barH,
                         new Color(255, 255, 255, 40).getRGB());
        }

        RenderSystem.disableScissor();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    public void mouseClicked(double mouseX, double mouseY, int button) {
        List<ModuleButton> visible = getVisible();
        for (ModuleButton mb : visible) mb.mouseClicked(mouseX, mouseY, button);
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        List<ModuleButton> visible = getVisible();
        for (ModuleButton mb : visible) mb.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (ModuleButton mb : moduleButtons) mb.mouseReleased(mouseX, mouseY, button);
    }

    public void mouseScrolled(double mouseX, double mouseY, double vAmt) {
        if (mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH) {
            targetScroll -= vAmt * MAX_SCROLL_SPEED;
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton mb : moduleButtons) mb.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onGuiClose() {
        dragging = false;
        scrollOffset = 0;
        targetScroll = 0;
        for (ModuleButton mb : moduleButtons) mb.onGuiClose();
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    public Category getCategory() { return category; }

    /** Collapse all expanded module buttons in this window. */
    public void collapseAll() {
        for (ModuleButton mb : moduleButtons) mb.extended = false;
    }
}
