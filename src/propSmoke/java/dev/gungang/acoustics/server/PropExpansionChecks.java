package dev.gungang.acoustics.server;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

final class PropExpansionChecks {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static List<Mob> mobs(ServerPlayer p){var list=new ArrayList<Mob>();p.serverLevel().getAllEntities().forEach(e->{if(e instanceof Mob m&&e.getTags().contains(PropSummons.TAG)&&!e.isRemoved())list.add(m);});return list;}
    private static PropEntity as(PropHuntMatch match,ServerPlayer p,String item){var prop=match.prop(p.getUUID());prop.setup(p.getUUID(),item,0);prop.setPos(p.position());match.abilities.initialize(p,prop);return prop;}
    static void run(MinecraftServer server,PropHuntMatch match,ServerPlayer a,ServerPlayer b)throws Exception {
        var counter=MinecraftServer.class.getDeclaredField("tickCount");counter.setAccessible(true);int now=server.getTickCount();
        try{
            var immunity=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");immunity.setAccessible(true);immunity.setInt(a,0);immunity.setInt(b,0);
            match.unlock(a);a.setPos(.5,100,.5);b.setPos(5.5,100,.5);b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);b.setHealth(100);
            for(int i=100;i<=139;i++){String type=PropCatalog.type(String.valueOf(i));check(type!=null,"Catalog number "+i);String item=PropCatalog.variant(type,new Random(1));check(PropCatalog.number(item).equals(String.valueOf(i)),"Catalog roundtrip "+i);}
            var prop=as(match,a,"minecraft:spawner");match.abilities.use(a,prop);
            check(mobs(a).size()==1&&match.abilities.mobHealth(a.getUUID())==100,"Spawner health and registration");check(match.abilities.remaining(a.getUUID())==1680,"84 second cooldown");match.abilities.use(a,prop);check(mobs(a).size()==1,"No duplicate summon during cooldown");
            var spider=mobs(a).get(0);spider.setPos(b.position().add(.2,0,0));PropSummons.tick(match,spider,prop.position(),0);
            check(Math.abs(b.getHealth()-84)<.01&&b.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Spider 16 damage and poison: health="+b.getHealth());b.removeAllEffects();b.invulnerableTime=0;b.setHealth(100);
            prop=as(match,a,"minecraft:bee_nest");match.abilities.tick(a,prop);check(mobs(a).size()==3&&mobs(a).stream().allMatch(Entity::isInvulnerable),"Three invulnerable bees");
            var bee=mobs(a).get(0);bee.setPos(b.position().add(.1,.5,0));PropSummons.tick(match,bee,prop.position(),0);check(Math.abs(b.getHealth()-64)<.01,"Bee damage 36");b.invulnerableTime=0;b.setHealth(100);
            b.setPos(a.position().add(100,0,0));var idle=bee.position();PropSummons.tick(match,bee,prop.position(),0);check(bee.isNoGravity()&&!bee.isRemoved()&&bee.position().distanceToSqr(idle)>0,"Bees orbit without a nearby hunter");b.setPos(a.position().add(5,0,0));
            prop=as(match,a,"minecraft:infested_stone");check(prop.state().getBlock() instanceof net.minecraft.world.level.block.InfestedBlock,"Infested random appearance");match.abilities.found(a,prop);check(mobs(a).size()==3&&mobs(a).stream().allMatch(m->m.getMaxHealth()==50),"Three 50 HP silverfish");
            var silver=mobs(a).get(0);silver.setPos(b.position().add(.1,0,0));PropSummons.tick(match,silver,prop.position(),0);check(Math.abs(b.getHealth()-75)<.01,"Silverfish damage 25");
            prop=as(match,a,"minecraft:command_block");match.abilities.use(a,prop);check(a.getAbilities().flying&&a.isNoGravity()&&match.abilities.active(a.getUUID())==100&&match.abilities.remaining(a.getUUID())==600,"Command flight and cooldown");
            double flightY=a.getY();a.setPos(a.getX()+1,flightY+2,a.getZ());a.setDeltaMovement(.2,1,.3);match.abilities.tick(a,prop);check(Math.abs(a.getY()-flightY)<.001&&a.getDeltaMovement().y==0&&a.getDeltaMovement().x==.2,"Command hover preserves horizontal motion and locks altitude: y="+a.getY()+", expected="+flightY+", motion="+a.getDeltaMovement());
            counter.setInt(server,now+100);match.abilities.tick(a,prop);check(!a.getAbilities().flying&&!a.isNoGravity(),"Flight expires exactly after five seconds");
            prop=as(match,a,"gungang:mannequin");check(a.getMaxHealth()==255&&prop.skin().equals(b.getUUID()),"Mannequin health and seeker skin");match.abilities.use(a,prop);check(prop.mode()==1,"Pose cycle");
            prop=as(match,a,"minecraft:pink_petals");check(prop.state().getValue(BlockStateProperties.FLOWER_AMOUNT)==4,"Four pink petals");
            prop=as(match,a,"minecraft:sugar_cane");counter.setInt(server,now+1300);match.abilities.tick(a,prop);check(prop.height()==2&&PropGeometry.parts(prop).size()==2,"Cane 60 second growth");counter.setInt(server,now+2500);match.abilities.tick(a,prop);check(prop.height()==3&&prop.getBoundingBox().getYsize()==3,"Cane capped at three blocks");
            prop=as(match,a,"minecraft:beacon");double speed=a.getAttributeValue(Attributes.MOVEMENT_SPEED);match.abilities.use(a,prop);match.abilities.tick(a,prop);check(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*1.2)<1e-6,"Beacon team movement boost");match.abilities.use(a,prop);match.abilities.tick(a,prop);check(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-6,"Beacon boost removal");
            prop=as(match,a,"gungang:conveyor_belt");match.abilities.use(a,prop);check(prop.state().getValue(ConveyorBlock.REVERSED),"Conveyor E direction");match.abilities.use(a,prop);check(prop.sinkDepth()==.5,"Conveyor half-block burial");
            prop=as(match,a,"gungang:blue_jump_pad");match.abilities.use(a,prop);check(prop.sinkDepth()==.125,"Pad burial");
            prop=as(match,a,"minecraft:stone");prop.setGlowingTag(true);check(!prop.marker(),"External glow is not discovery");prop.setGlowingTag(false);
            var padPos=new net.minecraft.core.BlockPos(20,100,20);for(var pad:List.of(MotionBlocks.BLUE.get(),MotionBlocks.LIME.get())){
                a.serverLevel().setBlock(padPos,pad.defaultBlockState(),3);a.setPos(20.5,100.125,20.5);a.setOnGround(true);a.setDeltaMovement(Vec3.ZERO);
                match.action(a,"charge");match.action(a,"jump");check(Math.abs(a.getDeltaMovement().y-MotionRules.jumpVelocity(((JumpPadBlock)pad).height))<1e-6&&a.getPersistentData().getBoolean("GunGangPadJump"),"Disguised charged jump uses installed pad and fall protection");counter.setInt(server,server.getTickCount()+20);
            }a.serverLevel().setBlock(padPos,Blocks.AIR.defaultBlockState(),3);a.getPersistentData().remove("GunGangPadJump");a.setPos(.5,100,.5);
            prop=as(match,a,"minecraft:minecart");a.setYRot(137);match.abilities.tick(a,prop);check(prop.getYRot()==137,"Minecart follows continuous player yaw");
            for(String item:List.of("gungang:mannequin","gungang:dried_ghast")){
                counter.setInt(server,server.getTickCount()+2);
                prop=as(match,a,item);a.setYRot(73);match.abilities.tick(a,prop);check(prop.getYRot()==73,"Disguise follows view: "+item);
                match.action(a,"lock");a.setYRot(151);match.abilities.tick(a,prop);check(prop.getYRot()==73,"Locked disguise preserves yaw: "+item);match.unlock(a);
            }
            var seekerMode=b.gameMode.getGameModeForPlayer();b.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            try{
                check(match.seekers().isEmpty(),"No active seeker for summon checks");
                for(String item:List.of("minecraft:spawner","minecraft:bee_nest","minecraft:infested_stone")){
                    prop=as(match,a,item);match.abilities.use(a,prop);match.abilities.tick(a,prop);
                    int count=item.equals("minecraft:spawner")?1:3;check(mobs(a).size()==count,"Summons survive without seekers: "+item);
                    match.abilities.use(a,prop);match.abilities.tick(a,prop);check(mobs(a).size()==count,"No duplicate idle summons: "+item);
                }
            }finally{b.setGameMode(seekerMode);}
            prop=as(match,a,"minecraft:fire");b.setPos(a.position());b.invulnerableTime=0;b.setHealth(100);match.abilities.tick(a,prop);check(b.getHealth()==50&&b.getRemainingFireTicks()>=199,"Fire 50 damage plus ten seconds");b.clearFire();
            prop=as(match,a,"minecraft:oak_sign");match.abilities.use(a,prop);check(!prop.text().isEmpty(),"Sign randomized text");
            prop=as(match,a,"minecraft:end_crystal");b.setPos(a.position().add(5,0,0));b.invulnerableTime=0;b.setHealth(100);match.abilities.use(a,prop);for(int i=0;i<20;i++)match.abilities.tick(a,prop);check(b.getHealth()==50&&match.abilities.remaining(a.getUUID())==600,"Crystal homing impact and cooldown");
            prop=as(match,a,"minecraft:spawner");match.abilities.use(a,prop);check(!mobs(a).isEmpty(),"Reset test summon");GimmickEpoch.reset(server);check(mobs(a).isEmpty(),"Reset removes every summon");
            as(match,a,"minecraft:stone");a.setHealth(50);b.setPos(40.5,100,.5);
            com.mojang.logging.LogUtils.getLogger().info("GUNGANG_PROP_139_PASS catalog=40 summons=pass cooldowns=pass flight=pass growth=pass buffs=pass damage=pass reset=pass");
        }finally{counter.setInt(server,now);}
    }
}
