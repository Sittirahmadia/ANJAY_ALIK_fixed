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

    // ── State ──────────────────────────────────────────────────────────────────
    public final List<RenderableSetting> settings = new ArrayList<>();
    public final Window parent;
    public final Module module;
    public int     offset;       // Y offset from Window.getY()
    public boolean extended;
    public AnimationUtils animation = new AnimationUtils(0);

    // ── Color state ────────────────────────────────────────────────────────────
    private Color nameColor   = new Color(195, 195, 200);
    private Color rowAlpha    = new Color(255, 255, 255, 0);

    public ModuleButton(Window parent, Module module, int offset) {
        this.parent   = parent;
        this.module   = module;
        this.offset   = offset;
        this.extended = false;

        int settingOffset = parent.getHeight(); // first setting starts one row below the module row
        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting b)  settings.add(new CheckBox(this, b, settingOffset));
            else if (s instanceof NumberSetting n)    settings.add(new Slider(this, n, settingOffset));
            else if (s instanceof ModeSetting<?> m)   settings.add(new ModeBox(this, m, settingOffset));
            else if (s instanceof KeybindSetting k)   settings.add(new KeybindBox(this, k, settingOffset));
            else if (s instanceof StringSetting st)   settings.add(new StringBox(this, st, settingOffset));
            else if (s instanceof MinMaxSetting mm)   settings.add(new MinMaxSlider(this, mm, settingOffset));
            settingOffset += parent.getHeight();
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        for (RenderableSetting rs : settings) rs.onUpdate();

        int px  = parent.getX();
        int py  = parent.getY();
        int pw  = parent.getWidth();
        int ph  = parent.getHeight();          // ROW_H = 28
        int r   = ClickGUI.roundQuads.getValueInt();
        int idx = Argon.INSTANCE.getModuleManager().getModulesInCategory(module.getCategory()).indexOf(module);

        // Absolute Y of this module row
        int rowY = py + offset;

        // ── Animated name colour (accent when on, grey when off) ─────────────
        Color targetName = module.isEnabled()
                ? Utils.getMainColor(255, idx)
                : new Color(155, 155, 162);
        nameColor = ColorUtils.smoothColorTransition(0.12f, targetName, nameColor);

        // ── Hover tint ───────────────────────────────────────────────────────
        boolean hov = isHovered(mouseX, mouseY);
        int hovTarget = (hov && !parent.dragging) ? 18 : 0;
        rowAlpha = ColorUtils.smoothAlphaTransition(0.08f, hovTarget, rowAlpha);

        // ── Row background ────────────────────────────────────────────────────
        boolean isLast = parent.moduleButtons.get(parent.moduleButtons.size() - 1) == this;
        boolean settingsOpen = animation.getValue() > ph;

        if (isLast && !settingsOpen) {
            // Round bottom corners on the last row when settings are closed
            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(12, 12, 15, 175),
                    px, rowY, px + pw, rowY + ph,
                    0, 0, r, r, 50);
        } else {
            context.fill(px, rowY, px + pw, rowY + ph,
                    new Color(12, 12, 15, 175).getRGB());
        }

        // Hover overlay
        if (rowAlpha.getAlpha() > 0)
            context.fill(px, rowY, px + pw, rowY + ph, rowAlpha.getRGB());

        // ── Left accent bar ───────────────────────────────────────────────────
        int barAlpha = module.isEnabled() ? 240 : 50;
        context.fillGradient(
                px, rowY + 5,
                px + 2, rowY + ph - 5,
                Utils.getMainColor(barAlpha, idx).getRGB(),
                Utils.getMainColor(barAlpha, idx + 1).getRGB());

        // ── Module name ───────────────────────────────────────────────────────
        int nameX = px + 12;
        int nameY = rowY + ph / 2 + 3;
        TextRenderer.drawString(module.getName(), context, nameX, nameY, nameColor.getRGB());

        // ── Toggle dot (right side, accent when enabled) ─────────────────────
        int dotX = px + pw - 12;
        int dotY = rowY + ph / 2;
        if (module.isEnabled()) {
            RenderUtils.renderCircle(context.getMatrices(),
                    Utils.getMainColor(210, idx), dotX, dotY, 3, 12);
        } else {
            context.fill(dotX - 3, dotY - 3, dotX + 3, dotY + 3,
                    new Color(55, 55, 60, 120).getRGB());
        }

        // ── Expand arrow ──────────────────────────────────────────────────────
        if (!settings.isEmpty()) {
            CharSequence arrow = EncryptedString.of(extended ? "\u25B2" : "\u25BC");
            int arrowX = dotX - TextRenderer.getWidth(arrow) - (module.isEnabled() ? 12 : 6);
            int arrowColor = extended
                    ? Utils.getMainColor(180, idx).getRGB()
                    : new Color(65, 65, 72).getRGB();
            TextRenderer.drawString(arrow, context, arrowX, nameY, arrowColor);
        }

        // ── Description tooltip ───────────────────────────────────────────────
        if (hov && !parent.dragging && module.getDescription() != null) {
            renderTooltip(context, module.getDescription(), idx);
        }

        // ── Settings rendering ────────────────────────────────────────────────
        renderSettings(context, mouseX, mouseY, delta);

        if (extended)
            for (RenderableSetting rs : settings)
                rs.renderDescription(context, mouseX, mouseY, delta);
    }

    private void renderSettings(DrawContext context, int mouseX, int mouseY, float delta) {
        int scissorH = (int) animation.getValue();
        int scissorW = parent.getWidth();
        if (scissorH <= parent.getHeight() || scissorW <= 0) return;

        int px = parent.getX();
        int py = parent.getY() + offset;
        int scissorY = MinecraftClient.getInstance().getWindow().getHeight() - (py + scissorH);
        scissorY = Math.max(0, scissorY);

        RenderSystem.enableScissor(px, scissorY, scissorW, scissorH);

        // Left indent guide line for settings block
        int indentX  = px + 8;
        int indentY1 = py + parent.getHeight();
        int indentY2 = py + (int) animation.getValue();
        context.fillGradient(indentX, indentY1, indentX + 1, indentY2,
                Utils.getMainColor(80, 0).getRGB(),
                Utils.getMainColor(30, 2).getRGB());

        for (RenderableSetting rs : settings)
            rs.render(context, mouseX, mouseY, delta);

        // Slider knobs on top
        for (RenderableSetting rs : settings) {
            if (rs instanceof Slider slider) {
                RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 160),
                        slider.parentX() + Math.max(slider.lerpedOffsetX, 2.5),
                        slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
                RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
                        slider.parentX() + Math.max(slider.lerpedOffsetX, 2.5),
                        slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
            } else if (rs instanceof MinMaxSlider slider) {
                RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 160),
                        slider.parentX() + Math.max(slider.lerpedOffsetMinX, 2.5),
                        slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
                RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
                        slider.parentX() + Math.max(slider.lerpedOffsetMinX, 2.5),
                        slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
                RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 160),
                        slider.parentX() + Math.max(slider.lerpedOffsetMaxX, 2.5),
                        slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
                RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
                        slider.parentX() + Math.max(slider.lerpedOffsetMaxX, 2.5),
                        slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
            }
        }

        RenderSystem.disableScissor();
    }

    private void renderTooltip(DrawContext context, CharSequence desc, int idx) {
        int tw  = TextRenderer.getWidth(desc);
        int fw  = mc.getWindow().getFramebufferWidth();
        int fh  = mc.getWindow().getFramebufferHeight();
        int tx  = fw / 2 - tw / 2;
        int ty  = fh - 38;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(13, 13, 16, 225),
                tx - 10, ty - 14, tx + tw + 10, ty + 14, 4, 4, 4, 4, 10);
        context.fillGradient(tx - 10, ty - 14, tx + tw + 10, ty - 13,
                Utils.getMainColor(150, idx).getRGB(),
                Utils.getMainColor(150, idx + 1).getRGB());
        TextRenderer.drawString(desc, context, tx, ty, new Color(175, 175, 182).getRGB());
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) module.toggle();
            if (button == 1 && !settings.isEmpty()) {
                if (!extended) collapseOthers();
                extended = !extended;
            }
        }
        if (extended)
            for (RenderableSetting rs : settings) rs.mouseClicked(mouseX, mouseY, button);
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (extended)
            for (RenderableSetting rs : settings) rs.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (RenderableSetting rs : settings) rs.mouseReleased(mouseX, mouseY, button);
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (RenderableSetting rs : settings) rs.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onGuiClose() {
        rowAlpha = null; nameColor = new Color(195, 195, 200);
        extended = false;
        for (RenderableSetting rs : settings) rs.onGuiClose();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void collapseOthers() {
        for (ModuleButton mb : parent.moduleButtons) mb.extended = false;
    }

    public boolean isHovered(double mx, double my) {
        int px = parent.getX(), py = parent.getY() + offset;
        int pw = parent.getWidth(), ph = parent.getHeight();
        return mx > px && mx < px + pw && my > py && my < py + ph;
    }

    /**
     * Matches search query against the module name AND all setting names.
     * This is the "Search Setting" feature.
     */
    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase();
        if (module.getName().toString().toLowerCase().contains(q)) return true;
        // Also search setting names
        for (Setting<?> s : module.getSettings()) {
            if (s.getName().toString().toLowerCase().contains(q)) return true;
        }
        return false;
    }
}
