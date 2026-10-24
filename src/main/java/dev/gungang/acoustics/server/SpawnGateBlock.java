package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/**
 * Only walking teammates get an empty collision shape. Default/ray/projectile contexts stay solid.
 * Bomb mode keeps ALPHA/BRAVO but swaps attack and defence between rounds, so there the ALPHA gate
 * opens for the attacking team and the BRAVO gate for the defending team, following the side spawns.
 * Free-for-all has no teams, so every gate stays shut.
 */
public final class SpawnGateBlock extends Block {
    public static final String FREE_FOR_ALL="FREE_FOR_ALL";
    public final String team;
    /** The match rule as the client sees it ({@link #rule()} values); set by ClientSetup. */
    public static java.util.function.Supplier<String> clientRule=()->null;
    /** The preparation rule as the client sees it ({@link MatchManager#preparationGate()} values); set by ClientSetup. */
    public static java.util.function.Supplier<String> clientPreparation=()->null;
    public SpawnGateBlock(String team){
        super(Properties.of().strength(-1,3600000).noOcclusion().dynamicShape().lightLevel(s->8)
                .isViewBlocking((s,l,p)->false).isSuffocating((s,l,p)->false).sound(SoundType.GLASS));this.team=team;
    }
    public boolean allows(Entity entity){
        if(entity instanceof BotEntity bot)return bot.isAlive()&&bot.getTeam()!=null&&bot.getTeam().getName().equals(team.equals("ALPHA")?"Red":"Blue")
                &&open(MatchManager.preparationGate(),team);
        if(!(entity instanceof Player player)||player.isSpectator()||!player.isAlive())return false;
        if(player.level().isClientSide?"PROP_HUNT".equals(clientRule.get()):MatchManager.realPropHunt()!=null)return true;
        if(!player.level().isClientSide) {
            String owner=owner(rule());
            return owner!=null&&owner.equals(LobbyManager.team(player.getUUID()))&&open(MatchManager.preparationGate(),owner);
        }
        String owner=owner(clientRule.get());var scoreboardTeam=player.getTeam();
        return owner!=null&&scoreboardTeam!=null&&scoreboardTeam.getName().equals(owner.equals("ALPHA")?"Red":"Blue")
                &&open(clientPreparation.get(),owner);
    }
    /** During preparation only the free-roaming defenders get through their own gate; everyone else stays in spawn. */
    private static boolean open(String preparation,String owner){return preparation==null||preparation.equals(owner);}
    /** Server-side rule: {@link #FREE_FOR_ALL}, the bomb attacker ("ALPHA"/"BRAVO"), or null for ordinary team modes. */
    static String rule(){
        if(MatchManager.freeForAll()!=null)return FREE_FOR_ALL;
        var bomb=MatchManager.bomb();return bomb==null?null:bomb.game.attacker();
    }
    /**
     * The team this gate lets through under a rule: nobody in free-for-all, attackers (ALPHA gate) or
     * defenders (BRAVO gate) in bomb mode, otherwise the gate's own team.
     */
    public String owner(String rule){
        if(FREE_FOR_ALL.equals(rule))return null;
        if(!"ALPHA".equals(rule)&&!"BRAVO".equals(rule))return team;
        return team.equals("ALPHA")?rule:rule.equals("ALPHA")?"BRAVO":"ALPHA";
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return context instanceof EntityCollisionContext entity&&allows(entity.getEntity())?Shapes.empty():Shapes.block();
    }
    @Override public VoxelShape getVisualShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.block();}
    @Override public boolean skipRendering(BlockState state,BlockState other,Direction direction){return other.is(this)||super.skipRendering(state,other,direction);}
    @Override public float getShadeBrightness(BlockState state,BlockGetter level,BlockPos pos){return 1;}
    @Override public boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
    @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state){return net.minecraft.world.level.material.PushReaction.BLOCK;}
    @Override public void onProjectileHit(Level level,BlockState state,net.minecraft.world.phys.BlockHitResult hit,net.minecraft.world.entity.projectile.Projectile projectile){if(!level.isClientSide)projectile.discard();}
    @Override public void onBlockExploded(BlockState state,Level level,BlockPos pos,Explosion explosion){}
}
