package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** One transaction at a time. Stack references are exchanged, never restored from saved copies. */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT)
public final class OffhandMacro {
    public interface Clicks { boolean gungang$attack(); void gungang$use(); }
    public static final KeyMapping ATTACK = mapping("attack", GLFW.GLFW_KEY_G);
    public static final KeyMapping USE = mapping("use", GLFW.GLFW_KEY_H);
    private static LocalPlayer owner;
    private static int originalSlot;
    private static MacroSequence sequence;
    private static boolean use;
    private static boolean click;
    private static boolean holdingUse;
    private static boolean gunAction;
    private static boolean shieldHold;
    private static boolean previousAttackDown, previousUseDown;

    private static KeyMapping mapping(String name, int key) {
        return new KeyMapping("gungang.key.offhand." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, key, "gungang.key.category");
    }

    public static boolean executingClick() { return click; }
    public static boolean ownsGunInput() { return owner != null && gunAction; }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            if (owner != null) advance(mc);
            return;
        }
        boolean attackClick = drain(ATTACK), useClick = drain(USE);
        boolean attackDown = ATTACK.isDown(), useDown = USE.isDown();
        // Suppress operating-system key repeat and discard additional requests while busy.
        boolean attackEdge = attackClick && !previousAttackDown;
        boolean useEdge = useClick && !previousUseDown;
        previousAttackDown = attackDown;
        previousUseDown = useDown;
        if (owner == null && (attackEdge || useEdge) && canStart(mc)) {
            if(useEdge&&!attackEdge&&dev.gungang.acoustics.server.CombatGadgets.is(mc.player.getOffhandItem(),"shield"))return;
            begin(mc, !attackEdge);
        }
    }

    private static boolean drain(KeyMapping mapping) {
        boolean clicked = false;
        while (mapping.consumeClick()) clicked = true;
        return clicked;
    }

    private static boolean canStart(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null
                && mc.isWindowActive() && mc.screen == null && !mc.isPaused()
                && mc.player.isAlive() && !mc.player.isSpectator() && !mc.player.isHandsBusy()
                && !mc.player.isUsingItem() && !mc.gameMode.isDestroying()
                && mc.player.containerMenu == mc.player.inventoryMenu
                && !mc.player.getOffhandItem().isEmpty()
                && ((!mc.options.keyAttack.isDown() && !mc.options.keyUse.isDown())
                    || (ATTACK.isDown()&&dev.gungang.acoustics.server.CombatGadgets.is(mc.player.getOffhandItem(),"shield")));
    }

    private static void begin(Minecraft mc, boolean rightClick) {
        owner = mc.player;
        originalSlot = owner.getInventory().selected;
        sequence = new MacroSequence();
        gunAction = false;
        use = rightClick;
        shieldHold=!rightClick&&dev.gungang.acoustics.server.CombatGadgets.is(owner.getOffhandItem(),"shield");
        // Keep the server's selected slot current before the swap packet.
        owner.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));
        swap(owner);
    }

    private static void advance(Minecraft mc) {
        if (mc.player != owner || mc.level == null || mc.getConnection() == null || !owner.isAlive()) {
            reset(mc);
            return;
        }
        if (mc.screen != null || mc.isPaused() || !mc.isWindowActive() || owner.isSpectator()
                || owner.getInventory().selected != originalSlot) {
            finish(mc);
            return;
        }
        try {
            if(shieldHold){
                if(!ATTACK.isDown()||!dev.gungang.acoustics.server.CombatGadgets.is(owner.getMainHandItem(),"shield"))finish(mc);
                return;
            }
            MacroSequence.Step step = sequence.next(use && USE.isDown() && (gunAction || (holdingUse && owner.isUsingItem())));
            if (step == MacroSequence.Step.WAIT) return;
            if (step == MacroSequence.Step.RESTORE) { finish(mc); return; }
            if (step == MacroSequence.Step.ACT) {
                gunAction = TaczMacroCompatibility.isGun(owner);
                if (gunAction) {
                    if (use) TaczMacroCompatibility.aim(owner, true);
                    else if (TaczMacroCompatibility.attack(owner) == TaczMacroCompatibility.Result.WAIT) return;
                    sequence.actionDone();
                    return;
                }
                click = true;
                try {
                    if (use) ((Clicks) mc).gungang$use();
                    else ((Clicks) mc).gungang$attack();
                } finally { click = false; }
                sequence.actionDone();
                if (use && owner.isUsingItem()) {
                    holdingUse = true;
                    mc.options.keyUse.setDown(true);
                }
                return;
            }
            if (gunAction && use && USE.isDown()) {
                TaczMacroCompatibility.aim(owner, true);
                return;
            }
            if (holdingUse && USE.isDown() && owner.isUsingItem()) {
                mc.options.keyUse.setDown(true);
                return;
            }
            finish(mc);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Offhand macro failed; restoring hands", failure);
            finish(mc);
        }
    }

    private static void finish(Minecraft mc) {
        try {
            if (mc.player == owner && mc.getConnection() != null && owner.isAlive()) {
                if (gunAction) TaczMacroCompatibility.release(owner);
                if (owner.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(owner);
                if (!use && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
                // Restore the slot before swapping, including when the user scrolls mid-macro.
                owner.getInventory().selected = originalSlot;
                owner.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));
                swap(owner);
            }
        } finally { reset(mc); }
    }

    private static void swap(LocalPlayer player) {
        player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        ItemStack main = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, offhand);
        player.setItemInHand(InteractionHand.OFF_HAND, main);
    }

    private static void reset(Minecraft mc) {
        if (holdingUse) mc.options.keyUse.setDown(physicalDown(mc, mc.options.keyUse));
        holdingUse = false;
        gunAction = false;
        shieldHold = false;
        click = false;
        owner = null;
        sequence = null;
    }

    private static boolean physicalDown(Minecraft mc, KeyMapping mapping) {
        var key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        if (!mc.isWindowActive() || key.getValue() < 0) return false;
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(window, key.getValue());
        return false;
    }
    private OffhandMacro() { }
}
