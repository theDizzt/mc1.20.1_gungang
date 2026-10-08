package dev.gungang.acoustics.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.fml.ModList;

/** TaCZ consumes its own input rather than Minecraft's attack/use methods. No hard dependency. */
public final class TaczMacroCompatibility {
    public enum Result { NOT_GUN, WAIT, DONE }
    private static boolean initialized;
    private static Class<?> gunType, operatorType;
    private static Method shoot, aim, charge;

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            gunType = Class.forName("com.tacz.guns.api.item.IGun");
            operatorType = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            shoot = operatorType.getMethod("shoot");
            aim = operatorType.getMethod("aim", boolean.class);
            charge = operatorType.getMethod("chargeShoot", boolean.class);
        } catch (ReflectiveOperationException failure) {
            gunType = null;
            LogUtils.getLogger().warn("TaCZ macro API unavailable; falling back to ordinary item actions", failure);
        }
    }

    public static boolean isGun(LocalPlayer player) {
        initialize();
        return gunType != null && gunType.isInstance(player.getMainHandItem().getItem()) && operatorType.isInstance(player);
    }

    public static Result attack(LocalPlayer player) {
        if (!isGun(player)) return Result.NOT_GUN;
        try {
            if (!(boolean) charge.invoke(player, true)) return Result.WAIT;
            String result = ((Enum<?>) shoot.invoke(player)).name();
            return switch (result) {
                case "COOL_DOWN", "NOT_DRAW", "IS_DRAWING" -> Result.WAIT;
                default -> Result.DONE; // Success, no ammo, cancelled, bolt/reload required: never force a shot.
            };
        } catch (ReflectiveOperationException failure) {
            LogUtils.getLogger().warn("TaCZ offhand attack could not be performed", failure);
            return Result.DONE;
        }
    }

    public static void aim(LocalPlayer player, boolean down) {
        if (!isGun(player)) return;
        try { aim.invoke(player, down); }
        catch (ReflectiveOperationException failure) { LogUtils.getLogger().warn("TaCZ offhand aim failed", failure); }
    }

    public static void release(LocalPlayer player) {
        initialize();
        if (gunType == null || !operatorType.isInstance(player)) return;
        try { charge.invoke(player, false); aim.invoke(player, false); }
        catch (ReflectiveOperationException failure) { LogUtils.getLogger().warn("TaCZ offhand input release failed", failure); }
    }
    private TaczMacroCompatibility() { }
}
