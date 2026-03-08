package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class CheckBox extends RenderableSetting {
    private final BooleanSetting setting;
    private Color hoverColor;

    public CheckBox(ModuleButton parent, Setting<?> setting, int offset) {
        super(parent, setting, offset);
        this.setting = (BooleanSetting) setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int idx = parent.settings.indexOf(this);
        int ry  = parentY() + parentOffset() + offset;
        int rh  = parentHeight();

        // Label — indented to clear the accent bar
        TextRenderer.drawString(setting.getName(), context,
                parentX() + 14, ry + rh / 2 + 3,
                new Color(185, 185, 192).getRGB());

        // Toggle pill — right-aligned
        boolean val  = setting.getValue();
        int pillW = 26; int pillH = 13;
        int pillX = parentX() + parentWidth() - pillW - 8;
        int pillY = ry + (rh - pillH) / 2;

        // Pill background
        Color pillBg = val
                ? Utils.getMainColor(200, idx)
                : new Color(32, 32, 38, 200);
        RenderUtils.renderRoundedQuad(context.getMatrices(), pillBg,
                pillX, pillY, pillX + pillW, pillY + pillH,
                6, 6, 6, 6, 8);

        // Pill outline
        Color outlineCol = val
                ? Utils.getMainColor(120, idx)
                : new Color(50, 50, 58, 180);
        RenderUtils.renderRoundedOutline(context, outlineCol,
                pillX, pillY, pillX + pillW, pillY + pillH,
                6, 6, 6, 6, 1.0, 8);

        // Thumb circle
        double thumbX = val ? pillX + pillW - 10.5 : pillX + 2.5;
        RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 100),
                thumbX + 4, pillY + 6.5, 4.5, 12);
        RenderUtils.renderCircle(context.getMatrices(), Color.WHITE,
                thumbX + 4, pillY + 6.5, 3.8, 12);

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
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mouseOver && parent.extended && keyCode == GLFW.GLFW_KEY_BACKSPACE)
            setting.setValue(setting.getOriginalValue());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
            setting.toggle();
    }
}
