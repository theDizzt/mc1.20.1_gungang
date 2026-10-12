package dev.gungang.acoustics.server;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;

public final class LoadoutAttributes {
    private static final UUID HEALTH=UUID.fromString("117c11c7-8d1b-4a30-a5a6-b884df81cb17"), SPEED=UUID.fromString("87779744-a60f-43e6-ab7b-77846ed35d98");
    public static void apply(ServerPlayer player,Loadouts.Weapon weapon) {
        boolean needsRecovery=SurvivalGadgets.needsRecovery(player);
        float ratio=player.getHealth()/player.getMaxHealth();
        var health=player.getAttribute(Attributes.MAX_HEALTH); var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        health.removeModifier(HEALTH);
        health.addTransientModifier(new AttributeModifier(HEALTH,"GunGang primary health",weapon.maxHealth()-health.getBaseValue(),AttributeModifier.Operation.ADDITION));
        speed.removeModifier(SPEED);
        speed.addTransientModifier(new AttributeModifier(SPEED,"GunGang primary speed",weapon.movementSpeed()/100-1,AttributeModifier.Operation.MULTIPLY_TOTAL));
        player.setHealth(needsRecovery?SurvivalRules.LIFE_HEALTH:Math.max(1,Math.min(player.getMaxHealth(),ratio*player.getMaxHealth())));
    }
    public static void clear(ServerPlayer player) {
        player.getAttribute(Attributes.MAX_HEALTH).removeModifier(HEALTH);
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED);
        player.setHealth(Math.min(player.getHealth(),player.getMaxHealth()));
    }
    private LoadoutAttributes() { }
}
