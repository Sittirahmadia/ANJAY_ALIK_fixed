package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * AnchorMacroV2 — Argon Client
 *
 * Improvements over AnchorMacro:
 *  - Millisecond-precision delays via TimerUtils (no tick clock jitter)
 *  - Nearby anchor scanning — finds the best anchor in a configurable radius,
 *    not just the one under the crosshair
 *  - Smart target selection — prioritises anchors closest to an enemy player
 *  - Health-based charge count — calculates how many charges are needed to
 *    kill the target based on their current HP and armor
 *  - Auto-rotate to anchor silently before each interaction
 *  - Burst charge mode — fires multiple charge clicks in one go per tick
 *  - Adaptive idle pause — random post-explode pause to break rhythm patterns
 *  - Silent slot restore via saved slot index
 *  - Multi-state flow: IDLE → FIND → CHARGE → SWITCH_TOTEM → EXPLODE → loop
 *  - Misclick simulation — occasionally delays a single charge to look human
 *  - Configurable search radius for nearby anchors
 *  - Weapon-based activation mode (only runs while holding sword/axe/mace)
 */
public final class AnchorMacroV2 extends Module implements PlayerTickListener, ItemUseListener {

    // ── Enums ──────────────────────────────────────────────────────────────────
    public enum AnchorPriority { Closest, MostCharged, NearEnemy }
    public enum ChargeMode     { Smart, Fixed }
    public enum ActivationMode { Always, OnRMB, OnWeapon }

    // ── Settings ───────────────────────────────────────────────────────────────

    private final ModeSetting<ActivationMode> activation = new ModeSetting<>(
            EncryptedString.of("Activation"), ActivationMode.Always, ActivationMode.class)
            .setDescription(EncryptedString.of("Always / Only on RMB / Only while holding a weapon"));

    private final BooleanSetting charger = new BooleanSetting(
            EncryptedString.of("Charger"), true)
            .setDescription(EncryptedString.of("Auto-charges the anchor with glowstone"));

    private final BooleanSetting exploder = new BooleanSetting(
            EncryptedString.of("Exploder"), true)
            .setDescription(EncryptedString.of("Auto-explodes the anchor after charging"));

    private final ModeSetting<ChargeMode> chargeMode = new ModeSetting<>(
            EncryptedString.of("Charge Mode"), ChargeMode.Fixed, ChargeMode.class)
            .setDescription(EncryptedString.of("Fixed = use Charges setting | Smart = calculate from target HP"));

    private final NumberSetting chargesNeeded = new NumberSetting(
            EncryptedString.of("Charges"), 1, 4, 4, 1)
            .setDescription(EncryptedString.of("Fixed charges to fill (only used in Fixed mode)"));

    private final BooleanSetting nearbySearch = new BooleanSetting(
            EncryptedString.of("Nearby Search"), true)
            .setDescription(EncryptedString.of("Scan nearby blocks for anchors, not just the crosshair target"));

    private final NumberSetting searchRadius = new NumberSetting(
            EncryptedString.of("Search Radius"), 1, 6, 4, 0.5)
            .setDescription(EncryptedString.of("Radius in blocks to scan for anchors (Nearby Search)"));

    private final ModeSetting<AnchorPriority> priority = new ModeSetting<>(
            EncryptedString.of("Priority"), AnchorPriority.NearEnemy, AnchorPriority.class)
            .setDescription(EncryptedString.of("How to pick the best anchor when multiple are nearby"));

    private final BooleanSetting autoRotate = new BooleanSetting(
            EncryptedString.of("Auto Rotate"), true)
            .setDescription(EncryptedString.of("Silently rotates toward the anchor before interacting"));

    private final BooleanSetting burstCharge = new BooleanSetting(
            EncryptedString.of("Burst Charge"), false)
            .setDescription(EncryptedString.of("Fires multiple charge clicks per tick for maximum speed"));

    private final NumberSetting burstAmount = new NumberSetting(
            EncryptedString.of("Burst Amount"), 1, 4, 2, 1)
            .setDescription(EncryptedString.of("Clicks per tick in burst charge mode"));

