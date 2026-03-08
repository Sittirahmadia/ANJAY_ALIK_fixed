package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class ModeBox extends RenderableSetting {
    public final ModeSetting<?> setting;
    private Color hoverColor;

    public ModeBox(ModuleButton parent, Setting<?> setting, int offset) {
        super(parent, setting, offset);
        this.setting = (ModeSetting<?>) setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int idx = parent.settings.indexOf(this);
        int ry  = parentY() + parentOffset() + offset;
        int rh  = parentHeight();

        // Label — indented
        TextRenderer.drawString(setting.getName(), context,
                parentX() + 14, ry + rh / 2 + 3, new Color(185, 185, 192).getRGB());

        // Mode chip — right-aligned pill with accent outline
        String modeName = setting.getMode().name();
        int chipW = TextRenderer.getWidth(modeName) + 14;
        int chipH = 15;
        int chipX = parentX() + parentWidth() - chipW - 8;
        int chipY = ry + (rh - chipH) / 2;
        Color accent = Utils.getMainColor(190, idx);

        // Chip fill (very subtle tint)
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28),
                chipX, chipY, chipX + chipW, chipY + chipH,
                4, 4, 4, 4, 6);
        // Chip outline
        RenderUtils.renderRoundedOutline(context,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110),
                chipX, chipY, chipX + chipW, chipY + chipH,
                4, 4, 4, 4, 1.0, 6);

        int labelX = chipX + chipW / 2 - TextRenderer.getWidth(modeName) / 2;
        TextRenderer.drawString(modeName, context, labelX, chipY + 11,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 230).getRGB());

        // Hover
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
            setting.setModeIndex(setting.getOriginalValue());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setting.cycle();
    }
}
