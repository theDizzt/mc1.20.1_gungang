package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tells a player the health damage each of their hits dealt; the client counts the combo (3 s window). */
@Mod.EventBusSubscriber(modid = GungangAcoustics.ID)
public final class ComboHits {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getAmount() > 0)) return;
        var victim = event.getEntity();
        if (!(DamageCredit.attacker(victim, event.getSource()) instanceof ServerPlayer attacker) || attacker == victim) return;
        // Health actually removed: overkill past the victim's remaining health does not count.
        float dealt = Math.min(victim.getHealth(), event.getAmount());
        if (dealt > 0) GungangNetwork.send(attacker, new GungangNetwork.ComboHit(dealt));
    }
    private ComboHits() { }
}