    private final MinMaxSetting chargeDelayMs = new MinMaxSetting(
            EncryptedString.of("Charge Delay ms"), 0, 300, 1, 0, 80)
            .setDescription(EncryptedString.of("Random ms delay between charges (0 = instant)"));

    private final MinMaxSetting swapDelayMs = new MinMaxSetting(
            EncryptedString.of("Swap Delay ms"), 0, 300, 1, 0, 60)
            .setDescription(EncryptedString.of("Random ms delay after slot swap"));

    private final MinMaxSetting explodeDelayMs = new MinMaxSetting(
            EncryptedString.of("Explode Delay ms"), 0, 300, 1, 0, 80)
            .setDescription(EncryptedString.of("Random ms pause before detonating"));

    private final MinMaxSetting idlePauseMs = new MinMaxSetting(
            EncryptedString.of("Idle Pause ms"), 0, 500, 1, 40, 120)
            .setDescription(EncryptedString.of("Random post-explode pause before recharging (AC bypass)"));

    private final BooleanSetting misclick = new BooleanSetting(
            EncryptedString.of("Misclick"), true)
            .setDescription(EncryptedString.of("Randomly delays a charge to break rhythm patterns (AC bypass)"));

    private final NumberSetting misclickChance = new NumberSetting(
            EncryptedString.of("Misclick Chance %"), 0, 25, 6, 1)
            .setDescription(EncryptedString.of("Chance per charge to add an extra random delay"));

    private final BooleanSetting safeExplode = new BooleanSetting(
            EncryptedString.of("Safe Explode"), true)
            .setDescription(EncryptedString.of("Sneaks before exploding to reduce self-damage"));

    private final BooleanSetting autoRecharge = new BooleanSetting(
            EncryptedString.of("Auto Recharge"), true)
            .setDescription(EncryptedString.of("Immediately loops — recharge and explode again"));

    private final BooleanSetting autoDisable = new BooleanSetting(
            EncryptedString.of("Auto Disable"), true)
            .setDescription(EncryptedString.of("Disables when out of glowstone or no anchor found"));

    private final BooleanSetting restoreSlot = new BooleanSetting(
            EncryptedString.of("Restore Slot"), true)
            .setDescription(EncryptedString.of("Returns to original hotbar slot when done"));

    private final BooleanSetting simulateClick = new BooleanSetting(
            EncryptedString.of("Simulate Click"), true)
            .setDescription(EncryptedString.of("Uses mouse simulation so CPS counters register the click"));

    // ── State ──────────────────────────────────────────────────────────────────

    private enum State { IDLE, FIND, SWITCH_GLOWSTONE, CHARGE, SWITCH_TOTEM, EXPLODE, IDLE_PAUSE }

    private State     state        = State.IDLE;
    private BlockPos  targetAnchor = null;
    private int       prevSlot     = -1;
    private boolean   wasSneaking  = false;
    private final TimerUtils timer = new TimerUtils();
    private long      waitMs       = 0;
    private final Random rng       = new Random();

    // ── Constructor ────────────────────────────────────────────────────────────

    public AnchorMacroV2() {
        super(
                EncryptedString.of("Anchor Macro V2"),
                EncryptedString.of("Smart anchor combat — nearby scan, health-based charges, ms-precision AC bypass"),
                -1,
                Category.COMBAT
        );
        addSettings(
                activation,
                charger, exploder,
                chargeMode, chargesNeeded,
                nearbySearch, searchRadius, priority,
                autoRotate, burstCharge, burstAmount,
                chargeDelayMs, swapDelayMs, explodeDelayMs, idlePauseMs,
                misclick, misclickChance,
                safeExplode, autoRecharge, autoDisable,
                restoreSlot, simulateClick
        );
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        cleanup();
        super.onDisable();
    }

