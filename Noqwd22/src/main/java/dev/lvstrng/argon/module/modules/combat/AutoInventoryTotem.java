package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.mixin.HandledScreenMixin;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.FakeInvScreen;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * AutoInventoryTotem — Argon Client
 *
 * Improvements over the original:
 *  - Silent inventory (FakeInvScreen) — opens inv without the server seeing it
 *  - Randomised totem slot selection — avoids pattern detection
 *  - Configurable random delay range — mimics human hand speed
 *  - Health threshold trigger — only acts when HP is dangerously low
 *  - HoverMode support ported from HoverTotem style
 *  - Auto Switch + Force Totem retained from original
 *  - Proper packet close after silent swap to avoid ghost-open detection
 */
public final class AutoInventoryTotem extends Module implements TickListener {

    // ── Settings ──────────────────────────────────────────────────────────────

    private final BooleanSetting hoverMode = new BooleanSetting(
            EncryptedString.of("Hover Mode"), false)
            .setDescription(EncryptedString.of("Only swaps when your cursor is over a totem in inventory"));

    private final BooleanSetting silentInv = new BooleanSetting(
            EncryptedString.of("Silent Inv"), true)
            .setDescription(EncryptedString.of("Swaps totem without visually opening your inventory (better AC bypass)"));

    private final BooleanSetting totemOnInv = new BooleanSetting(
            EncryptedString.of("Totem On Inv"), true)
            .setDescription(EncryptedString.of("Switches hotbar to totem slot when you open your inventory"));

    private final BooleanSetting autoSwitch = new BooleanSetting(
            EncryptedString.of("Auto Switch"), false)
            .setDescription(EncryptedString.of("Automatically switches hotbar to your totem slot"));

    private final BooleanSetting forceTotem = new BooleanSetting(
            EncryptedString.of("Force Totem"), false)
            .setDescription(EncryptedString.of("Replaces the totem slot item even if it is not empty"));

    private final BooleanSetting randomiseSlot = new BooleanSetting(
            EncryptedString.of("Randomise Slot"), true)
            .setDescription(EncryptedString.of("Picks a random totem from inventory instead of always the first one (AC bypass)"));

    private final BooleanSetting healthTrigger = new BooleanSetting(
            EncryptedString.of("Health Trigger"), false)
            .setDescription(EncryptedString.of("Only refills totem when health drops below the threshold"));

    private final NumberSetting healthThreshold = new NumberSetting(
            EncryptedString.of("Health Threshold"), 10, 20, 6, 1)
            .setDescription(EncryptedString.of("HP at which the health trigger activates (half-hearts)"));

    private final NumberSetting minDelay = new NumberSetting(
            EncryptedString.of("Min Delay"), 1, 10, 0, 1)
            .setDescription(EncryptedString.of("Minimum ticks to wait before swapping (AC bypass)"));

    private final NumberSetting maxDelay = new NumberSetting(
            EncryptedString.of("Max Delay"), 3, 20, 1, 1)
            .setDescription(EncryptedString.of("Maximum ticks to wait before swapping (AC bypass)"));

    private final NumberSetting totemSlot = new NumberSetting(
            EncryptedString.of("Totem Slot"), 1, 9, 1, 1)
            .setDescription(EncryptedString.of("Hotbar slot (1-9) where you want your totem"));

    // ── State ─────────────────────────────────────────────────────────────────

    private final Random rng = new Random();
    private int clock = -1;       // countdown; -1 = waiting for next trigger
    private int targetDelay = 0;  // randomised delay picked each cycle
    private boolean swappedThisCycle = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AutoInventoryTotem() {
        super(
                EncryptedString.of("Auto Inv Totem"),
                EncryptedString.of("Silently refills totem in offhand & hotbar from inventory with AC bypass"),
                -1,
                Category.COMBAT
        );
        addSettings(
                hoverMode, silentInv, totemOnInv, autoSwitch,
                forceTotem, randomiseSlot, healthTrigger, healthThreshold,
                minDelay, maxDelay, totemSlot
        );
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    // ── Main tick logic ───────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        int hotbarSlot = totemSlot.getValueInt() - 1; // convert 1-9 → 0-8

        // ── Auto Switch: switch hotbar to totem slot when inv is open ──────
        if (totemOnInv.getValue() && mc.currentScreen instanceof InventoryScreen) {
            mc.player.getInventory().selectedSlot = hotbarSlot;
        }

        // ── Health trigger guard ───────────────────────────────────────────
        if (healthTrigger.getValue()) {
            float hp = mc.player.getHealth();
            if (hp > healthThreshold.getValue()) {
                reset();
                return;
            }
        }

        // ── Already has totem in offhand and hotbar slot — nothing to do ──
        boolean offhandOk = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean hotbarOk  = mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING);

