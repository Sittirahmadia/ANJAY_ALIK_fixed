package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public abstract class RenderableSetting {
    public MinecraftClient mc = MinecraftClient.getInstance();
    public ModuleButton parent;
    public Setting<?> setting;
    public int offset;
    public Color currentColor;
    public boolean mouseOver;
    int x, y, width, height;

    public RenderableSetting(ModuleButton parent, Setting<?> setting, int offset) {
        this.parent  = parent;
        this.setting = setting;
        this.offset  = offset;
    }

    public int parentX()      { return parent.parent.getX(); }
    public int parentY()      { return parent.parent.getY(); }
    public int parentWidth()  { return parent.parent.getWidth(); }
    public int parentHeight() { return parent.parent.getHeight(); }
    public int parentOffset() { return parent.offset; }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        mouseOver = isHovered(mouseX, mouseY);
        x      = parentX();
        y      = parentY() + parentOffset() + offset;
        width  = parentX() + parentWidth();
        height = parentY() + parentOffset() + offset + parentHeight();

        // Setting row background — slightly darker + indented feel via left padding
        context.fill(x, y, width, height, currentColor.getRGB());

        // Subtle left indent line (2 px, offset from accent bar)
        context.fill(x + 9, y + 3, x + 10, height - 3,
                new Color(255, 255, 255, 12).getRGB());

        // Thin separator between settings
        context.fill(x + 12, height - 1, width - 6, height,
                new Color(255, 255, 255, 8).getRGB());
    }

    public void renderDescription(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isHovered(mouseX, mouseY) || setting.getDescription() == null || parent.parent.dragging) return;
        CharSequence desc = setting.getDescription();
        int tw  = TextRenderer.getWidth(desc);
        int fw  = mc.getWindow().getFramebufferWidth();
        int fh  = mc.getWindow().getFramebufferHeight();
        int tx  = fw / 2 - tw / 2;
        int ty  = fh - 36;

        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(13, 13, 16, 220),
                tx - 9, ty - 13, tx + tw + 9, ty + 14, 4, 4, 4, 4, 10);
        // Top accent line
        context.fillGradient(tx - 9, ty - 13, tx + tw + 9, ty - 12,
                Utils.getMainColor(140, 0).getRGB(),
                Utils.getMainColor(140, 2).getRGB());
        TextRenderer.drawString(desc, context, tx, ty, new Color(175, 175, 182).getRGB());
    }

    public void onUpdate() {
        if (currentColor == null) currentColor = new Color(10, 10, 13, 0);
        else currentColor = new Color(10, 10, 13, currentColor.getAlpha());
        if (currentColor.getAlpha() != 130)
            currentColor = ColorUtils.smoothAlphaTransition(0.05F, 130, currentColor);
    }

    public void onGuiClose() { currentColor = null; }
    public void keyPressed(int keyCode, int scanCode, int modifiers) {}
    public void mouseClicked(double mouseX, double mouseY, int button) {}
    public void mouseReleased(double mouseX, double mouseY, int button) {}
    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {}

    public boolean isHovered(double mx, double my) {
        return mx > parentX() && mx < parentX() + parentWidth()
                && my > offset + parentOffset() + parentY()
                && my < offset + parentOffset() + parentY() + parentHeight();
    }
}