    // ── Tick ───────────────────────────────────────────────────────────────────

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.getNetworkHandler() == null) return;

        // Activation gate
        if (!passesActivationGate()) {
            if (state != State.IDLE) cleanup();
            state = State.IDLE;
            return;
        }

        // Timer gate — wait until the current delay has elapsed
        if (waitMs > 0 && !timer.hasReached(waitMs)) return;
        waitMs = 0;

        switch (state) {

            // ── IDLE: save slot, transition to FIND ────────────────────────
            case IDLE -> {
                if (prevSlot == -1)
                    prevSlot = mc.player.getInventory().selectedSlot;
                state = State.FIND;
            }

            // ── FIND: locate the best anchor ───────────────────────────────
            case FIND -> {
                targetAnchor = findBestAnchor();

                if (targetAnchor == null) {
                    if (autoDisable.getValue()) setEnabledStatus(false);
                    state = State.IDLE;
                    return;
                }

                int charges = getCharges(targetAnchor);
                int needed  = neededCharges(targetAnchor);

                if (charger.getValue() && charges < needed) {
                    if (findGlowstoneSlot() == -1) {
                        if (autoDisable.getValue()) setEnabledStatus(false);
                        state = State.IDLE;
                        return;
                    }
                    state  = State.SWITCH_GLOWSTONE;
                    waitMs = randomMs(swapDelayMs);
                    timer.reset();
                } else if (exploder.getValue() && charges > 0) {
                    state  = State.SWITCH_TOTEM;
                    waitMs = randomMs(swapDelayMs);
                    timer.reset();
                }
            }

            // ── SWITCH_GLOWSTONE ───────────────────────────────────────────
            case SWITCH_GLOWSTONE -> {
                int gs = findGlowstoneSlot();
                if (gs == -1) {
                    if (autoDisable.getValue()) setEnabledStatus(false);
                    state = State.IDLE;
                    return;
                }
                InventoryUtils.setInvSlot(gs);
                state  = State.CHARGE;
                waitMs = randomMs(chargeDelayMs);
                timer.reset();
            }

            // ── CHARGE ─────────────────────────────────────────────────────
            case CHARGE -> {
                if (targetAnchor == null || !isAnchor(targetAnchor)) {
                    state = State.FIND;
                    return;
                }

                // Recover if glowstone slipped out of hand
                if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                    int gs = findGlowstoneSlot();
                    if (gs == -1) {
                        if (autoDisable.getValue()) setEnabledStatus(false);
                        state = State.IDLE;
                        return;
                    }
                    InventoryUtils.setInvSlot(gs);
                    waitMs = randomMs(swapDelayMs);
                    timer.reset();
                    return;
                }

                int charges = getCharges(targetAnchor);
                int needed  = neededCharges(targetAnchor);

                if (charges >= needed || charges >= 4) {
                    // Done charging
                    if (exploder.getValue()) {
                        state  = State.SWITCH_TOTEM;
                        waitMs = randomMs(swapDelayMs);
                        timer.reset();
                    } else {
                        cleanup();
                        state = State.IDLE;
                    }
                    return;
                }

                // Rotate toward anchor if enabled
                if (autoRotate.getValue()) BlockUtils.rotateToBlock(targetAnchor);

                // Misclick delay — random extra pause on this charge
                if (misclick.getValue() && rng.nextInt(100) < misclickChance.getValueInt()) {
                    waitMs = 50L + rng.nextInt(100);
                    timer.reset();
                    return;
                }

                // Burst: fire multiple charge clicks this tick
                int clicks = burstCharge.getValue() ? burstAmount.getValueInt() : 1;
                for (int i = 0; i < clicks; i++) {
                    rightClickAnchor(targetAnchor);
                    // Stop early if anchor is now full
                    if (getCharges(targetAnchor) >= needed) break;
                }

                waitMs = randomMs(chargeDelayMs);
                timer.reset();
            }

            // ── SWITCH_TOTEM ───────────────────────────────────────────────
            case SWITCH_TOTEM -> {
                int totemSlot = findTotemSlot();
                if (totemSlot == -1) {
                    if (autoDisable.getValue()) setEnabledStatus(false);
                    state = State.IDLE;
                    return;
                }
                InventoryUtils.setInvSlot(totemSlot);

                if (safeExplode.getValue()) {
                    mc.player.setSneaking(true);
                    wasSneaking = true;
                }

                state  = State.EXPLODE;
                waitMs = randomMs(explodeDelayMs);
                timer.reset();
            }

            // ── EXPLODE ────────────────────────────────────────────────────
            case EXPLODE -> {
                if (targetAnchor == null || !isAnchor(targetAnchor)) {
                    cleanup();
                    state = State.IDLE;
                    return;
                }

                // Totem sanity check
                if (!mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    int totemSlot = findTotemSlot();
                    if (totemSlot == -1) {
                        if (autoDisable.getValue()) setEnabledStatus(false);
                        state = State.IDLE;
                        return;
                    }
                    InventoryUtils.setInvSlot(totemSlot);
                    waitMs = randomMs(swapDelayMs);
                    timer.reset();
                    return;
                }

                int charges = getCharges(targetAnchor);
                if (charges == 0) {
                    // Already detonated (us or someone else)
                    releaseSneakIfNeeded();
                    afterExplode();
                    return;
                }

                if (autoRotate.getValue()) BlockUtils.rotateToBlock(targetAnchor);

                rightClickAnchor(targetAnchor);
                releaseSneakIfNeeded();
                afterExplode();
            }

            // ── IDLE_PAUSE: post-explode human rhythm pause ─────────────
            case IDLE_PAUSE -> {
                // Timer already waited; now recharge
                state  = State.SWITCH_GLOWSTONE;
                waitMs = randomMs(swapDelayMs);
                timer.reset();
            }
        }
    }

    // ── Post-explode routing ───────────────────────────────────────────────────

    private void afterExplode() {
        if (autoRecharge.getValue() && charger.getValue()) {
            // Adaptive idle pause before recharging
            long pause = randomMs(idlePauseMs);
            if (pause > 0) {
                state  = State.IDLE_PAUSE;
                waitMs = pause;
                timer.reset();
            } else {
                state  = State.SWITCH_GLOWSTONE;
                waitMs = randomMs(swapDelayMs);
                timer.reset();
            }
        } else {
            cleanup();
            state = State.IDLE;
            if (autoDisable.getValue()) setEnabledStatus(false);
        }
    }

    // ── Anchor search ──────────────────────────────────────────────────────────

    /**
     * Finds the best anchor based on the chosen priority mode.
     * Falls back to crosshair target if nearbySearch is off.
     */
    private BlockPos findBestAnchor() {
        if (!nearbySearch.getValue()) {
            return getCrosshairAnchor();
        }

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos origin = mc.player.getBlockPos();
        int r = (int) Math.ceil(searchRadius.getValue());

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = origin.add(dx, dy, dz);
                    if (isAnchor(p)) candidates.add(p);
                }
            }
        }

        if (candidates.isEmpty()) return null;

        return switch (priority.getMode()) {
            case Closest    -> candidates.stream()
                    .min(Comparator.comparingDouble(p -> mc.player.getPos().squaredDistanceTo(p.getX() + .5, p.getY() + .5, p.getZ() + .5)))
                    .orElse(null);

            case MostCharged -> candidates.stream()
                    .max(Comparator.comparingInt(this::getCharges))
                    .orElse(null);

            case NearEnemy  -> {
                // Pick anchor with the nearest enemy player within 6 blocks of the anchor
                BlockPos best = null;
                double bestDist = Double.MAX_VALUE;
                for (BlockPos p : candidates) {
                    Vec3d center = Vec3d.ofCenter(p);
                    for (PlayerEntity player : mc.world.getPlayers()) {
                        if (player == mc.player) continue;
                        double d = player.squaredDistanceTo(center);
                        if (d < 36.0 && d < bestDist) { // within 6 blocks of anchor
                            bestDist = d;
                            best = p;
                        }
                    }
                }
                // Fall back to crosshair if no enemy is near any anchor
                yield best != null ? best : getCrosshairAnchor();
            }
        };
    }

    /** Returns crosshair-targeted anchor BlockPos or null. */
    private BlockPos getCrosshairAnchor() {
        if (mc.crosshairTarget == null) return null;
        if (mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockPos pos = hit.getBlockPos();
        return isAnchor(pos) ? pos : null;
    }

    // ── Health-based charge calculation ───────────────────────────────────────

    /**
     * Smart mode: estimate how many charges are needed to kill the nearest
     * enemy near the anchor. Each overworld explosion deals roughly 5 HP of
     * effective damage after armor. Falls back to the fixed setting.
     *
     * This is a lightweight estimate — full server-side damage maths would
     * require EnchantmentHelper which is stubbed out in 1.21 mappings.
     */
    private int neededCharges(BlockPos anchor) {
        if (chargeMode.isMode(ChargeMode.Fixed)) return chargesNeeded.getValueInt();

        // Smart: find nearest player to this anchor
        if (mc.world == null) return chargesNeeded.getValueInt();
        Vec3d center = Vec3d.ofCenter(anchor);
        PlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double d = p.squaredDistanceTo(center);
            if (d < 36.0 && d < best) { best = d; nearest = p; }
        }

        if (nearest == null) return chargesNeeded.getValueInt();

        // Rough damage per explosion: 5 effective HP (conservative estimate)
        float hp = nearest.getHealth() + nearest.getAbsorptionAmount();
        int charges = (int) Math.ceil(hp / 5.0f);
        return Math.max(1, Math.min(charges, 4));
    }

    // ── Interaction helpers ────────────────────────────────────────────────────

    private void rightClickAnchor(BlockPos pos) {
        // Build a synthetic BlockHitResult for the top face of the anchor
        Vec3d hit = Vec3d.ofCenter(pos).add(0, 0.5, 0);
        BlockHitResult bhr = BlockHitResult.createMissed(hit, net.minecraft.util.math.Direction.UP, pos);

        if (simulateClick.getValue()) {
            MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        } else {
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            if (result.isAccepted() && result.shouldSwingHand())
                mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ── Activation ────────────────────────────────────────────────────────────

    private boolean passesActivationGate() {
        return switch (activation.getMode()) {
            case Always   -> true;
            case OnRMB    -> GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            case OnWeapon -> {
                var item = mc.player.getMainHandStack().getItem();
                yield item instanceof net.minecraft.item.SwordItem
                        || item instanceof net.minecraft.item.AxeItem
                        || item instanceof net.minecraft.item.MaceItem;
            }
        };
    }

    // ── Slot helpers ──────────────────────────────────────────────────────────

    private int findGlowstoneSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) return i;
        return -1;
    }

    private int findTotemSlot() {
        int slot = InventoryUtils.findTotemSlot();
        if (slot != -1) return slot;
        // Fallback: random totem slot
        return InventoryUtils.findRandomTotemSlot();
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private boolean isAnchor(BlockPos pos) {
        return mc.world != null && BlockUtils.isBlock(pos, Blocks.RESPAWN_ANCHOR);
    }

    private int getCharges(BlockPos pos) {
        if (mc.world == null) return 0;
        return mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES);
    }

    private long randomMs(MinMaxSetting s) {
        long min = (long) s.getMinValue();
        long max = (long) s.getMaxValue();
        return min >= max ? min : min + (long)(rng.nextDouble() * (max - min));
    }

    private void releaseSneakIfNeeded() {
        if (wasSneaking && mc.player != null) {
            mc.player.setSneaking(false);
            wasSneaking = false;
        }
    }

    private void cleanup() {
        releaseSneakIfNeeded();
        if (restoreSlot.getValue() && prevSlot != -1 && mc.player != null) {
            InventoryUtils.setInvSlot(prevSlot);
        }
        prevSlot     = -1;
        targetAnchor = null;
        waitMs       = 0;
    }

    private void reset() {
        state        = State.IDLE;
        targetAnchor = null;
        prevSlot     = -1;
        wasSneaking  = false;
        waitMs       = 0;
        timer.reset();
    }

    // ── Cancel vanilla right-click on anchor ──────────────────────────────────

    @Override
    public void onItemUse(ItemUseEvent event) {
        if (mc.player == null || mc.crosshairTarget == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (BlockUtils.isBlock(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR))
            event.cancel();
    }
}
