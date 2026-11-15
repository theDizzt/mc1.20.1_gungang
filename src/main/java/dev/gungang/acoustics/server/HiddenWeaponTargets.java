package dev.gungang.acoustics.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Hidden weapons hit living bodies, including ordinary mobs and third-party NPCs. */
final class HiddenWeaponTargets {
    static boolean valid(LivingEntity target){
        if(!target.isAlive()||target.isRemoved()||target.isSpectator()||SafeZones.contains(target)
                ||LobbyManager.respawning(target.getUUID())||FreeForAllMatch.blocked(target))return false;
        if(target instanceof Player player&&player.isCreative())return false;
        // Disguise bodies and map NPCs retain prop hunt's own targeting rules.
        if(target instanceof PropEntity||target instanceof BotEntity bot&&bot.disguised())return false;
        return MatchManager.propHunt()==null||!PropHuntMatch.npc(target);
    }
    private HiddenWeaponTargets(){}
}
