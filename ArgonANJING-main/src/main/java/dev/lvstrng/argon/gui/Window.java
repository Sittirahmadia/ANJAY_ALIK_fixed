package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Window {
    public ArrayList<ModuleButton> moduleButtons = new ArrayList<>();
    public int x, y;
    private final int width, height;
    public Color currentColor;
    private final Category category;
    public boolean dragging, extended;
    private int dragX, dragY;
    private int prevX, prevY;
    public ClickGui parent;

    /** Simple unicode glyph per category for the header. */
    private static String getCategoryIcon(Category cat) {
        String name = cat.name.toString().toUpperCase();
        if (name.contains("COMBAT"))  return "\u2694";  // ⚔
        if (name.contains("MISC"))    return "\u2699";  // ⚙
        if (name.contains("RENDER"))  return "\u25A6";  // ▦
        if (name.contains("CLIENT"))  return "\u2605";  // ★
        if (name.contains("MOVE"))    return "\u27A4";  // ➤
        return "\u25CF";                                 // ●
    }

    public Window(int x, int y, int width, int height, Category category, ClickGui parent) {
        this.x = x;         this.y = y;
        this.width = width; this.height = height;
        this.dragging = false; this.extended = true;
        this.category = category; this.parent = parent;
        this.prevX = x;     this.prevY = y;

        int offset = height;
        for (Module module : new ArrayList<>(Argon.INSTANCE.getModuleManager().getModulesInCategory(category))) {
            moduleButtons.add(new ModuleButton(this, module, offset));
            offset += height;
        }
    }

    // ── Filtered button list ──────────────────────────────────────────────
    private List<ModuleButton> getVisible() {
        String q = parent.searchQuery;
        if (q == null || q.isEmpty()) return moduleButtons;
        return moduleButtons.stream()
                .filter(mb -> mb.matchesSearch(q))
                .collect(Collectors.toList());
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        List<ModuleButton> visible = getVisible();
        if (!parent.searchQuery.isEmpty() && visible.isEmpty()) return;

        int toAlpha = ClickGUI.alphaWindow.getValueInt();
        int r       = ClickGUI.roundQuads.getValueInt();

        // Smooth alpha
        if (currentColor == null) currentColor = new Color(14, 14, 17, 0);
        else currentColor = new Color(14, 14, 17, currentColor.getAlpha());
        if (currentColor.getAlpha() != toAlpha)
            currentColor = ColorUtils.smoothAlphaTransition(0.05F, toAlpha, currentColor);
        int alpha = currentColor.getAlpha();

        // Compute total height of the module list area for this render pass
        int totalH = 0;
        for (ModuleButton mb : visible) totalH += (int) mb.animation.getValue();

        // ── Deep drop shadow ──────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(0, 0, 0, Math.min(alpha / 3, 65)),
                prevX - 6, prevY - 6, prevX + width + 6, prevY + height + totalH + 6,
                r + 2, r + 2, r + 2, r + 2, 8);

        // ── Module list body (below header) ───────────────────────────────
        if (totalH > 0) {
            // Slightly darker than the header
            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(10, 10, 13, Math.min(alpha, 215)),
                    prevX, prevY + height,
                    prevX + width, prevY + height + totalH,
                    0, 0, r, r, 50);

            // Thin right-edge shimmer
            context.fillGradient(
                    prevX + width - 1, prevY + height,
                    prevX + width,     prevY + height + totalH,
                    Utils.getMainColor(50, 0).getRGB(),
                    Utils.getMainColor(50, 3).getRGB());
        }

        // ── Header background ─────────────────────────────────────────────
        int headerBtmRadius = totalH > 0 ? 0 : r;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(16, 16, 20, Math.min(alpha + 18, 255)),
                prevX, prevY, prevX + width, prevY + height,
                r, r, headerBtmRadius, headerBtmRadius, 50);

        // Header bottom gradient accent line
        context.fillGradient(
                prevX + r,     prevY + height - 2,
                prevX + width - r, prevY + height,
                Utils.getMainColor(Math.min(alpha + 55, 255), 0).getRGB(),
                Utils.getMainColor(Math.min(alpha + 55, 255), 4).getRGB());

        // ── Header text: icon + category label ────────────────────────────
        String icon = getCategoryIcon(category);
        CharSequence iconSeq  = EncryptedString.of(icon + " ");
        CharSequence labelSeq = parent.searchQuery.isEmpty()
                ? category.name
                : EncryptedString.of(category.name + "  \u00B7  " + visible.size());

        int iconW      = TextRenderer.getWidth(iconSeq);
        int labelW     = TextRenderer.getWidth(labelSeq);
        int totalTextW = iconW + labelW;
        int textX      = prevX + width / 2 - totalTextW / 2;
        int textY      = prevY + height / 2 + 3;

        // Icon in accent colour, label in white
        TextRenderer.drawString(iconSeq,  context, textX,         textY,
                Utils.getMainColor(Math.min(alpha + 80, 255), 1).getRGB());
        TextRenderer.drawString(labelSeq, context, textX + iconW, textY,
                Color.WHITE.getRGB());

        // ── Module buttons ────────────────────────────────────────────────
        updateButtons(delta, visible);
        for (ModuleButton mb : visible)
            mb.render(context, mouseX, mouseY, delta);
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton mb : moduleButtons) mb.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onGuiClose() {
        currentColor = null; dragging = false;
        for (ModuleButton mb : moduleButtons) mb.onGuiClose();
    }

    public boolean isDraggingAlready() {
        for (Window w : parent.windows)
            if (w.dragging) return true;
        return false;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0 && !isDraggingAlready()) {
            dragging = true;
            dragX = (int)(mouseX - x);
            dragY = (int)(mouseY - y);
        }
        if (extended) {
            List<ModuleButton> visible = getVisible();
            for (ModuleButton mb : visible) mb.mouseClicked(mouseX, mouseY, button);
        }
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (extended) {
            List<ModuleButton> visible = getVisible();
            for (ModuleButton mb : visible) mb.mouseDragged(mouseX, mouseY, button, dX, dY);
        }
    }

    public void updateButtons(float delta, List<ModuleButton> visible) {
        int offset = height;
        for (ModuleButton mb : visible) {
            mb.animation.animate(0.5 * delta,
                    mb.extended ? height * (mb.settings.size() + 1) : height);
            mb.offset = offset;
            offset += (int) mb.animation.getValue();
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) dragging = false;
        for (ModuleButton mb : moduleButtons) mb.mouseReleased(mouseX, mouseY, button);
    }

    public void mouseScrolled(double mouseX, double mouseY, double h, double v) {
        prevX = x; prevY = y;
        prevY = (int)(prevY + v * 20);
        setY((int)(y + v * 20));
    }

    public int  getX()      { return prevX; }
    public int  getY()      { return prevY; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int  getWidth()  { return width; }
    public int  getHeight() { return height; }

    public boolean isHovered(double mx, double my) {
        return mx > x && mx < x + width && my > y && my < y + height;
    }

    public void updatePosition(double mouseX, double mouseY, float delta) {
        prevX = x; prevY = y;
        if (dragging) {
            x = (int) MathUtils.goodLerp(0.3f * delta, x, mouseX - dragX);
            y = (int) MathUtils.goodLerp(0.3f * delta, y, mouseY - dragY);
        }
    }
}
