package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class KeybindBox extends RenderableSetting {
    public KeybindSetting keybind;
    private Color hoverColor;

    public KeybindBox(ModuleButton parent, Setting<?> setting, int offset) {
        super(parent, setting, offset);
        this.keybind = (KeybindSetting) setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int idx = parent.settings.indexOf(this);
        int ry  = parentY() + parentOffset() + offset;
        int rh  = parentHeight();

        boolean listening = keybind.isListening();
        Color   textColor = listening
                ? Utils.getMainColor(255, idx)
                : new Color(185, 185, 192);

        CharSequence nameSeq = setting.getName();
        CharSequence keySeq  = listening
                ? EncryptedString.of(" \u2014 listening...")
                : EncryptedString.of(": " + KeyUtils.getKey(keybind.getKey()));

        // Name label — indented
        TextRenderer.drawString(nameSeq, context,
                parentX() + 14, ry + rh / 2 + 3, textColor.getRGB());

        // Key value — right-aligned chip
        int keyW  = TextRenderer.getWidth(keySeq) + 12;
        int keyH  = 14;
        int keyX  = parentX() + parentWidth() - keyW - 8;
        int keyY  = ry + (rh - keyH) / 2;

        if (!listening) {
            Color accent = Utils.getMainColor(160, idx);
            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22),
                    keyX, keyY, keyX + keyW, keyY + keyH, 3, 3, 3, 3, 6);
            RenderUtils.renderRoundedOutline(context,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90),
                    keyX, keyY, keyX + keyW, keyY + keyH, 3, 3, 3, 3, 1.0, 6);
        }
        TextRenderer.drawString(keySeq, context,
                keyX + keyW / 2 - TextRenderer.getWidth(keySeq) / 2,
                keyY + 10, textColor.getRGB());

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
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) return;
        if (!keybind.isListening()) {
            keybind.toggleListening(); keybind.setListening(true);
        } else {
            if (keybind.isModuleKey()) parent.module.setKey(button);
            keybind.setKey(button); keybind.setListening(false);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && mouseOver) {
            if (keybind.isModuleKey()) parent.module.setKey(keybind.getOriginalKey());
            keybind.setKey(keybind.getOriginalKey()); keybind.setListening(false);
        } else if (keybind.isListening() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (keybind.isModuleKey()) parent.module.setKey(keyCode);
            keybind.setKey(keyCode); keybind.setListening(false);
        }
    }
}
