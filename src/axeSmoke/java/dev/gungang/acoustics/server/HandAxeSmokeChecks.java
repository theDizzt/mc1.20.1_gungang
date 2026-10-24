package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in integration tests in an isolated disposable server world. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.DEDICATED_SERVER)
public final class HandAxeSmokeChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();long time=level.getGameTime();
        try {
            var previewStack=new net.minecraft.world.item.ItemStack(GadgetItems.HAND_AXE.get());
            HandAxeItem.configurePreview(previewStack);
            var preview=previewStack.getTag();var config=ExtraGadgetConfig.handAxe;
            check(preview.getDouble("AxeMinRange")==config.minThrowDistance()
                    &&preview.getDouble("AxeMaxRange")==config.maxThrowDistance()
                    &&preview.getDouble("AxeChargeSeconds")==config.chargeSeconds()
                    &&preview.getDouble("AxeArcDegrees")==config.arcDegrees(),"Trajectory parameters differ from server throw settings");
            var block=new BlockPos(0,240,0);level.setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
            for(var face:Direction.values()) {
                var normal=Vec3.atLowerCornerOf(face.getNormal());
                var contact=Vec3.atCenterOf(block).add(normal.scale(.5));
                var axe=GadgetItems.HAND_AXE_PROJECTILE.get().create(level);
                axe.setPos(contact.add(normal.scale(.7)));axe.setDeltaMovement(normal.scale(-1));axe.expireAfter(20);
                axe.tick();
                check(axe.embedded(),"Actual ray collision did not embed: "+face);
                check(axe.position().distanceTo(contact)<.001,"Impact moved past face: "+face);
                float yaw=axe.getYRot(),pitch=axe.getXRot();
                server.getWorldData().overworldData().setGameTime(time+19);axe.tick();
                check(!axe.isRemoved()&&axe.position().distanceTo(contact)<.001&&axe.getDeltaMovement().lengthSqr()==0,"Embedded axe moved/expired early");
                check(axe.getYRot()==yaw&&axe.getXRot()==pitch,"Embedded rotation changed");
                var saved=new CompoundTag();axe.saveWithoutId(saved);
                var restored=GadgetItems.HAND_AXE_PROJECTILE.get().create(level);restored.load(saved);restored.tick();
                check(restored.embedded()&&!restored.isRemoved()&&restored.position().distanceTo(contact)<.001,"Save/load lost embedded state");
                server.getWorldData().overworldData().setGameTime(time+20);axe.tick();restored.tick();
                check(axe.isRemoved()&&restored.isRemoved(),"Cooldown expiry not preserved across reload");
                server.getWorldData().overworldData().setGameTime(time);
            }
            var victim=EntityType.COW.create(level);victim.setPos(5,240,5);
            victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
            var axe=GadgetItems.HAND_AXE_PROJECTILE.get().create(level);
            axe.onHitEntity(new EntityHitResult(victim));
            check(axe.isRemoved(),"Entity hit did not remove axe");
            check(Math.abs(victim.getHealth()-(1000-ExtraGadgetConfig.handAxe.throwDamage()))<.01,"Throw damage changed");
            victim.discard();
            System.out.println("GUNGANG_HAND_AXE_SERVER_PASS faces=6 persistence=pass expiry=pass damage=pass");
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_HAND_AXE_SERVER_FAIL");}
        finally {server.getWorldData().overworldData().setGameTime(time);server.halt(false);}
    }
}
