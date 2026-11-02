package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.Vec3;

/** Non-TaCZ ranged weapons, using the real compound-bow projectile when its optional mod is installed. */
final class BotRanged {
    static boolean supports(ItemStack stack){return stack.is(Items.BOW)||stack.is(Items.CROSSBOW)||CompoundBow.isBow(stack);}
    static BotWeaponData data(ItemStack stack){
        if(CompoundBow.isBow(stack))return new BotWeaponData("bow",8,.04,1,32,40,1,0);
        if(stack.is(Items.CROSSBOW))return new BotWeaponData("bow",3.15,.05,.99,10,30,1,0);
        return BotWeaponData.BOW;
    }
    static int fire(BotEntity bot,float yaw,float pitch)throws ReflectiveOperationException {
        var stack=bot.getMainHandItem();var level=(ServerLevel)bot.level();
        if(CompoundBow.isBow(stack)){
            var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValues().stream()
                    .filter(t->{var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(t);return id!=null&&id.getNamespace().equals("tacticalmilitarybow");})
                    .filter(t->t.create(level) instanceof CompoundBow.ArrowAccess).findFirst().orElseThrow();
            var shot=(Projectile)type.create(level);
            var fake=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(bot.getUUID(),"BotBow"));
            fake.moveTo(bot.getX(),bot.getY(),bot.getZ(),yaw,pitch);fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack.copy());
            try{shot.getClass().getMethod("prepare",net.minecraft.server.level.ServerPlayer.class,ItemStack.class,float.class).invoke(shot,fake,stack,1f);}
            finally{fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
            shot.setOwner(bot);shot.setPos(bot.getEyePosition());shot.xOld=shot.getX();shot.yOld=shot.getY();shot.zOld=shot.getZ();
            shot.setDeltaMovement(Vec3.directionFromRotation(pitch,yaw).scale(data(stack).speed()));level.addFreshEntity(shot);
        }else{
            var arrow=new Arrow(level,bot);arrow.setBaseDamage(6);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
            if(stack.is(Items.CROSSBOW))arrow.setShotFromCrossbow(true);
            var direction=Vec3.directionFromRotation(pitch,yaw);arrow.shoot(direction.x,direction.y,direction.z,(float)data(stack).speed(),.5f);
            level.addFreshEntity(arrow);
        }
        bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND);bot.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT,1,1);BotManager.heard(bot);
        return stack.is(Items.CROSSBOW)?40:30;
    }
    private BotRanged(){}
}
