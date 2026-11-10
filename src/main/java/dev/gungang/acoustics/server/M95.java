package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned charge; each shot buys the highest affordable penetration tier. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class M95 {
    public interface NativeBullet { void gungang$nativeBulletTick(); }
    private static final class NativeTrace {
        final Projectile bullet;final Vec3 from,to;Vec3 hit;
        NativeTrace(Projectile bullet,Vec3 from,Vec3 to){this.bullet=bullet;this.from=from;this.to=to;}
    }
    private static final ThreadLocal<NativeTrace> TRACE=new ThreadLocal<>();
    public static boolean nativeTick(Projectile bullet){var trace=TRACE.get();return trace!=null&&trace.bullet==bullet;}
    public static boolean skipTracedBlocks(net.minecraft.world.entity.Entity entity,Vec3 from,Vec3 to){
        var trace=TRACE.get();return trace!=null&&trace.bullet==entity&&trace.from.distanceToSqr(from)<1e-12&&trace.to.distanceToSqr(to)<1e-12;
    }
    public static void nativeHit(Projectile bullet,Vec3 at){var trace=TRACE.get();if(trace!=null&&trace.bullet==bullet)trace.hit=at;}
    public static final String GUN="tacz:m95",DAMAGE="GunGangM95Damage",MAGAZINE="GunGangM95Magazine";
    private static final Map<LivingEntity,Double> CHARGE=new WeakHashMap<>();
    /** Set the final TaCZ pre-hit multiplier, including hits outside our custom hitscan path. */
    @SuppressWarnings("unchecked") public static void registerTacz(){
        try{
            var type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            var gun=type.getMethod("getGunId");
            var multiplier=type.getMethod("setHeadshotMultiplier",float.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,type,event->{
                try{
                    if(GUN.equals(String.valueOf(gun.invoke(event))))multiplier.invoke(event,(float)ExtraGadgetConfig.m95.headshotMultiplier());
                }catch(ReflectiveOperationException failure){com.mojang.logging.LogUtils.getLogger().error("M95 pre-hit multiplier failed",failure);}
            });
        }catch(ClassNotFoundException absent){/* TaCZ is optional. */}
        catch(ReflectiveOperationException incompatible){throw new IllegalStateException("M95 requires TaCZ's pre-hit headshot multiplier API",incompatible);}
    }
    public static final String MODE="GunGangM95AmmoMode";
    /** Freeze ammo selection once so changing weapons cannot alter an in-flight normal bullet. */
    public static boolean intercept(Projectile bullet){
        if(!bullet(bullet))return false;
        var tag=bullet.getPersistentData();
        if(!tag.contains("GunGangM95ShotMode")){
            var owner=bullet.getOwner();
            boolean piercing=owner instanceof LivingEntity living&&mode(living.getMainHandItem())==1;
            tag.putBoolean("GunGangM95ShotMode",piercing);
            if(!bullet.level().isClientSide&&owner instanceof LivingEntity living)M95Echo.fired(living);
        }
        return tag.getBoolean("GunGangM95ShotMode");
    }
    public static int mode(ItemStack stack){return stack.hasTag()?Math.max(0,Math.min(1,stack.getTag().getInt(MODE))):0;}
    public static String modeLabel(ItemStack stack){return mode(stack)==1?"관통탄":"일반탄";}
    public static void cycle(net.minecraft.server.level.ServerPlayer player){
        var stack=player.getMainHandItem();if(!is(stack)||!player.isAlive()||player.isSpectator()||player.containerMenu!=player.inventoryMenu)return;
        var tag=stack.getOrCreateTag();tag.putInt(MODE,(mode(stack)+1)%2);
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("M95 관통력 · "+modeLabel(stack)),true);
    }
    static void ensureLoadout(java.nio.file.Path directory)throws java.io.IOException {
        var marker=directory.resolve(".m95-penetration-v1");if(java.nio.file.Files.exists(marker)){refreshDescription(directory);return;}
        java.nio.file.Path path=directory.resolve("34_m95.json");
        com.google.gson.JsonObject json=new com.google.gson.JsonObject();
        try(var files=java.nio.file.Files.list(directory)){
            for(var candidate:files.filter(p->p.toString().endsWith(".json")).toList()){
                var existing=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(candidate)).getAsJsonObject();
                if(existing.has("id")&&existing.get("id").getAsString().equals("34")){path=candidate;json=existing;break;}
            }
        }
        json.remove("nbt");json.addProperty("id","34");json.addProperty("name","M95 대물 저격총");
        json.addProperty("itemNbt","{Count:1b,id:\"tacz:modern_kinetic_gun\",tag:{AttachmentMUZZLE:{Count:0b,id:\"minecraft:air\"},AttachmentSCOPE:{Count:1b,id:\"tacz:attachment\",tag:{AttachmentId:\"kpp:nemesisscope\"}},GunCurrentAmmoCount:3,GunFireMode:\"SEMI\",GunId:\"tacz:m95\",HasBulletInBarrel:1b}}");
        json.addProperty("category","저격총");json.addProperty("releaseDate","2026-12-19");
        json.addProperty("maxHealth",100);json.addProperty("movementSpeed",90);
        json.addProperty("description","히트스캔 · 몸통 피해 51, 헤드샷 ×1.33. 관통력은 초당 15 충전되며 최대 200입니다. 발사 시 가장 높은 사용 가능 단계의 비용만 소모합니다: 10은 1블록, 50은 3블록, 100은 10블록, 150은 모든 블록 관통. 블록은 파괴하지 않습니다.");
        json.addProperty("usage","사격 키로 발사합니다. T키로 자동 → 50 → 100 → 150 모드를 전환합니다. 고정 모드의 충전량이 부족하면 관통 없이 발사합니다.");
        json.add("specs",ServerFiles.GSON.toJsonTree(new Loadouts.Specs("51 / 헤드샷 ×1.33","일반탄 / 관통탄 히트스캔","","관통탄 256블록","")));
        java.nio.file.Files.writeString(path,ServerFiles.GSON.toJson(json));
        java.nio.file.Files.writeString(marker,"M95 penetration loadout migrated.\n");
        refreshDescription(directory);
    }
    private static void refreshDescription(java.nio.file.Path directory)throws java.io.IOException {
        var marker=directory.resolve(".m95-description-ammo-v3");if(java.nio.file.Files.exists(marker))return;
        try(var files=java.nio.file.Files.list(directory)){
            for(var path:files.filter(p->p.toString().endsWith(".json")).toList()){
                var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                if(!json.has("id")||!json.get("id").getAsString().equals("34"))continue;
                json.addProperty("description","M95 대물 저격총 · 일반탄/히트스캔 관통탄, 탄창 3발. 피해 및 거리별 감쇠는 TaCZ 무기 수치를 사용하며 헤드샷은 1.33배입니다. 관통탄은 #85dbd9 레이저 잔상 0.5초, 일반탄은 TaCZ 기본 잔상을 사용합니다. 관통력은 초당 15 충전, 최대 200입니다. 관통력 10은 1블록, 50은 3블록, 100은 10블록, 150은 모든 블록을 관통하며 사용한 단계의 비용만 소모합니다. 줌을 20초간 유지하면 벽 너머 적의 현재 위치를 볼 수 있습니다. 발사하거나 줌을 해제하면 투시가 종료되고 대기 시간이 초기화됩니다.");
                json.addProperty("usage","T키: 일반탄 ↔ 관통탄. 일반탄은 TaCZ 기본 탄환 효과를 사용하고 관통력을 소모하지 않습니다. 관통탄은 충전량에 따라 사용 가능한 가장 높은 단계의 비용을 소모하며, 경로의 건강모드 파괴 가능 블록을 파괴합니다. 투시에는 관통력 200이 필요하지 않습니다. /gungang modify ui 34에서 투시 대기 시간과 무기 수치를 변경할 수 있습니다.");
                json.add("specs",ServerFiles.GSON.toJsonTree(new Loadouts.Specs("TaCZ 거리별 피해 / 헤드샷 ×1.33","일반탄 / 관통탄 히트스캔","","관통탄 256블록","")));
                json.add("stats",ServerFiles.GSON.toJsonTree(List.of(new Loadouts.Stat("탄창","3발"),new Loadouts.Stat("관통력","초당 15 / 최대 200"),new Loadouts.Stat("관통 단계","10: 1블록 / 50: 3블록 / 100: 10블록 / 150: 무제한"),new Loadouts.Stat("투시","줌 20초 유지 · 발사 시 초기화"))));
                java.nio.file.Files.writeString(path,ServerFiles.GSON.toJson(json));
                java.nio.file.Files.writeString(marker,"M95 scope and penetration description updated.\n");return;
            }
        }
    }
    public static boolean is(ItemStack stack){return stack.hasTag()&&GUN.equals(stack.getTag().getString("GunId"));}
    public static boolean bullet(Projectile bullet){return bullet instanceof Las5Damage.BulletAccess access&&GUN.equals(String.valueOf(access.getGunId()));}
    public static double charge(LivingEntity owner){return CHARGE.getOrDefault(owner,0d);}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        var entity=event.getEntity();if(entity.level().isClientSide)return;
        if(!entity.isAlive()||entity.isSpectator()){CHARGE.remove(entity);return;}
        boolean carried=false;
        if(entity instanceof net.minecraft.world.entity.player.Player p) {
            for(int i=0;i<p.getInventory().getContainerSize();i++)carried|=updateMagazine(p.getInventory().getItem(i));
        }else carried=updateMagazine(entity.getMainHandItem())|updateMagazine(entity.getOffhandItem());
        if(carried)CHARGE.put(entity,Math.min(ExtraGadgetConfig.m95.maximumCharge(),charge(entity)+ExtraGadgetConfig.m95.rechargePerSecond()/20));
        else CHARGE.remove(entity);
    }
    private static boolean updateMagazine(ItemStack stack){
        if(!is(stack))return false;
        var tag=stack.getOrCreateTag();int capacity=ExtraGadgetConfig.m95.magazine();
        if(tag.getInt(MAGAZINE)!=capacity)tag.putInt(MAGAZINE,capacity);
        if(tag.getInt("GunCurrentAmmoCount")>capacity)tag.putInt("GunCurrentAmmoCount",capacity);
        return true;
    }
    public static CombatGadgets.Gauge gauge(LivingEntity owner){
        return is(owner.getMainHandItem())?new CombatGadgets.Gauge("m95",(float)charge(owner),(float)ExtraGadgetConfig.m95.maximumCharge(),false):null;
    }
    private static int spend(LivingEntity owner){
        var c=ExtraGadgetConfig.m95;double available=charge(owner),cost=0;int blocks=0;
        
        if(available>=c.unlimitedThreshold()){cost=c.unlimitedThreshold();blocks=Integer.MAX_VALUE;}
        else if(available>=c.thirdThreshold()){cost=c.thirdThreshold();blocks=c.thirdBlocks();}
        else if(available>=c.largeThreshold()){cost=c.largeThreshold();blocks=c.largeBlocks();}
        else if(available>=c.smallThreshold()){cost=c.smallThreshold();blocks=c.smallBlocks();}
        CHARGE.put(owner,Math.max(0,available-cost));return blocks;
    }
    /** Called at the bullet's first server tick, retaining TaCZ entity/headshot and damage handling. */
    public static void fire(Projectile bullet){
        if(bullet.level().isClientSide){bullet.discard();return;}
        if(!(bullet.getOwner() instanceof LivingEntity owner)||!owner.isAlive()){bullet.discard();return;}

        var c=ExtraGadgetConfig.m95;var motion=bullet.getDeltaMovement();
        Vec3 direction=motion.lengthSqr()>1e-8?motion.normalize():owner.getLookAngle();
        var blocks=new ArrayList<BlockHitResult>();
        Vec3 from=bullet.position(),end=blockEnd(bullet,from,from.add(direction.scale(c.hitscanRange())),spend(owner),blocks);
        bullet.setDeltaMovement(end.subtract(from));
        // Run TaCZ's own bullet collision pass, including its headshot, multi-hit and event handling.
        // Only the already-budgeted block segment is exempted from its second terrain ray.
        var trace=new NativeTrace(bullet,from,end);TRACE.set(trace);
        try {((NativeBullet)bullet).gungang$nativeBulletTick();}
        finally {TRACE.remove();}
        if(bullet.isRemoved()&&trace.hit!=null)end=trace.hit;
        for(var hit:blocks)if(from.distanceToSqr(hit.getLocation())<=from.distanceToSqr(end)+1e-6&&!SafeZones.contains(owner)&&!SpawnGateProtection.bulletBlocked(bullet,hit.getLocation()))
            GimmickEntity.hit(bullet.level(),hit.getBlockPos(),Float.MAX_VALUE);
        GungangNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->owner),
                new GungangNetwork.M95Shot(owner.getId(),from.x,from.y,from.z,end.x,end.y,end.z));
        bullet.discard();
    }
    private static Vec3 blockEnd(Projectile bullet,Vec3 start,Vec3 end,int budget,List<BlockHitResult> blocks){

        Vec3 direction=end.subtract(start).normalize(),cursor=start;
        var passed=new HashSet<BlockPos>();
        for(int i=0;i<2048&&cursor.distanceToSqr(start)<=end.distanceToSqr(start);i++){
            var hit=bullet.level().clip(new net.minecraft.world.level.ClipContext(cursor,end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,bullet));
            if(hit.getType()==HitResult.Type.MISS)return end;
            BlockPos pos=hit.getBlockPos();
            if(passed.add(pos)){
                if(GimmickEntity.at(bullet.level(),pos)!=null)blocks.add(hit);
                if(passed.size()>budget)return hit.getLocation();
            }
            // Exit this block cell exactly; air gaps never consume penetration.
            Vec3 at=hit.getLocation();double advance=Double.POSITIVE_INFINITY;
            for(int axis=0;axis<3;axis++){
                double v=axis==0?direction.x:axis==1?direction.y:direction.z;
                double p=axis==0?at.x:axis==1?at.y:at.z;
                int b=axis==0?pos.getX():axis==1?pos.getY():pos.getZ();
                if(Math.abs(v)>1e-9)advance=Math.min(advance,Math.max(0,((v>0?b+1:b)-p)/v));
            }
            cursor=at.add(direction.scale(advance+1e-5));
        }
        return end;
    }
    private M95(){}
}