        if (offhandOk && (hotbarOk || !forceTotem.getValue())) {
            reset();
            return;
        }

        // ── Route: hover mode (manual inventory open) or silent mode ───────
        if (hoverMode.getValue()) {
            tickHoverMode(hotbarSlot);
        } else {
            tickSilentMode(hotbarSlot);
        }
    }

    // ── Hover Mode ────────────────────────────────────────────────────────────
    // User manually opens inventory; we read the hovered slot and swap if it's a totem.

    private void tickHoverMode(int hotbarSlot) {
        if (!(mc.currentScreen instanceof InventoryScreen inv)) {
            reset();
            return;
        }

        // Auto-switch hotbar selection
        if (autoSwitch.getValue())
            mc.player.getInventory().selectedSlot = hotbarSlot;

        // Delay countdown
        if (clock == -1) {
            targetDelay = randomDelay();
            clock = targetDelay;
        }
        if (clock > 0) { clock--; return; }

        Slot hovered = ((HandledScreenMixin) inv).getFocusedSlot();
        if (hovered == null) return;

        int idx = hovered.getIndex();
        if (idx < 0 || idx > 35) return;
        if (!hovered.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        boolean offhandOk = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        if (!offhandOk) {
            // Swap hovered totem → offhand (slot 40)
            mc.interactionManager.clickSlot(
                    inv.getScreenHandler().syncId,
                    idx, 40, SlotActionType.SWAP, mc.player
            );
        } else if (forceTotem.getValue()) {
            // Swap hovered totem → chosen hotbar slot
            mc.interactionManager.clickSlot(
                    inv.getScreenHandler().syncId,
                    idx, hotbarSlot, SlotActionType.SWAP, mc.player
            );
        }

        clock = randomDelay(); // reset for next cycle
    }

    // ── Silent Mode ───────────────────────────────────────────────────────────
    // Opens a FakeInvScreen (invisible) or uses raw slot clicks to swap
    // without the player visually seeing the inventory open.

    private void tickSilentMode(int hotbarSlot) {
        // Only act when NO screen is open (to avoid double-screen conflicts)
        if (mc.currentScreen != null && !(mc.currentScreen instanceof FakeInvScreen)) return;

        // Initialise delay for this cycle
        if (clock == -1) {
            targetDelay = randomDelay();
            clock = targetDelay;
        }
        if (clock > 0) { clock--; return; }
        if (swappedThisCycle) { finishSilentCycle(); return; }

        // Find a totem in inventory slots 9-35
        int totemIdx = randomiseSlot.getValue()
                ? InventoryUtils.findRandomTotemSlot()
                : InventoryUtils.findTotemSlot();

        if (totemIdx == -1) return; // no totems left

        // Open a silent fake inventory so clickSlot works
        FakeInvScreen fakeScreen = new FakeInvScreen(mc.player);
        mc.setScreen(fakeScreen);

        boolean offhandOk = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean hotbarOk  = mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING);

        if (!offhandOk) {
            // Move totem from inventory → offhand (slot 40 = offhand in player screen)
            mc.interactionManager.clickSlot(
                    fakeScreen.getScreenHandler().syncId,
                    totemIdx, 40, SlotActionType.SWAP, mc.player
            );
        } else if (forceTotem.getValue() && !hotbarOk) {
            // Move totem from inventory → target hotbar slot
            mc.interactionManager.clickSlot(
                    fakeScreen.getScreenHandler().syncId,
                    totemIdx, hotbarSlot, SlotActionType.SWAP, mc.player
            );
        }

        // Auto switch hotbar selection
        if (autoSwitch.getValue())
            mc.player.getInventory().selectedSlot = hotbarSlot;

        swappedThisCycle = true;
        clock = randomDelay(); // small pause before closing
    }

    /** Closes the silent screen and sends a close packet so the server knows. */
    private void finishSilentCycle() {
        if (mc.currentScreen instanceof FakeInvScreen fakeScreen) {
            // Send close packet — prevents ghost-screen detection on some anticheats
            mc.getNetworkHandler().sendPacket(
                    new CloseHandledScreenC2SPacket(fakeScreen.getScreenHandler().syncId)
            );
            mc.setScreen(null);
        }
        swappedThisCycle = false;
        clock = -1; // re-arm for next cycle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a random delay between minDelay and maxDelay (inclusive). */
    private int randomDelay() {
        int lo = minDelay.getValueInt();
        int hi = Math.max(lo, maxDelay.getValueInt());
        return lo + (hi > lo ? rng.nextInt(hi - lo + 1) : 0);
    }

    private void reset() {
        clock = -1;
        swappedThisCycle = false;
    }
}
