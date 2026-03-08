package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.managers.ConfigManager;
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

/**
 * ClickGui — redesigned single-panel layout.
 *
 * Layout (framebuffer pixels):
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  ARGON                                  [Search] [⚙ Configs]  │  <- TITLE_H (36)
 * ├──────────────┬──────────────────────────────────────────────────┤
 * │              │                                                  │
 * │  Category    │   Module list (scrollable per-category window)   │
 * │  sidebar     │                                                  │
 * │  (SIDEBAR_W) │              (content width)                     │
 * │              │                                                  │
 * └──────────────┴──────────────────────────────────────────────────┘
 */
public final class ClickGui extends Screen {

    // ── Panel dimensions (framebuffer pixels) ─────────────────────────────────
    private static final int PANEL_W   = 860;
    private static final int PANEL_H   = 530;
    private static final int SIDEBAR_W = 178;
    private static final int TITLE_H   = 38;
    private static final int DIVIDER   = 1;
    private static final int RADIUS    = 6;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<Window> windows = new ArrayList<>();
    private int selectedCategoryIdx  = 0;
    private Color dimAlpha = new Color(0, 0, 0, 0);

    // ── Search ────────────────────────────────────────────────────────────────
    public  String  searchQuery   = "";
    public  boolean searchFocused = false;

    // ── Config manager overlay ────────────────────────────────────────────────
    private boolean showConfigs   = false;
    private List<String> cfgList  = new ArrayList<>();
    private int  cfgHovered       = -1;
    private int  cfgScrollOffset  = 0;
    private String cfgNewName     = "";
    private boolean cfgInputFocused = false;
    private int  cfgDeleteConfirm = -1;   // index pending delete confirmation

    // ── Toast ─────────────────────────────────────────────────────────────────
    private long   toastTime   = 0;
    private String toastMsg    = "";
    private static final long TOAST_MS = 1800;

    // ── Category tab hover animation ──────────────────────────────────────────
    private final float[] tabHover;

    public ClickGui() {
        super(Text.empty());
        for (Category cat : Category.values())
            windows.add(new Window(cat, this));
        tabHover = new float[Category.values().length];
    }

    // ── Computed panel bounds ─────────────────────────────────────────────────

    private int panelX() { return mc.getWindow().getFramebufferWidth()  / 2 - PANEL_W / 2; }
    private int panelY() { return mc.getWindow().getFramebufferHeight() / 2 - PANEL_H / 2; }
    private int rightX() { return panelX() + SIDEBAR_W + DIVIDER; }
    private int rightW() { return PANEL_W - SIDEBAR_W - DIVIDER; }
    private int contentY() { return panelY() + TITLE_H; }
    private int contentH() { return PANEL_H - TITLE_H; }

    // ── Screen overrides ──────────────────────────────────────────────────────

    @Override protected void setInitialFocus() { if (client != null) super.setInitialFocus(); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() {
        saveAndClose();
    }

    private void saveAndClose() {
        try { Argon.INSTANCE.getProfileManager().saveProfile(); } catch (Exception ignored) {}
        Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
        onGuiClose();
    }

    public void onGuiClose() {
        mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
        dimAlpha = null;
        searchQuery = "";
        searchFocused = false;
        showConfigs = false;
        for (Window w : windows) w.onGuiClose();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen != this) return;

        // Render game world behind GUI
        if (Argon.INSTANCE.previousScreen != null)
            Argon.INSTANCE.previousScreen.render(context, 0, 0, delta);

        // Dim overlay
        if (dimAlpha == null) dimAlpha = new Color(0, 0, 0, 0);
        int targetDim = ClickGUI.background.getValue() ? 155 : 0;
        if (dimAlpha.getAlpha() != targetDim)
            dimAlpha = ColorUtils.smoothAlphaTransition(0.06f, targetDim, dimAlpha);
        if (dimAlpha.getAlpha() > 0)
            context.fill(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight(), dimAlpha.getRGB());

        RenderUtils.unscaledProjection();
        int fmx = (int)(mouseX * mc.getWindow().getScaleFactor());
        int fmy = (int)(mouseY * mc.getWindow().getScaleFactor());

        super.render(context, fmx, fmy, delta);

        int px = panelX(), py = panelY();
        int rx = rightX(),  rw = rightW();
        int cy = contentY(), ch = contentH();
        int r  = RADIUS;

        // ── 1. Outer drop shadow ──────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(0, 0, 0, 70),
                px - 8, py - 8, px + PANEL_W + 8, py + PANEL_H + 8,
                r + 3, r + 3, r + 3, r + 3, 10);

