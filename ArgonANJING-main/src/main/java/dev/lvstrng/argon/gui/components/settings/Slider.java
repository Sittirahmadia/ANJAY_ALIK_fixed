package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class Slider extends RenderableSetting {
    public boolean dragging;
    public double offsetX;
    public double lerpedOffsetX = 0;
    private final NumberSetting setting;
    public Color currentColor1, currentColor2;
    private Color hoverColor;

    public Slider(ModuleButton parent, Setting<?> setting, int offset) {
        super(parent, setting, offset);
        this.setting = (NumberSetting) setting;
    }

    @Override
    public void onUpdate() {
        int idx = parent.settings.indexOf(this);
        Color c1 = Utils.getMainColor(0, idx).darker();
        Color c2 = Utils.getMainColor(0, idx + 1).darker();
        if (currentColor1 == null) currentColor1 = new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), 0);
        else currentColor1 = new Color(c1.getRed(), c1.getGreen(), c1.getBlue(), currentColor1.getAlpha());
        if (currentColor2 == null) currentColor2 = new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), 0);
        else currentColor2 = new Color(c2.getRed(), c2.getGreen(), c2.getBlue(), currentColor2.getAlpha());
        if (currentColor1.getAlpha() != 255) currentColor1 = ColorUtils.smoothAlphaTransition(0.05F, 255, currentColor1);
        if (currentColor2.getAlpha() != 255) currentColor2 = ColorUtils.smoothAlphaTransition(0.05F, 255, currentColor2);
        super.onUpdate();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int ry = parentY() + parentOffset() + offset;
        int rh = parentHeight();

        offsetX       = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()) * parentWidth();
        lerpedOffsetX = MathUtils.goodLerp((float)(0.5 * delta), lerpedOffsetX, offsetX);

        int trackY1 = ry + 24;
        int trackY2 = ry + rh - 4;

        // Track background — rounded pill
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(24, 24, 30, 200),
                parentX(), trackY1, parentX() + parentWidth(), trackY2,
                2, 2, 2, 2, 6);

        // Filled portion (gradient)
        if (lerpedOffsetX > 0) {
            context.fillGradient(
                    parentX(), trackY1,
                    (int)(parentX() + lerpedOffsetX), trackY2,
                    currentColor1.getRGB(), currentColor2.getRGB());
        }

        // Name + value label — indented
        String label = setting.getName() + ":  " + setting.getValue();
        TextRenderer.drawString(label, context,
                parentX() + 14, ry + 9, new Color(185, 185, 192).getRGB());

        // Hover overlay
        if (!parent.parent.dragging) {
            int toA = isHovered(mouseX, mouseY) ? 18 : 0;
            if (hoverColor == null) hoverColor = new Color(255, 255, 255, toA);
            else hoverColor = new Color(255, 255, 255, hoverColor.getAlpha());
            if (hoverColor.getAlpha() != toA)
                hoverColor = ColorUtils.smoothAlphaTransition(0.05F, toA, hoverColor);
            context.fill(parentX(), ry, parentX() + parentWidth(), ry + rh, hoverColor.getRGB());
        }
    }

    @Override
    public void onGuiClose() { currentColor1 = null; currentColor2 = null; super.onGuiClose(); }

    private void slide(double mouseX) {
        double pct = MathHelper.clamp((mouseX - parentX()) / parentWidth(), 0, 1);
        setting.setValue(MathUtils.roundToDecimal(
                pct * (setting.getMax() - setting.getMin()) + setting.getMin(),
                setting.getIncrement()));
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mouseOver && parent.extended && keyCode == GLFW.GLFW_KEY_BACKSPACE)
            setting.setValue(setting.getOriginalValue());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) { dragging = true; slide(mouseX); }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (dragging) slide(mouseX);
    }
}
