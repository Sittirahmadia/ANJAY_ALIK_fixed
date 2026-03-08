package dev.lvstrng.argon.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.Window;
import dev.lvstrng.argon.gui.components.settings.*;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.*;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

public final class ModuleButton {
    public List<RenderableSetting> settings = new ArrayList<>();
    public Window parent;
    public Module module;
    public int offset;
    public boolean extended;
    public int settingOffset;
    public Color currentColor;
    public Color defaultColor  = new Color(200, 200, 205);
    public Color currentAlpha;
    public AnimationUtils animation = new AnimationUtils(0);

    public ModuleButton(Window parent, Module module, int offset) {
        this.parent   = parent;
        this.module   = module;
        this.offset   = offset;
        this.extended = false;

        settingOffset = parent.getHeight();
        for (Setting<?> setting : module.getSettings()) {
            if      (setting instanceof BooleanSetting s)  settings.add(new CheckBox(this, s, settingOffset));
            else if (setting instanceof NumberSetting s)   settings.add(new Slider(this, s, settingOffset));
            else if (setting instanceof ModeSetting<?> s)  settings.add(new ModeBox(this, s, settingOffset));
            else if (setting instanceof KeybindSetting s)  settings.add(new KeybindBox(this, s, settingOffset));
            else if (setting instanceof StringSetting s)   settings.add(new StringBox(this, s, settingOffset));
            else if (setting instanceof MinMaxSetting s)   settings.add(new MinMaxSlider(this, s, settingOffset));
            settingOffset += parent.getHeight();
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent.getY() + offset > MinecraftClient.getInstance().getWindow().getHeight()) return;

        for (RenderableSetting rs : settings) rs.onUpdate();

        // ── Row background ────────────────────────────────────────────────
        if (currentColor == null) currentColor = new Color(14, 14, 17, 0);
        else currentColor = new Color(14, 14, 17, currentColor.getAlpha());
        currentColor = ColorUtils.smoothAlphaTransition(0.05F, 155, currentColor);

        // Smooth name colour: accent when enabled, grey when disabled
        int idx = Argon.INSTANCE.getModuleManager().getModulesInCategory(module.getCategory()).indexOf(module);
        Color targetNameColor = module.isEnabled() ? Utils.getMainColor(255, idx) : new Color(190, 190, 195);
        if (!defaultColor.equals(targetNameColor))
            defaultColor = ColorUtils.smoothColorTransition(0.1F, targetNameColor, defaultColor);

        boolean isLast = parent.moduleButtons.get(parent.moduleButtons.size() - 1) == this;
        int r  = ClickGUI.roundQuads.getValueInt();
        int px = parent.getX();
        int py = parent.getY();
        int pw = parent.getWidth();
        int ph = parent.getHeight();

        // Row bg — last row gets rounded bottom corners (when settings are closed)
        if (!isLast || animation.getValue() > ph) {
            context.fill(px, py + offset, px + pw, py + ph + offset, currentColor.getRGB());
        } else {
            RenderUtils.renderRoundedQuad(context.getMatrices(), currentColor,
                    px, py + offset, px + pw, py + ph + offset,
                    0, 0, r, r, 50);
        }

        // ── Left accent bar ───────────────────────────────────────────────
        int barAlpha = module.isEnabled() ? 230 : 45;
        context.fillGradient(
                px,     py + offset + 4,
                px + 2, py + offset + ph - 4,
                Utils.getMainColor(barAlpha, idx).getRGB(),
                Utils.getMainColor(barAlpha, idx + 1).getRGB());

        // ── Enable/disable dot indicator (right side) ─────────────────────
        int dotX = px + pw - 10;
        int dotY = py + offset + ph / 2;
        if (module.isEnabled()) {
            RenderUtils.renderCircle(context.getMatrices(),
                    Utils.getMainColor(200, idx),
                    dotX, dotY, 3, 10);
        }

        // ── Module name (left-aligned with indent) ────────────────────────
        int nameX = px + 10;                 // indent past the accent bar
        int nameY = py + offset + ph / 2 + 3;
        TextRenderer.drawString(module.getName(), context, nameX, nameY, defaultColor.getRGB());

        // ── Expand arrow (right of name, when settings exist) ─────────────
        if (!module.getSettings().isEmpty()) {
            CharSequence arrow = EncryptedString.of(extended ? "\u25B2" : "\u25BC");  // ▲ / ▼
            int arrowColor = extended
                    ? Utils.getMainColor(200, idx).getRGB()
                    : new Color(70, 70, 78).getRGB();
            int arrowW = TextRenderer.getWidth(arrow);
            TextRenderer.drawString(arrow, context,
                    dotX - arrowW - (module.isEnabled() ? 8 : 0),
                    nameY, arrowColor);
        }

        renderHover(context, mouseX, mouseY);
        renderSettings(context, mouseX, mouseY, delta);

        if (extended)
            for (RenderableSetting rs : settings)
                rs.renderDescription(context, mouseX, mouseY, delta);

        // ── Description tooltip (shown at bottom-centre of screen) ────────
        if (isHovered(mouseX, mouseY) && !parent.dragging && module.getDescription() != null) {
            CharSequence desc = module.getDescription();
            int tw  = TextRenderer.getWidth(desc);
            int fw  = mc.getWindow().getFramebufferWidth();
            int fh  = mc.getWindow().getFramebufferHeight();
            int tx  = fw / 2 - tw / 2;
            int ty  = fh - 36;

            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(13, 13, 16, 220),
                    tx - 9, ty - 13, tx + tw + 9, ty + 14, 4, 4, 4, 4, 10);
            // Accent top bar on tooltip
            context.fillGradient(tx - 9, ty - 13, tx + tw + 9, ty - 12,
                    Utils.getMainColor(160, idx).getRGB(),
                    Utils.getMainColor(160, idx + 1).getRGB());
            TextRenderer.drawString(desc, context, tx, ty, new Color(180, 180, 188).getRGB());
        }
    }

    private void renderHover(DrawContext context, int mouseX, int mouseY) {
        if (parent.dragging) return;
        int toA = isHovered(mouseX, mouseY) ? 20 : 0;
        if (currentAlpha == null) currentAlpha = new Color(255, 255, 255, toA);
        else currentAlpha = new Color(255, 255, 255, currentAlpha.getAlpha());
        if (currentAlpha.getAlpha() != toA)
            currentAlpha = ColorUtils.smoothAlphaTransition(0.05F, toA, currentAlpha);
        context.fill(parent.getX(), parent.getY() + offset,
                parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset,
                currentAlpha.getRGB());
    }

    private void renderSettings(DrawContext context, int mouseX, int mouseY, float delta) {
        int scissorH = (int) animation.getValue();
        int scissorW = parent.getWidth();
        if (scissorH <= 0 || scissorW <= 0) return;
        int scissorX = parent.getX();
        int scissorY = (int)(mc.getWindow().getHeight() - (parent.getY() + offset + scissorH));
        scissorY = Math.max(0, scissorY);
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        if (animation.getValue() > parent.getHeight()) {
            // Thin left indent line spanning the settings area
            int indentX  = parent.getX() + 6;
            int indentY1 = parent.getY() + offset + parent.getHeight();
            int indentY2 = parent.getY() + offset + (int) animation.getValue();
            context.fillGradient(indentX, indentY1, indentX + 1, indentY2,
                    Utils.getMainColor(90, 0).getRGB(),
                    Utils.getMainColor(40, 2).getRGB());

            for (RenderableSetting rs : settings)
                rs.render(context, mouseX, mouseY, delta);

            for (RenderableSetting rs : settings) {
                if (rs instanceof Slider slider) {
                    RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 170),
                            slider.parentX() + Math.max(slider.lerpedOffsetX, 2.5),
                            slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
                    RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
                            slider.parentX() + Math.max(slider.lerpedOffsetX, 2.5),
                            slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
                } else if (rs instanceof MinMaxSlider slider) {
                    RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 170),
                            slider.parentX() + Math.max(slider.lerpedOffsetMinX, 2.5),
                            slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
                    RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
                            slider.parentX() + Math.max(slider.lerpedOffsetMinX, 2.5),
                            slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
                    RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 170),
                            slider.parentX() + Math.max(slider.lerpedOffsetMaxX, 2.5),
                            slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
                    RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
                            slider.parentX() + Math.max(slider.lerpedOffsetMaxX, 2.5),
                            slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
                }
            }
        }
        RenderSystem.disableScissor();
    }

    public void onExtend() {
        for (ModuleButton mb : parent.moduleButtons) mb.extended = false;
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (RenderableSetting rs : settings) rs.keyPressed(keyCode, scanCode, modifiers);
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (extended)
            for (RenderableSetting rs : settings) rs.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) module.toggle();
            if (button == 1 && !module.getSettings().isEmpty()) {
                if (!extended) onExtend();
                extended = !extended;
            }
        }
        if (extended)
            for (RenderableSetting rs : settings) rs.mouseClicked(mouseX, mouseY, button);
    }

    public void onGuiClose() {
        currentAlpha = null; currentColor = null;
        for (RenderableSetting rs : settings) rs.onGuiClose();
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (RenderableSetting rs : settings) rs.mouseReleased(mouseX, mouseY, button);
    }

    public boolean isHovered(double mx, double my) {
        return mx > parent.getX() && mx < parent.getX() + parent.getWidth()
                && my > parent.getY() + offset && my < parent.getY() + offset + parent.getHeight();
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        return module.getName().toString().toLowerCase().contains(query.toLowerCase());
    }
}
