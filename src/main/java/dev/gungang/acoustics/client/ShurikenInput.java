package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.ShurikenItem;
import dev.gungang.acoustics.server.TwinBlades;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 33 shuriken controls: the attack key throws, TaCZ's fire select key cycles Semi / Burst / Ricochet and TaCZ's reload
 * key reloads (TaCZ itself ignores both keys for a non-gun item). While a shuriken is carried, a jump press in mid-air
 * climbs one step up the wall the player faces, or else double-jumps once per airtime. Both are applied here, to the
 * client-driven movement; the server paces them and adds the effects.
 * <p>The fire select key also restarts the 31 twin blades' combo on the server, as it does on screen.
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class ShurikenInput {
    /** Ticks off the ground before a jump press counts as an air jump: the press that leaves the ground is the ground jump. */
    private static final int AIR_TICKS=2;
    /** How far ahead (blocks) a wall must be for a climb step. */
    private static final double WALL_REACH=.3;
    private static KeyMapping fireSelect,reload;
    private static boolean keysMissing,selectDown,reloadDown,jumpDown,airJumped;
    private static int airTicks,climbCooldown;
    private static long lastThrow=Long.MIN_VALUE/2;

    private static KeyMapping key(String type,String field){
        try{return (KeyMapping)Class.forName("com.tacz.guns.client.input."+type).getField(field).get(null);}
        catch(ReflectiveOperationException|LinkageError|ClassCastException missing){return null;}
    }
    private static void keys(){
        if(keysMissing||fireSelect!=null&&reload!=null)return;
        fireSelect=key("FireSelectKey","FIRE_SELECT_KEY");reload=key("ReloadKey","RELOAD_KEY");
        // Without TaCZ the shuriken still throws and reloads on its own when empty; only mode switching is unavailable.
        if(fireSelect==null||reload==null)keysMissing=true;
    }
    private static void send(String action){GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action(action,"",""));}

    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void attack(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(!event.isAttack()||mc.player==null||mc.level==null||mc.screen!=null||!ShurikenItem.is(mc.player.getMainHandItem()))return;
        event.setCanceled(true);event.setSwingHand(false);
        if(mc.player.isSpectator())return;
        // Click only as fast as the server throws, so every throw seen is a real throw.
        long now=mc.level.getGameTime();var stack=mc.player.getMainHandItem();
        if(now-lastThrow>=0&&now-lastThrow<ShurikenItem.throwTicks(stack))return;
        if(ShurikenItem.reloadEnd(stack)>0)return;
        lastThrow=now;
        send("shurikenThrow");
        if(ShurikenItem.ammo(stack)>=ShurikenItem.perThrow(stack)){
            ShurikenAnimation.thrown();
            // Third-person observers see the arm swing; first person draws its own throw.
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.getConnection()==null){selectDown=reloadDown=jumpDown=airJumped=false;airTicks=climbCooldown=0;return;}
        keys();
        boolean free=mc.screen==null&&!player.isSpectator();
        boolean holding=ShurikenItem.is(player.getMainHandItem());
        boolean select=fireSelect!=null&&fireSelect.isDown(),reloading=reload!=null&&reload.isDown();
        if(free&&select&&!selectDown){
            if(holding)send("shurikenMode");
            else if(TwinBlades.is(player.getMainHandItem()))send("bladesReset");
        }
        if(free&&holding&&reloading&&!reloadDown)send("shurikenReload");
        selectDown=select;reloadDown=reloading;
        jump(mc,player,free);
    }
    private static void jump(Minecraft mc,LocalPlayer player,boolean free){
        boolean jump=mc.options.keyJump.isDown(),pressed=jump&&!jumpDown;jumpDown=jump;
        if(climbCooldown>0)climbCooldown--;
        if(player.onGround()||player.isInWater()||player.isInLava()||player.onClimbable()||player.isPassenger()){airJumped=false;airTicks=0;return;}
        airTicks++;
        if(!free||!pressed||airTicks<AIR_TICKS||player.getAbilities().flying||player.isFallFlying()||!ShurikenItem.carried(player))return;
        var motion=player.getDeltaMovement();
        if(facingWall(player)){
            if(climbCooldown>0)return;
            double velocity=ShurikenItem.climbVelocity(carriedStack(player));
            if(velocity<=0)return;
            // One stride up the wall per tap: rise, keep pressing into it.
            climbCooldown=ShurikenItem.CLIMB_TICKS;
            player.setDeltaMovement(motion.x*.6,Math.max(motion.y,velocity),motion.z*.6);
            player.fallDistance=0;
            ShurikenAnimation.climbed();
            send("shurikenClimb");
            return;
        }
        if(airJumped)return;
        airJumped=true;
        player.setDeltaMovement(motion.x,Math.max(motion.y,ShurikenItem.jumpVelocity(carriedStack(player))),motion.z);
        player.fallDistance=0;
        send("shurikenJump");
    }
    /** A solid wall within reach in the horizontal look direction, at the feet or the chest. Barriers don't count. */
    private static boolean facingWall(LocalPlayer player){
        var look=player.getLookAngle();double length=Math.sqrt(look.x*look.x+look.z*look.z);
        if(length<1e-4)return false;
        double dx=look.x/length*WALL_REACH,dz=look.z/length*WALL_REACH;
        var box=player.getBoundingBox().deflate(.02,.1,.02);
        var moved=box.move(dx,0,dz);var level=player.level();
        if(!level.getEntityCollisions(player,moved).isEmpty())return true;
        var test=net.minecraft.world.phys.shapes.Shapes.create(moved);
        var ctx=net.minecraft.world.phys.shapes.CollisionContext.of(player);
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.util.Mth.floor(moved.minX),net.minecraft.util.Mth.floor(moved.minY),net.minecraft.util.Mth.floor(moved.minZ),
                net.minecraft.util.Mth.floor(moved.maxX),net.minecraft.util.Mth.floor(moved.maxY),net.minecraft.util.Mth.floor(moved.maxZ))){
            var state=level.getBlockState(pos);
            if(state.isAir()||state.is(net.minecraft.world.level.block.Blocks.BARRIER))continue;
            var shape=state.getCollisionShape(level,pos,ctx);
            if(!shape.isEmpty()&&net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape.move(pos.getX(),pos.getY(),pos.getZ()),test,net.minecraft.world.phys.shapes.BooleanOp.AND))return true;
        }
        return false;
    }
    private static net.minecraft.world.item.ItemStack carriedStack(LocalPlayer player){
        for(var stack:player.getInventory().items)if(ShurikenItem.is(stack))return stack;
        return player.getOffhandItem();
    }
    private ShurikenInput(){}
}
