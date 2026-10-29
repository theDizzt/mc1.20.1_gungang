package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;

/** Per-match state for the extended disguise deck. No ability edits the map. */
public final class PropAbilities {
    private static final UUID CART=UUID.fromString("ca3ad028-bc47-4995-9a62-d91fdc065319"),BEACON=UUID.fromString("bcbd5028-bc47-4995-9a62-d91fdc065319");
    static final class Runtime {
        int cooldown,active,stun,born,contact;boolean mayfly,flying,flight,bees;
        Vec3 stunPosition,lastPosition;double flightY;
        final List<Mob> mobs=new ArrayList<>();
        final List<Bolt> bolts=new ArrayList<>();
    }
    private static final class Bolt {Vec3 pos;final UUID target;int age;Bolt(Vec3 pos,UUID target){this.pos=pos;this.target=target;}}
    private final PropHuntMatch match;
    private final Map<UUID,Runtime> states=new HashMap<>();
    PropAbilities(PropHuntMatch match){this.match=match;}
    private Runtime data(UUID id){return states.computeIfAbsent(id,k->{var r=new Runtime();r.born=match.now();return r;});}
    public static int cooldown(String item){return switch(item){case "minecraft:spawner"->84*20;case "minecraft:command_block","minecraft:end_crystal","minecraft:tnt","gungang:gas_barrel"->30*20;case "minecraft:dispenser"->20;case "gungang:small_health_pack"->ExtraGadgetConfig.ticks(GadgetConfig.smallHealthPack.cooldownSeconds());case "gungang:large_health_pack"->ExtraGadgetConfig.ticks(GadgetConfig.largeHealthPack.cooldownSeconds());default->0;};}
    public int remaining(UUID id){var r=data(id);return Math.max(0,r.cooldown-match.now());}
    public int active(UUID id){return Math.max(0,data(id).active-match.now());}
    public boolean stunned(UUID id){return data(id).stun>match.now();}
    public float mobHealth(UUID id){return (float)data(id).mobs.stream().filter(m->m instanceof net.minecraft.world.entity.monster.CaveSpider&&m.isAlive()&&!m.isRemoved()).mapToDouble(Mob::getHealth).sum();}
    public float mobMaximum(UUID id){return (float)data(id).mobs.stream().filter(m->m instanceof net.minecraft.world.entity.monster.CaveSpider&&m.isAlive()&&!m.isRemoved()).mapToDouble(Mob::getMaxHealth).sum();}
    public void initialize(ServerPlayer p,PropEntity prop){
        remove(p);var r=data(p.getUUID());r.born=match.now();r.lastPosition=p.position();
        String item=prop.item();
        if(item.equals("minecraft:flower_pot")){
            var pots=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues().stream().filter(b->b instanceof FlowerPotBlock).toList();
            prop.state(pots.get(p.getRandom().nextInt(pots.size())).defaultBlockState());
        }
        if(item.equals("minecraft:infested_stone")){
            var blocks=List.of(Blocks.INFESTED_STONE,Blocks.INFESTED_COBBLESTONE,Blocks.INFESTED_STONE_BRICKS,Blocks.INFESTED_MOSSY_STONE_BRICKS,Blocks.INFESTED_CRACKED_STONE_BRICKS,Blocks.INFESTED_CHISELED_STONE_BRICKS,Blocks.INFESTED_DEEPSLATE);
            prop.state(blocks.get(p.getRandom().nextInt(blocks.size())).defaultBlockState());
        }
        if(item.equals("gungang:mannequin")){var seekers=match.players().stream().filter(q->!match.hider(q.getUUID())).toList();prop.skin(seekers.isEmpty()?p.getUUID():seekers.get(p.getRandom().nextInt(seekers.size())).getUUID());}
        double maximum=item.equals("gungang:mannequin")?255:50;float health=p.getHealth();boolean resized=p.getMaxHealth()!=maximum;
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);p.setHealth(resized?p.getMaxHealth():Math.min(health,p.getMaxHealth()));
        modifier(p,CART,item.equals("minecraft:minecart")?1.5:0);
    }
    private static void modifier(ServerPlayer p,UUID id,double value){var attr=p.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;var old=attr.getModifier(id);if(old!=null&&old.getAmount()==value)return;if(old!=null)attr.removeModifier(id);if(value!=0)attr.addTransientModifier(new AttributeModifier(id,"prop_ability",value,AttributeModifier.Operation.MULTIPLY_TOTAL));}
    public void remove(ServerPlayer p){var r=states.remove(p.getUUID());if(r!=null){r.mobs.forEach(Entity::discard);stopFlight(p,r);}modifier(p,CART,0);modifier(p,BEACON,0);}
    public void clear(){for(var p:match.players())remove(p);states.values().forEach(r->r.mobs.forEach(Entity::discard));states.clear();}
    public void resetTransient(){for(var p:match.players()){var r=states.get(p.getUUID());if(r!=null){r.mobs.forEach(Entity::discard);r.mobs.clear();r.bolts.clear();r.active=0;r.stun=0;stopFlight(p,r);}}}
    private static void stopFlight(ServerPlayer p,Runtime r){if(!r.flight)return;r.flight=false;p.getAbilities().mayfly=r.mayfly;p.getAbilities().flying=r.flying;p.setNoGravity(false);p.fallDistance=0;p.onUpdateAbilities();}
    public boolean use(ServerPlayer p,PropEntity prop){
        String item=prop.item();var r=data(p.getUUID());int now=match.now();
        if(Set.of("minecraft:nether_portal","minecraft:lodestone","minecraft:command_block","minecraft:end_crystal").contains(item)&&!match.hunting())return true;
        if(item.equals("minecraft:spawner")&&!match.hunting()&&!match.hiding())return true;
        if(cooldown(item)>0&&r.cooldown>now)return true;
        switch(item){
            case "minecraft:anvil"->{prop.state(prop.state().is(Blocks.ANVIL)?Blocks.CHIPPED_ANVIL.defaultBlockState():prop.state().is(Blocks.CHIPPED_ANVIL)?Blocks.DAMAGED_ANVIL.defaultBlockState():Blocks.ANVIL.defaultBlockState());}
            case "minecraft:flower_pot"->{var pots=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues().stream().filter(b->b instanceof FlowerPotBlock).toList();prop.state(pots.get(p.getRandom().nextInt(pots.size())).defaultBlockState());}
            case "minecraft:spawner"->{
                var target=nearest(p.serverLevel(),prop.position(),Double.MAX_VALUE);
                Vec3 origin=target==null?prop.position():target.position(),at=origin.add(2,0,0);
                for(int i=0;i<16;i++){double a=i*Math.PI/8;var candidate=origin.add(Math.cos(a)*2,0,Math.sin(a)*2);if(p.level().noCollision(new AABB(candidate.x-.4,candidate.y,candidate.z-.4,candidate.x+.4,candidate.y+.6,candidate.z+.4))){at=candidate;break;}}
                var mob=PropSummons.spawn(p.serverLevel(),EntityType.CAVE_SPIDER,p.getUUID(),at,100,16,false);if(mob!=null){r.mobs.add(mob);r.cooldown=now+1680;}
            }
            case "minecraft:nether_portal"->{for(var target:match.players()){
                var disguise=match.prop(target.getUUID());var box=disguise==null?target.getBoundingBox():disguise.getBoundingBox();
                if(target!=p&&target.level()==prop.level()&&(!target.isSpectator()||disguise!=null)&&box.inflate(.15).intersects(prop.getBoundingBox()))match.relocate(target,match.randomMapPosition(p));
            }}
            case "minecraft:lodestone"->{for(var target:match.players()){var other=match.prop(target.getUUID());if(target!=p&&other!=null&&other.level()==prop.level()&&other.distanceToSqr(prop)<=24*24){match.unlock(target);var vector=prop.position().subtract(target.position());PlayerImpulse.apply(target,vector.normalize().scale(Math.min(2,vector.length()*.25)).add(0,.2,0));}}}
            case "gungang:blue_jump_pad","gungang:lime_jump_pad"->prop.toggle();
            case "gungang:conveyor_belt"->{prop.mode((prop.mode()+1)%4);prop.state(prop.state().setValue(ConveyorBlock.REVERSED,prop.mode()%2==1));}
            case "minecraft:command_block"->{match.unlock(p);r.mayfly=p.getAbilities().mayfly;r.flying=p.getAbilities().flying;r.flight=true;r.flightY=p.getY();r.active=now+100;r.cooldown=now+600;p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.setNoGravity(true);p.setDeltaMovement(p.getDeltaMovement().multiply(1,0,1));p.hurtMarked=true;p.onUpdateAbilities();}
            case "minecraft:oak_sign"->{
                var all=match.players().stream().filter(q->match.hider(q.getUUID())).toList();var teammate=all.isEmpty()?p:all.get(p.getRandom().nextInt(all.size()));
                var pos=match.prop(teammate.getUUID())==null?teammate.blockPosition():match.prop(teammate.getUUID()).blockPosition();
                var labels=List.of(p.getGameProfile().getName(),"(●´꒳`●)","(｡>﹏<｡)","(❀╹◡╹)","凸(｀0´)凸","٩( ᐛ )و","Σ(￣□￣;)",pos.getX()+", "+pos.getY()+", "+pos.getZ());prop.text(labels.get(p.getRandom().nextInt(labels.size())));
            }
            case "minecraft:infested_stone"->{
                if((match.hunting()||match.hiding())&&match.seekers().isEmpty()&&r.mobs.stream().noneMatch(m->m.isAlive()&&!m.isRemoved()))found(p,prop);
            }
            case "gungang:mannequin"->prop.mode((prop.mode()+1)%6);
            case "minecraft:beacon"->prop.toggle();
            case "minecraft:end_crystal"->{var target=nearest(p.serverLevel(),prop.position(),24);if(target==null)return true;r.bolts.add(new Bolt(prop.position().add(0,1,0),target.getUUID()));r.cooldown=now+600;}
            default->{return false;}
        }
        return true;
    }
    private ServerPlayer nearest(ServerLevel level,Vec3 pos,double range){return match.seekers().stream().filter(p->p.serverLevel()==level&&p.distanceToSqr(pos)<=range*range).min(Comparator.comparingDouble(p->p.distanceToSqr(pos))).orElse(null);}
    public void tick(ServerPlayer p,PropEntity prop){
        var r=data(p.getUUID());int now=match.now();String item=prop.item();
        boolean moving=r.lastPosition!=null&&p.position().subtract(r.lastPosition).horizontalDistanceSqr()>.0001;r.lastPosition=p.position();
        if(r.flight&&now>=r.active)stopFlight(p,r);
        if(r.flight){p.setDeltaMovement(p.getDeltaMovement().multiply(1,0,1));p.fallDistance=0;if(Math.abs(p.getY()-r.flightY)>.001){p.setPos(p.getX(),r.flightY,p.getZ());p.connection.teleport(p.getX(),r.flightY,p.getZ(),p.getYRot(),p.getXRot());prop.setPos(p.position());}}
        if(r.stun>now){p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;if(r.stunPosition!=null)p.connection.teleport(r.stunPosition.x,r.stunPosition.y,r.stunPosition.z,p.getYRot(),p.getXRot());}
        boolean beacon=match.players().stream().map(q->match.prop(q.getUUID())).anyMatch(other->other!=null&&other.item().equals("minecraft:beacon")&&other.open());
        modifier(p,BEACON,beacon?.2:0);
        if(item.equals("minecraft:sugar_cane")){int height=Math.min(3,1+(now-r.born)/1200);if(prop.height()!=height){prop.height(height);p.refreshDimensions();}}
        if(item.equals("minecraft:oak_sign")&&!match.locked(p.getUUID()))prop.state(prop.state().setValue(StandingSignBlock.ROTATION,Math.floorMod(Math.round(p.getYRot()*16/360),16)));
        if(Set.of("minecraft:minecart","gungang:mannequin","gungang:dried_ghast").contains(item)&&!match.locked(p.getUUID())){prop.setYRot(p.getYRot());prop.setYHeadRot(p.getYRot());prop.setYBodyRot(p.getYRot());}
        if(item.equals("minecraft:bee_nest")&&!r.bees){r.bees=true;for(int i=0;i<3;i++){var mob=PropSummons.spawn(p.serverLevel(),EntityType.BEE,p.getUUID(),prop.position().add(Math.cos(i*2.1),1,Math.sin(i*2.1)),100,36,true);if(mob!=null)r.mobs.add(mob);}}
        r.mobs.removeIf(Entity::isRemoved);for(int i=0;i<r.mobs.size();i++)PropSummons.tick(match,r.mobs.get(i),prop.position(),i);
        if(!match.hunting())return;
        for(var it=r.bolts.iterator();it.hasNext();){var bolt=it.next();var target=match.seekers().stream().filter(q->q.getUUID().equals(bolt.target)&&q.level()==prop.level()).findFirst().orElse(null);
            if(target==null||++bolt.age>100){it.remove();continue;}
            var goal=target.getBoundingBox().getCenter();var step=goal.subtract(bolt.pos);var next=bolt.pos.add(step.normalize().scale(Math.min(.6,step.length())));
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(bolt.pos,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.MISS){it.remove();continue;}
            p.serverLevel().sendParticles(ParticleTypes.DRAGON_BREATH,next.x,next.y,next.z,5,.07,.07,.07,.01);bolt.pos=next;
            if(next.distanceTo(goal)<.65){target.hurt(p.damageSources().magic(),50);p.serverLevel().sendParticles(ParticleTypes.EXPLOSION_EMITTER,next.x,next.y,next.z,1,0,0,0,0);p.level().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.PLAYERS,1,1);it.remove();}
        }
        if(now<r.contact)return;
        for(var target:match.seekers()){
            if(target.level()!=prop.level()||!target.getBoundingBox().inflate(.1).intersects(prop.getBoundingBox()))continue;
            if(item.equals("minecraft:fire")||item.equals("minecraft:soul_fire")){target.hurt(p.damageSources().inFire(),50);target.setSecondsOnFire(10);r.contact=now+20;}
            if(item.equals("minecraft:torchflower")){target.setSecondsOnFire(10);r.contact=now+20;}
            if(item.equals("minecraft:minecart")&&moving){target.hurt(p.damageSources().generic(),50);var away=target.position().subtract(prop.position()).normalize();PlayerImpulse.apply(target,target.getDeltaMovement().add(away.x*1.4,.5,away.z*1.4));r.stun=now+20;r.stunPosition=p.position();r.contact=now+20;}
        }
    }
    public void struck(ServerPlayer owner,PropEntity prop){
        if(!prop.item().equals("minecraft:dragon_egg"))return;
        for(int i=0;i<32;i++){var to=owner.position().add(owner.getRandom().nextInt(17)-8,owner.getRandom().nextInt(9)-4,owner.getRandom().nextInt(17)-8);if(to.distanceToSqr(owner.position())<=64&&owner.level().noCollision(owner,owner.getBoundingBox().move(to.subtract(owner.position())))){match.relocate(owner,to);break;}}
    }
    public void found(ServerPlayer owner,PropEntity prop){
        if(prop==null||!prop.item().equals("minecraft:infested_stone"))return;
        var r=data(owner.getUUID());for(int i=0;i<3;i++){var mob=PropSummons.spawn(owner.serverLevel(),EntityType.SILVERFISH,owner.getUUID(),prop.position().add((i-1)*.4,0,0),50,25,false);if(mob!=null)r.mobs.add(mob);}
    }
    /** Found infested-block minions keep attacking until review/reset. */
    public void orphanTick(){for(var entry:states.entrySet())if(match.prop(entry.getKey())==null){var r=entry.getValue();r.mobs.removeIf(Entity::isRemoved);for(var mob:r.mobs)PropSummons.tick(match,mob,mob.position(),0);}}
}