        // ── 2. Main panel background ──────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(11, 11, 14, 245),
                px, py, px + PANEL_W, py + PANEL_H,
                r, r, r, r, 60);

        // ── 3. Title bar background ───────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(15, 15, 18, 255),
                px, py, px + PANEL_W, py + TITLE_H,
                r, r, 0, 0, 60);

        // Title bar bottom accent line
        context.fillGradient(px + r, py + TITLE_H - 1, px + PANEL_W - r, py + TITLE_H,
                Utils.getMainColor(200, 0).getRGB(),
                Utils.getMainColor(200, 5).getRGB());

        // ── 4. Client name in title bar ───────────────────────────────────────
        CharSequence logo = EncryptedString.of("ARGON");
        TextRenderer.drawString(logo, context,
                px + 14, py + TITLE_H / 2 + 3,
                Utils.getMainColor(230, 0).getRGB());
        CharSequence ver = EncryptedString.of(Argon.INSTANCE.version.trim());
        int logoW = TextRenderer.getWidth(logo);
        TextRenderer.drawString(ver, context,
                px + 14 + logoW + 4, py + TITLE_H / 2 + 3,
                new Color(70, 70, 80, 200).getRGB());

        // ── 5. Search bar in title bar ────────────────────────────────────────
        int searchW = 180, searchH = 20;
        int searchX = px + PANEL_W / 2 - searchW / 2;
        int searchY = py + TITLE_H / 2 - searchH / 2;
        renderSearchBar(context, fmx, fmy, searchX, searchY, searchW, searchH);

        // ── 6. Configs button in title bar ────────────────────────────────────
        int cfgBtnX = px + PANEL_W - 72, cfgBtnY = py + TITLE_H / 2 - 10;
        int cfgBtnW = 60, cfgBtnH = 20;
        boolean cfgBtnHov = fmx >= cfgBtnX && fmx <= cfgBtnX + cfgBtnW
                && fmy >= cfgBtnY && fmy <= cfgBtnY + cfgBtnH;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                showConfigs ? new Color(25, 25, 30, 230)
                            : cfgBtnHov ? new Color(22, 22, 28, 220) : new Color(16, 16, 20, 200),
                cfgBtnX, cfgBtnY, cfgBtnX + cfgBtnW, cfgBtnY + cfgBtnH,
                4, 4, 4, 4, 10);
        if (showConfigs || cfgBtnHov)
            RenderUtils.renderRoundedOutline(context,
                    Utils.getMainColor(showConfigs ? 200 : 120, 2),
                    cfgBtnX, cfgBtnY, cfgBtnX + cfgBtnW, cfgBtnY + cfgBtnH,
                    4, 4, 4, 4, 1.0, 8);
        CharSequence cfgLbl = EncryptedString.of("\u2699 Configs");
        int cfgLblW = TextRenderer.getWidth(cfgLbl);
        TextRenderer.drawString(cfgLbl, context,
                cfgBtnX + cfgBtnW / 2 - cfgLblW / 2, cfgBtnY + cfgBtnH / 2 + 3,
                showConfigs ? Utils.getMainColor(220, 1).getRGB() : new Color(160, 160, 170).getRGB());

        // ── 7. Sidebar ────────────────────────────────────────────────────────
        renderSidebar(context, fmx, fmy, px, cy, ch);

        // ── 8. Divider ────────────────────────────────────────────────────────
        context.fillGradient(px + SIDEBAR_W, cy, px + SIDEBAR_W + DIVIDER, py + PANEL_H,
                Utils.getMainColor(60, 0).getRGB(),
                Utils.getMainColor(30, 3).getRGB());

        // ── 9. Active module window ───────────────────────────────────────────
        Window activeWindow = windows.get(selectedCategoryIdx);
        activeWindow.setPanel(rx, cy, rw, ch);
        activeWindow.render(context, fmx, fmy, delta);

        // ── 10. Config manager overlay ────────────────────────────────────────
        if (showConfigs) renderConfigOverlay(context, fmx, fmy);

        // ── 11. Toast ─────────────────────────────────────────────────────────
        renderToast(context);

        RenderUtils.scaledProjection();
    }

    // ── Sidebar rendering ─────────────────────────────────────────────────────

    private void renderSidebar(DrawContext context, int mx, int my,
                                int px, int cy, int ch) {
        Category[] cats = Category.values();
        int tabH = ch / cats.length;

        for (int i = 0; i < cats.length; i++) {
            int tabX = px;
            int tabY = cy + i * tabH;
            int tabW = SIDEBAR_W;
            boolean selected = i == selectedCategoryIdx;
            boolean hov = mx >= tabX && mx <= tabX + tabW
                       && my >= tabY && my <= tabY + tabH;

            // Smooth hover animation
            float targetHov = hov ? 1f : 0f;
            tabHover[i] += (targetHov - tabHover[i]) * 0.15f;

            // Tab background
            int bgAlpha = (int)(selected ? 28 : tabHover[i] * 18);
            if (bgAlpha > 0)
                context.fill(tabX, tabY, tabX + tabW, tabY + tabH,
                        new Color(255, 255, 255, bgAlpha).getRGB());

            // Left accent bar (only when selected)
            if (selected) {
                context.fillGradient(
                        tabX, tabY + 8,
                        tabX + 3, tabY + tabH - 8,
                        Utils.getMainColor(220, i).getRGB(),
                        Utils.getMainColor(180, i + 1).getRGB());
            }

            // Thin bottom separator
            if (i < cats.length - 1)
                context.fill(tabX + 12, tabY + tabH - 1, tabX + tabW - 12, tabY + tabH,
                        new Color(255, 255, 255, 10).getRGB());

            // Icon + label
            String icon = getCategoryIcon(cats[i]);
            CharSequence iconSeq  = EncryptedString.of(icon + " ");
            CharSequence labelSeq = cats[i].name;
            int iconW  = TextRenderer.getWidth(iconSeq);
            int labelW = TextRenderer.getWidth(labelSeq);
            int totalW = iconW + labelW;
            int textX  = tabX + SIDEBAR_W / 2 - totalW / 2;
            int textY  = tabY + tabH / 2 + 3;

            int iconColor = selected
                    ? Utils.getMainColor(230, i).getRGB()
                    : new Color(80, 80, 90, (int)(160 + tabHover[i] * 60)).getRGB();
            int nameColor = selected
                    ? Color.WHITE.getRGB()
                    : new Color(140, 140, 150, (int)(180 + tabHover[i] * 60)).getRGB();

            TextRenderer.drawString(iconSeq,  context, textX,         textY, iconColor);
            TextRenderer.drawString(labelSeq, context, textX + iconW, textY, nameColor);

            // Module count badge (small number, right-aligned)
            int modCount  = Argon.INSTANCE.getModuleManager().getModulesInCategory(cats[i]).size();
            CharSequence badge = EncryptedString.of(String.valueOf(modCount));
            int badgeColor = selected
                    ? Utils.getMainColor(150, i).getRGB()
                    : new Color(60, 60, 68).getRGB();
            TextRenderer.drawString(badge, context,
                    tabX + tabW - TextRenderer.getWidth(badge) - 8,
                    textY, badgeColor);
        }
    }

    // ── Search bar rendering ──────────────────────────────────────────────────

    private void renderSearchBar(DrawContext context, int mx, int my,
                                  int sx, int sy, int sw, int sh) {
        boolean hov = mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(8, 8, 11, 220),
                sx, sy, sx + sw, sy + sh, 4, 4, 4, 4, 10);
        RenderUtils.renderRoundedOutline(context,
                searchFocused ? Utils.getMainColor(170, 0)
                              : hov ? new Color(60, 60, 68, 160) : new Color(35, 35, 42, 130),
                sx, sy, sx + sw, sy + sh, 4, 4, 4, 4, 1.0, 8);

        CharSequence icon = EncryptedString.of("\u2315 ");
        int iconW = TextRenderer.getWidth(icon);
        TextRenderer.drawString(icon, context, sx + 6, sy + sh / 2 + 3,
                new Color(75, 75, 85).getRGB());

        boolean cursor  = searchFocused && (System.currentTimeMillis() % 900 < 450);
        String  rawText = searchQuery + (cursor ? "|" : "");
        boolean isEmpty = searchQuery.isEmpty() && !searchFocused;
        CharSequence display = isEmpty ? EncryptedString.of("Search modules & settings...")
                                       : EncryptedString.of(rawText);
        int textColor = isEmpty ? new Color(60, 60, 70).getRGB() : Color.WHITE.getRGB();
        TextRenderer.drawString(display, context, sx + 6 + iconW, sy + sh / 2 + 3, textColor);

        // Result count
        if (!searchQuery.isEmpty()) {
            int total = windows.stream()
                    .mapToInt(w -> (int) w.moduleButtons.stream()
                            .filter(mb -> mb.matchesSearch(searchQuery)).count())
                    .sum();
            CharSequence cnt = EncryptedString.of(total + "");
            TextRenderer.drawString(cnt, context,
                    sx + sw - TextRenderer.getWidth(cnt) - 6,
                    sy + sh / 2 + 3, Utils.getMainColor(160, 1).getRGB());
        }
    }

    // ── Config Manager overlay ────────────────────────────────────────────────

    private static final int CFG_W = 360, CFG_H = 310;
    private static final int CFG_ROW_H = 30;

    private void renderConfigOverlay(DrawContext context, int mx, int my) {
        int fw  = mc.getWindow().getFramebufferWidth();
        int fh  = mc.getWindow().getFramebufferHeight();
        int cx  = fw / 2 - CFG_W / 2;
        int cy  = fh / 2 - CFG_H / 2;

        // Dark backdrop
        context.fill(0, 0, fw, fh, new Color(0, 0, 0, 100).getRGB());

        // Panel
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(12, 12, 16, 252),
                cx, cy, cx + CFG_W, cy + CFG_H,
                RADIUS, RADIUS, RADIUS, RADIUS, 60);
        RenderUtils.renderRoundedOutline(context,
                Utils.getMainColor(100, 0),
                cx, cy, cx + CFG_W, cy + CFG_H,
                RADIUS, RADIUS, RADIUS, RADIUS, 1.2, 10);

        // Title bar
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(16, 16, 20, 255),
                cx, cy, cx + CFG_W, cy + 34,
                RADIUS, RADIUS, 0, 0, 60);
        context.fillGradient(cx + RADIUS, cy + 33, cx + CFG_W - RADIUS, cy + 34,
                Utils.getMainColor(180, 0).getRGB(), Utils.getMainColor(180, 4).getRGB());

        CharSequence title = EncryptedString.of("\u2699  Config Manager");
        TextRenderer.drawString(title, context, cx + 12, cy + 18 + 3,
                Utils.getMainColor(220, 0).getRGB());

        // Active config label
        CharSequence activeLbl = EncryptedString.of("Active: " + Argon.INSTANCE.configManager.getActiveConfig());
        int alW = TextRenderer.getWidth(activeLbl);
        TextRenderer.drawString(activeLbl, context, cx + CFG_W - alW - 10, cy + 18 + 3,
                new Color(80, 80, 95).getRGB());

        // Close button
        CharSequence close = EncryptedString.of("\u00D7");
        boolean closeHov = mx >= cx + CFG_W - 22 && mx <= cx + CFG_W - 8
                        && my >= cy + 10 && my <= cy + 26;
        TextRenderer.drawString(close, context, cx + CFG_W - 16, cy + 20 + 3,
                closeHov ? Color.WHITE.getRGB() : new Color(120, 120, 130).getRGB());

        // ── New config input ──────────────────────────────────────────────────
        int inputY = cy + 44;
        int inputW = CFG_W - 90, inputH = 24;
        int inputX = cx + 10;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(8, 8, 11, 220), inputX, inputY, inputX + inputW, inputY + inputH,
                4, 4, 4, 4, 10);
        RenderUtils.renderRoundedOutline(context,
                cfgInputFocused ? Utils.getMainColor(160, 0) : new Color(38, 38, 46, 160),
                inputX, inputY, inputX + inputW, inputY + inputH, 4, 4, 4, 4, 1.0, 8);
        boolean inputCursor = cfgInputFocused && (System.currentTimeMillis() % 900 < 450);
        String inputDisplay = cfgNewName.isEmpty() && !cfgInputFocused ? "Config name..."
                : cfgNewName + (inputCursor ? "|" : "");
        int inputTextColor = cfgNewName.isEmpty() && !cfgInputFocused
                ? new Color(55, 55, 65).getRGB() : Color.WHITE.getRGB();
        TextRenderer.drawString(EncryptedString.of(inputDisplay), context,
                inputX + 7, inputY + inputH / 2 + 3, inputTextColor);

        // Save button
        int saveBtnX = inputX + inputW + 6, saveBtnW = CFG_W - inputW - 22;
        boolean savHov = mx >= saveBtnX && mx <= saveBtnX + saveBtnW
                      && my >= inputY && my <= inputY + inputH;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                savHov ? new Color(22, 22, 28, 230) : new Color(16, 16, 20, 210),
                saveBtnX, inputY, saveBtnX + saveBtnW, inputY + inputH, 4, 4, 4, 4, 10);
        if (savHov) RenderUtils.renderRoundedOutline(context,
                Utils.getMainColor(150, 0),
                saveBtnX, inputY, saveBtnX + saveBtnW, inputY + inputH, 4, 4, 4, 4, 1.0, 8);
        CharSequence saveLbl = EncryptedString.of("Save");
        TextRenderer.drawString(saveLbl, context,
                saveBtnX + saveBtnW / 2 - TextRenderer.getWidth(saveLbl) / 2, inputY + inputH / 2 + 3,
                savHov ? Utils.getMainColor(220, 0).getRGB() : new Color(160, 160, 170).getRGB());

        // ── Config list ───────────────────────────────────────────────────────
        int listTop    = inputY + inputH + 8;
        int listH      = CFG_H - (listTop - cy) - 8;
        int listBottom = listTop + listH;

        context.fill(cx + 8, listTop, cx + CFG_W - 8, listBottom,
                new Color(8, 8, 11, 120).getRGB());

        cfgHovered = -1;
        int rowY   = listTop + 4 - cfgScrollOffset;
        for (int i = 0; i < cfgList.size(); i++) {
            if (rowY + CFG_ROW_H < listTop) { rowY += CFG_ROW_H; continue; }
            if (rowY > listBottom) break;

            String name    = cfgList.get(i);
            boolean isAct  = name.equals(Argon.INSTANCE.configManager.getActiveConfig());
            boolean hov    = mx >= cx + 8 && mx <= cx + CFG_W - 8
                          && my >= rowY && my <= rowY + CFG_ROW_H;
            if (hov) cfgHovered = i;

            // Row bg
            if (hov || isAct) {
                int rowAlpha = isAct ? 35 : 20;
                context.fill(cx + 10, rowY, cx + CFG_W - 10, rowY + CFG_ROW_H - 2,
                        new Color(255, 255, 255, rowAlpha).getRGB());
            }
            if (isAct) {
                context.fill(cx + 10, rowY, cx + 13, rowY + CFG_ROW_H - 2,
                        Utils.getMainColor(200, i).getRGB());
            }

            // Config name
            CharSequence nameSeq = EncryptedString.of(name);
            TextRenderer.drawString(nameSeq, context, cx + 16, rowY + CFG_ROW_H / 2 + 3,
                    isAct ? Utils.getMainColor(230, i).getRGB() : new Color(180, 180, 190).getRGB());

            // Action buttons (Load | Delete)
            if (hov) {
                boolean pendingDel = cfgDeleteConfirm == i;

                // Load button
                CharSequence loadLbl = EncryptedString.of("Load");
                int loadW   = TextRenderer.getWidth(loadLbl) + 10;
                int loadX   = cx + CFG_W - 82;
                boolean loadHov = mx >= loadX && mx <= loadX + loadW;
                context.fill(loadX, rowY + 4, loadX + loadW, rowY + CFG_ROW_H - 4,
                        new Color(20, 20, 26, 200).getRGB());
                if (loadHov) RenderUtils.renderRoundedOutline(context,
                        Utils.getMainColor(130, 0),
                        loadX, rowY + 4, loadX + loadW, rowY + CFG_ROW_H - 4,
                        3, 3, 3, 3, 1.0, 6);
                TextRenderer.drawString(loadLbl, context, loadX + 5, rowY + CFG_ROW_H / 2 + 3,
                        loadHov ? Utils.getMainColor(220, 0).getRGB() : new Color(150, 150, 160).getRGB());

                // Delete button
                CharSequence delLbl = pendingDel
                        ? EncryptedString.of("Sure?")
                        : EncryptedString.of("\u2715");
                int delW = TextRenderer.getWidth(delLbl) + 10;
                int delX = cx + CFG_W - delW - 12;
                // don't overlap with load
                delX = Math.max(loadX + loadW + 4, delX);
                boolean delHov = mx >= delX && mx <= delX + delW;
                context.fill(delX, rowY + 4, delX + delW, rowY + CFG_ROW_H - 4,
                        pendingDel ? new Color(80, 20, 20, 200).getRGB() : new Color(20, 20, 26, 200).getRGB());
                if (delHov) RenderUtils.renderRoundedOutline(context,
                        pendingDel ? new Color(200, 60, 60, 180) : new Color(120, 40, 40, 150),
                        delX, rowY + 4, delX + delW, rowY + CFG_ROW_H - 4,
                        3, 3, 3, 3, 1.0, 6);
                TextRenderer.drawString(delLbl, context, delX + 5, rowY + CFG_ROW_H / 2 + 3,
                        delHov ? new Color(240, 100, 100).getRGB() : new Color(150, 60, 60).getRGB());
            }

            rowY += CFG_ROW_H;
        }

        if (cfgList.isEmpty()) {
            CharSequence empty = EncryptedString.of("No saved configs yet");
            int ew = TextRenderer.getWidth(empty);
            TextRenderer.drawString(empty, context,
                    cx + CFG_W / 2 - ew / 2, listTop + listH / 2 + 3,
                    new Color(60, 60, 70).getRGB());
        }
    }

    // ── Toast ─────────────────────────────────────────────────────────────────

    private void renderToast(DrawContext context) {
        if (toastTime == 0) return;
        long elapsed = System.currentTimeMillis() - toastTime;
        if (elapsed >= TOAST_MS) return;
        float prog = (float) elapsed / TOAST_MS;
        int alpha = prog < 0.12f ? (int)(prog / 0.12f * 200)
                  : prog > 0.78f ? (int)((1f - (prog - 0.78f) / 0.22f) * 200)
                  : 200;
        int fw = mc.getWindow().getFramebufferWidth();
        int fh = mc.getWindow().getFramebufferHeight();
        CharSequence msg = EncryptedString.of(toastMsg);
        int tw = TextRenderer.getWidth(msg) + 22;
        int tx = fw / 2 - tw / 2;
        int ty = fh - 42;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(12, 12, 16, Math.min(alpha, 200)),
                tx, ty, tx + tw, ty + 22, 4, 4, 4, 4, 10);
        context.fillGradient(tx + 4, ty, tx + tw - 4, ty + 2,
                Utils.getMainColor(alpha, 0).getRGB(), Utils.getMainColor(alpha, 3).getRGB());
        TextRenderer.drawString(msg, context,
                tx + tw / 2 - TextRenderer.getWidth(msg) / 2, ty + 14,
                new Color(200, 200, 210, alpha).getRGB());
    }

    private void showToast(String msg) {
        toastMsg  = msg;
        toastTime = System.currentTimeMillis();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+S → save
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            quickSave();
            return true;
        }
        // ESC
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showConfigs) { showConfigs = false; return true; }
            if (cfgInputFocused) { cfgInputFocused = false; return true; }
            if (searchFocused && !searchQuery.isEmpty()) { searchQuery = ""; return true; }
            if (searchFocused) { searchFocused = false; return true; }
        }
        // Backspace
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cfgInputFocused && !cfgNewName.isEmpty()) {
                cfgNewName = cfgNewName.substring(0, cfgNewName.length() - 1);
                return true;
            }
            if (searchFocused && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
        }
        // Enter → save new config if input focused
        if (keyCode == GLFW.GLFW_KEY_ENTER && cfgInputFocused && !cfgNewName.isEmpty()) {
            Argon.INSTANCE.configManager.saveConfig(cfgNewName);
            cfgList = Argon.INSTANCE.configManager.listConfigs();
            showToast("\u2713  Saved \"" + cfgNewName + "\"");
            cfgNewName = ""; cfgInputFocused = false;
            return true;
        }
        windows.get(selectedCategoryIdx).keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (cfgInputFocused && chr >= 32 && chr != 127) {
            if (cfgNewName.length() < 24) cfgNewName += chr;
            return true;
        }
        if (searchFocused && chr >= 32 && chr != 127) {
            searchQuery += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int fmx = (int)(mouseX * mc.getWindow().getScaleFactor());
        int fmy = (int)(mouseY * mc.getWindow().getScaleFactor());
        int fw  = mc.getWindow().getFramebufferWidth();
        int fh  = mc.getWindow().getFramebufferHeight();

        // ── Config overlay input ──────────────────────────────────────────────
        if (showConfigs) {
            int cx = fw / 2 - CFG_W / 2;
            int cy = fh / 2 - CFG_H / 2;

            // Close button
            if (fmx >= cx + CFG_W - 22 && fmx <= cx + CFG_W - 8
                    && fmy >= cy + 10 && fmy <= cy + 26) {
                showConfigs = false; return true;
            }
            // Input field click
            int inputY = cy + 44;
            int inputW = CFG_W - 90, inputH = 24;
            int inputX = cx + 10;
            if (fmx >= inputX && fmx <= inputX + inputW
                    && fmy >= inputY && fmy <= inputY + inputH) {
                cfgInputFocused = true; return true;
            }
            // Save button click
            int saveBtnX = inputX + inputW + 6, saveBtnW = CFG_W - inputW - 22;
            if (fmx >= saveBtnX && fmx <= saveBtnX + saveBtnW
                    && fmy >= inputY && fmy <= inputY + inputH) {
                if (!cfgNewName.isEmpty()) {
                    Argon.INSTANCE.configManager.saveConfig(cfgNewName);
                    cfgList = Argon.INSTANCE.configManager.listConfigs();
                    showToast("\u2713  Saved \"" + cfgNewName + "\"");
                    cfgNewName = ""; cfgInputFocused = false;
                }
                return true;
            }
            cfgInputFocused = false;

            // Config list clicks
            int listTop   = inputY + inputH + 8;
            int listH     = CFG_H - (listTop - cy) - 8;
            int rowY      = listTop + 4 - cfgScrollOffset;
            for (int i = 0; i < cfgList.size(); i++) {
                if (rowY + CFG_ROW_H < listTop) { rowY += CFG_ROW_H; continue; }
                if (rowY > listTop + listH) break;
                String name = cfgList.get(i);
                // Load button
                int loadW  = 34, loadX  = cx + CFG_W - 82;
                if (fmx >= loadX && fmx <= loadX + loadW
                        && fmy >= rowY + 4 && fmy <= rowY + CFG_ROW_H - 4) {
                    if (Argon.INSTANCE.configManager.loadConfig(name))
                        showToast("\u2713  Loaded \"" + name + "\"");
                    cfgDeleteConfirm = -1;
                    return true;
                }
                // Delete button
                int delW = 38, delX  = cx + CFG_W - delW - 12;
                if (fmx >= delX && fmx <= delX + delW
                        && fmy >= rowY + 4 && fmy <= rowY + CFG_ROW_H - 4) {
                    if (cfgDeleteConfirm == i) {
                        Argon.INSTANCE.configManager.deleteConfig(name);
                        cfgList = Argon.INSTANCE.configManager.listConfigs();
                        showToast("\u2715  Deleted \"" + name + "\"");
                        cfgDeleteConfirm = -1;
                    } else {
                        cfgDeleteConfirm = i;
                    }
                    return true;
                }
                rowY += CFG_ROW_H;
            }
            cfgDeleteConfirm = -1;
            return true;
        }

        int px = panelX(), py = panelY();
        int cy2 = contentY(), ch = contentH();

        // ── Search bar click ──────────────────────────────────────────────────
        int searchW = 180, searchH = 20;
        int searchX = px + PANEL_W / 2 - searchW / 2;
        int searchY = py + TITLE_H / 2 - searchH / 2;
        if (fmx >= searchX && fmx <= searchX + searchW
                && fmy >= searchY && fmy <= searchY + searchH) {
            searchFocused = true; cfgInputFocused = false; return true;
        }

        // ── Config button click ───────────────────────────────────────────────
        int cfgBtnX = px + PANEL_W - 72, cfgBtnY = py + TITLE_H / 2 - 10;
        int cfgBtnW = 60, cfgBtnH = 20;
        if (fmx >= cfgBtnX && fmx <= cfgBtnX + cfgBtnW
                && fmy >= cfgBtnY && fmy <= cfgBtnY + cfgBtnH) {
            showConfigs = !showConfigs;
            if (showConfigs) cfgList = Argon.INSTANCE.configManager.listConfigs();
            cfgDeleteConfirm = -1;
            return true;
        }

        // ── Category sidebar click ────────────────────────────────────────────
        Category[] cats = Category.values();
        int tabH = ch / cats.length;
        for (int i = 0; i < cats.length; i++) {
            int tabX = px, tabY = cy2 + i * tabH;
            if (fmx >= tabX && fmx <= tabX + SIDEBAR_W
                    && fmy >= tabY && fmy <= tabY + tabH) {
                if (selectedCategoryIdx != i) {
                    windows.get(selectedCategoryIdx).collapseAll();
                    selectedCategoryIdx = i;
                }
                searchFocused = false;
                return true;
            }
        }

        searchFocused = false;

        // ── Module list click ─────────────────────────────────────────────────
        windows.get(selectedCategoryIdx).mouseClicked(fmx, fmy, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        int fmx = (int)(mouseX * mc.getWindow().getScaleFactor());
        int fmy = (int)(mouseY * mc.getWindow().getScaleFactor());
        windows.get(selectedCategoryIdx).mouseDragged(fmx, fmy, button, dX, dY);
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int fmx = (int)(mouseX * mc.getWindow().getScaleFactor());
        int fmy = (int)(mouseY * mc.getWindow().getScaleFactor());
        windows.get(selectedCategoryIdx).mouseReleased(fmx, fmy, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        int fmx = (int)(mouseX * mc.getWindow().getScaleFactor());
        int fmy = (int)(mouseY * mc.getWindow().getScaleFactor());
        if (showConfigs) {
            cfgScrollOffset = (int)Math.max(0, cfgScrollOffset - vAmt * 20);
        } else {
            windows.get(selectedCategoryIdx).mouseScrolled(fmx, fmy, vAmt);
        }
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void quickSave() {
        try { Argon.INSTANCE.getProfileManager().saveProfile(); } catch (Exception ignored) {}
        showToast("\u2713  Config saved");
    }

    private static String getCategoryIcon(Category cat) {
        String n = cat.name.toString().toUpperCase();
        if (n.contains("COMBAT")) return "\u2694";
        if (n.contains("MISC"))   return "\u2699";
        if (n.contains("RENDER")) return "\u25A6";
        if (n.contains("CLIENT")) return "\u2605";
        if (n.contains("MOVE"))   return "\u27A4";
        return "\u25CF";
    }
}
