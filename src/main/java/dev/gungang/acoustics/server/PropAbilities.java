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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Per-match state for the extended disguise deck. No ability edits the map. */
public final class PropAbilities {
    private static final UUID CART=UUID.fromString("ca3ad028-bc47-4995-9a62-d91fdc065319"),BEACON=UUID.fromString("bcbd5028-bc47-4995-9a62-d91fdc065319"),
            CLOAK=UUID.fromString("c10a6028-bc47-4995-9a62-d91fdc065319");
    static final String EUNBI="gungang:eunbi_doll",HANBI="gungang:hanbi_doll";
    /** 184 beam: 5 s of use refilled over 10 s, 50 damage a second; 191 cloak: 8 s refilled over 16 s, 2.4× speed. */
    static final int BEAM_TICKS=100,BEAM_REFILL=200,CLOAK_TICKS=160,CLOAK_REFILL=320;
    static final float BEAM_DPS=50,TNT_DAMAGE=80;
    static final double TNT_CART_RADIUS=4;
    static boolean doll(String item){return item.equals(EUNBI)||item.equals(HANBI);}
    /** Short-lived entities an ability leaves in the world: emoticon pop-ups and TNT about to go off. */
    private record Temp(Entity entity,int until,boolean tnt,UUID owner){}
    private final List<Temp> temps=new ArrayList<>();
    /** The hider whose daylight detector has turned the map's time, or null. */
    private UUID dayOwner;
    static final class Runtime {
        int cooldown,active,stun,born,contact,note,erupt,eruptReady,resonate=-1,cry,eyeStart,eyeTicks;
        /** 199 lightning storm until / next strike; 194 birthday song next note / position; 39 nozzle burst until. */
        int storm,stormNext,songAt,songNote,burst;boolean mayfly,flying,flight,bees,primary,cloak;
        /** Doll gauge in ticks of use left (-1: not a doll) and its maximum. */
        float gauge=-1,gaugeMax;
        Vec3 eyeFrom,eyeTo,eyeLand;
        Vec3 stunPosition,lastPosition;double flightY;
        final List<PropSummonEntity> mobs=new ArrayList<>();
        final List<Bolt> bolts=new ArrayList<>();
        final List<net.minecraft.world.entity.item.ItemEntity> discs=new ArrayList<>();
        void clearDiscs(){discs.forEach(Entity::discard);discs.clear();}
    }
    private static final class Bolt {Vec3 pos;final UUID target;int age;Bolt(Vec3 pos,UUID target){this.pos=pos;this.target=target;}}
    private final PropHuntMatch match;
    private final Map<UUID,Runtime> states=new HashMap<>();
    PropAbilities(PropHuntMatch match){this.match=match;}
    private Runtime data(UUID id){return states.computeIfAbsent(id,k->{var r=new Runtime();r.born=match.now();return r;});}
    public static int cooldown(String item){return switch(item){case "minecraft:spawner"->84*20;
        case "minecraft:respawn_anchor"->30*20;case "minecraft:end_portal_frame","minecraft:tnt_minecart"->5*20;case "minecraft:crying_obsidian","gungang:prettiest_block"->3*20;case "minecraft:daylight_detector"->2*20;case "minecraft:command_block","minecraft:end_crystal","minecraft:tnt","gungang:gas_barrel"->30*20;case "minecraft:dispenser","minecraft:jukebox"->20;case "minecraft:brewing_stand"->50*20;case "minecraft:conduit"->90*20;case "gungang:nozzle"->BURST_COOLDOWN;case "gungang:small_health_pack"->ExtraGadgetConfig.ticks(GadgetConfig.smallHealthPack.cooldownSeconds());case "gungang:large_health_pack"->ExtraGadgetConfig.ticks(GadgetConfig.largeHealthPack.cooldownSeconds());default->0;};}
    public int remaining(UUID id){var r=data(id);return Math.max(0,r.cooldown-match.now());}
    public int active(UUID id){return Math.max(0,data(id).active-match.now());}
    public boolean stunned(UUID id){return data(id).stun>match.now();}
    /** The second bar under the health bar: a doll's gauge in seconds, else the summoned spider's health. */
    public float auxiliary(UUID id){var r=data(id);return r.gauge>=0?r.gauge/20f:mobHealth(id);}
    public float auxiliaryMax(UUID id){var r=data(id);return r.gauge>=0?r.gaugeMax/20f:mobMaximum(id);}
    /** 191 cloak on: invisible to seekers and untouchable. */
    public boolean cloaked(UUID id){var r=states.get(id);return r!=null&&r.cloak;}
    /** A doll's gauge, 0..1 (bots ration their beam with it). */
    float gauge(UUID id){var r=states.get(id);return r==null||r.gauge<0||r.gaugeMax<=0?0:r.gauge/r.gaugeMax;}
    /** Attack key held (true) or let go on a doll disguise. */
    public void primary(LivingEntity p,PropEntity prop,boolean down){var r=data(p.getUUID());r.primary=down&&prop!=null&&doll(prop.item());}
    public float mobHealth(UUID id){return (float)data(id).mobs.stream().filter(m->m.kind()==PropSummonEntity.Kind.SPIDER&&m.isAlive()&&!m.isRemoved()).mapToDouble(PropSummonEntity::getHealth).sum();}
    public float mobMaximum(UUID id){return (float)data(id).mobs.stream().filter(m->m.kind()==PropSummonEntity.Kind.SPIDER&&m.isAlive()&&!m.isRemoved()).mapToDouble(PropSummonEntity::getMaxHealth).sum();}
    public void initialize(LivingEntity p,PropEntity prop){
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
        if(item.equals("gungang:hologram_projector")){prop.text(weapon(p));prop.mode(2*5+2);}
        if(item.equals("minecraft:armor_stand")){prop.text(armor(p.getRandom()));prop.mode(p.getRandom().nextInt(ARMOR_POSES));}
        if(doll(item)){r.gaugeMax=item.equals(EUNBI)?BEAM_TICKS:CLOAK_TICKS;r.gauge=r.gaugeMax;}
        if(item.equals("gungang:mannequin")){var seekers=match.players().stream().filter(q->!match.hider(q.getUUID())).toList();prop.skin(seekers.isEmpty()?p.getUUID():seekers.get(p.getRandom().nextInt(seekers.size())).getUUID());}
        double maximum=maxHealth(item);float health=p.getHealth();boolean resized=p.getMaxHealth()!=maximum;
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);p.setHealth(resized?p.getMaxHealth():Math.min(health,p.getMaxHealth()));
        modifier(p,CART,item.equals("minecraft:minecart")||item.equals("minecraft:tnt_minecart")?1.5:0);
    }
    /** Sturdy disguises (tagged "견고"): mannequins and dolls, (crying) obsidian, end crystal, geyser, war table, armory 255; big grass block 400; bedrock 999; every other block 50. */
    public static double maxHealth(String item){
        return Set.of("gungang:mannequin",EUNBI,HANBI,"minecraft:obsidian","minecraft:crying_obsidian","minecraft:end_crystal","gungang:geyser","gungang:war_table","gungang:armory").contains(item)?255
                :item.equals("gungang:big_grass_block")?400:item.equals("minecraft:bedrock")?999:50;
    }
    /** 176: four random armour pieces (or none in a slot), as item ids "head,chest,legs,feet". */
    static final int ARMOR_POSES=6;
    private static String armor(net.minecraft.util.RandomSource random){
        String[] kinds={"leather","chainmail","iron","golden","diamond","netherite",""};String[] slots={"helmet","chestplate","leggings","boots"};
        var parts=new ArrayList<String>();
        for(String slot:slots){
            String kind=kinds[random.nextInt(kinds.length)];
            if(slot.equals("helmet")&&random.nextInt(8)==0)parts.add(random.nextBoolean()?"minecraft:turtle_helmet":"minecraft:carved_pumpkin");
            else parts.add(kind.isEmpty()?"":"minecraft:"+kind+"_"+slot);
        }
        return String.join(",",parts);
    }
    /** Hologram projector: E steps the size, R the spin speed; mode = size step * 5 + speed (0 = still, 2 = the placed block's default). */
    public static final int[] HOLOGRAM_SIZES={50,75,100,150,200,300};
    private static final String[] HOLOGRAM_SPEEDS={"정지","느리게","보통","빠르게","매우 빠르게"};
    public static int hologramSize(int mode){return HOLOGRAM_SIZES[Math.floorMod(mode/5,HOLOGRAM_SIZES.length)];}
    public static int hologramSpeed(int mode){return Math.floorMod(mode,5);}
    /** A random selectable primary weapon as item NBT, shown by the projector only: it is never put in a container anyone can open. */
    private static String weapon(LivingEntity p){
        try{var list=Loadouts.load().primary();return list.isEmpty()?"":list.get(p.getRandom().nextInt(list.size())).stack().save(new net.minecraft.nbt.CompoundTag()).toString();}
        catch(java.io.IOException|RuntimeException e){return "";}
    }
    /**
     * Mozart, Piano Sonata No. 16 (K. 545), first movement opening at its written pitch, in semitones from the harp's own F#4
     * (C5 = 6). It spans A3 to C6, past the vanilla note block's two octaves, so it plays through the wide-range gungang:prop_note.
     */
    private static final int[] SONATA={6,10,13,5,6,8,6,15,13,18,13,13,11,10,11,10,
            3,5,6,8,10,11,13,15,1,3,5,6,8,10,11,13,-1,1,3,5,6,8,10,11,-2,-1,1,3,5,6,8,10,
            -4,-2,-1,1,3,5,6,8,-6,-4,-2,-1,1,3,5,6,-7,-6,-4,-2,-1,1,3,5,-9,-7,-6,-4,-2,-1,1,3,8,6,5,6,1,6};
    private static void modifier(LivingEntity p,UUID id,double value){if(p instanceof Mob&&value!=0)value=Math.sqrt(1+value)-1; // path-walking bots move attribute²
        var attr=p.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;var old=attr.getModifier(id);if(old!=null&&old.getAmount()==value)return;if(old!=null)attr.removeModifier(id);if(value!=0)attr.addTransientModifier(new AttributeModifier(id,"prop_ability",value,AttributeModifier.Operation.MULTIPLY_TOTAL));}
    public void remove(LivingEntity p){
        var r=states.remove(p.getUUID());if(r!=null){r.mobs.forEach(Entity::discard);r.clearDiscs();stopFlight(p,r);}
        modifier(p,CART,0);modifier(p,BEACON,0);modifier(p,CLOAK,0);
        // A new block, leaving the match or being found puts the map's time back.
        if(p.getUUID().equals(dayOwner))restoreDay();
    }
    public void clear(){for(var p:match.bodies())remove(p);states.values().forEach(r->{r.mobs.forEach(Entity::discard);r.clearDiscs();});states.clear();clearTemps();restoreDay();}
    public void resetTransient(){for(var p:match.bodies()){var r=states.get(p.getUUID());if(r!=null){r.mobs.forEach(Entity::discard);r.mobs.clear();r.bolts.clear();r.clearDiscs();r.active=0;r.stun=0;r.erupt=0;r.eyeLand=null;r.cry=0;r.storm=0;r.burst=0;r.primary=false;r.cloak=false;modifier(p,CLOAK,0);stopFlight(p,r);}}clearTemps();}
    private void clearTemps(){temps.forEach(t->t.entity().discard());temps.clear();}
    private void restoreDay(){if(dayOwner!=null){dayOwner=null;MapEnvironment.override(null);}}
    private static void stopFlight(LivingEntity p,Runtime r){if(!r.flight)return;r.flight=false;p.setNoGravity(false);p.fallDistance=0;if(p instanceof ServerPlayer sp){sp.getAbilities().mayfly=r.mayfly;sp.getAbilities().flying=r.flying;sp.onUpdateAbilities();}}
    private static ServerLevel level(LivingEntity p){return (ServerLevel)p.level();}
    /** Holds a body in place: a player's client is corrected at once, a bot is simply put back. */
    private static void pin(LivingEntity p,double x,double y,double z){if(p instanceof ServerPlayer sp)sp.connection.teleport(x,y,z,p.getYRot(),p.getXRot());else p.setPos(x,y,z);}
    public boolean use(LivingEntity p,PropEntity prop){
        String item=prop.item();var r=data(p.getUUID());int now=match.now();
        if(Set.of("minecraft:nether_portal","minecraft:lodestone","minecraft:command_block","minecraft:end_crystal","minecraft:tnt_minecart","minecraft:conduit").contains(item)&&!match.hunting()){
            if(item.equals("minecraft:end_crystal")||item.equals("minecraft:conduit"))PropHuntMatch.say(p,Component.literal(PropCatalog.name(item).getString()+": 찾는 시간에만 사용할 수 있습니다"),true);
            return true;
        }
        if(item.equals("minecraft:spawner")&&!match.hunting()&&!match.hiding())return true;
        if(cooldown(item)>0&&r.cooldown>now)return true;
        switch(item){
            case "minecraft:anvil"->{prop.state(prop.state().is(Blocks.ANVIL)?Blocks.CHIPPED_ANVIL.defaultBlockState():prop.state().is(Blocks.CHIPPED_ANVIL)?Blocks.DAMAGED_ANVIL.defaultBlockState():Blocks.ANVIL.defaultBlockState());}
            case "minecraft:flower_pot"->{var pots=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValues().stream().filter(b->b instanceof FlowerPotBlock).toList();prop.state(pots.get(p.getRandom().nextInt(pots.size())).defaultBlockState());}
            case "minecraft:spawner"->{
                var target=nearest(level(p),prop.position(),Double.MAX_VALUE);
                Vec3 origin=target==null?prop.position():target.position(),at=origin.add(2,0,0);
                for(int i=0;i<16;i++){double a=i*Math.PI/8;var candidate=origin.add(Math.cos(a)*2,0,Math.sin(a)*2);if(p.level().noCollision(new AABB(candidate.x-.4,candidate.y,candidate.z-.4,candidate.x+.4,candidate.y+.6,candidate.z+.4))){at=candidate;break;}}
                var mob=PropSummons.spawn(level(p),PropSummonEntity.Kind.SPIDER,p.getUUID(),at,100,16,false);if(mob!=null){r.mobs.add(mob);r.cooldown=now+1680;}
            }
            case "minecraft:nether_portal"->{for(var target:match.bodies()){
                var disguise=match.prop(target.getUUID());var box=disguise==null?target.getBoundingBox():disguise.getBoundingBox();
                if(target!=p&&target.level()==prop.level()&&(!target.isSpectator()||disguise!=null)&&box.inflate(.15).intersects(prop.getBoundingBox()))match.relocate(target,match.randomMapPosition(p));
            }}
            case "minecraft:lodestone"->{for(var target:match.bodies()){var other=match.prop(target.getUUID());if(target!=p&&other!=null&&other.level()==prop.level()&&other.distanceToSqr(prop)<=24*24){match.unlock(target);var vector=prop.position().subtract(target.position());PropHuntMatch.impulse(target,vector.normalize().scale(Math.min(2,vector.length()*.25)).add(0,.2,0));}}}
            case "gungang:blue_jump_pad","gungang:lime_jump_pad"->prop.toggle();
            // E only sinks the belt into the floor (its top flush with the floor) and raises it again; R turns it.
            case "gungang:conveyor_belt"->prop.toggle();
            case "minecraft:command_block"->{match.unlock(p);r.flight=true;r.flightY=p.getY();r.active=now+100;r.cooldown=now+600;if(p instanceof ServerPlayer sp){r.mayfly=sp.getAbilities().mayfly;r.flying=sp.getAbilities().flying;sp.getAbilities().mayfly=true;sp.getAbilities().flying=true;sp.onUpdateAbilities();}p.setNoGravity(true);p.setDeltaMovement(p.getDeltaMovement().multiply(1,0,1));p.hurtMarked=true;}
            case "minecraft:oak_sign"->{
                var all=match.bodies().stream().filter(q->match.hider(q.getUUID())).toList();var teammate=all.isEmpty()?p:all.get(p.getRandom().nextInt(all.size()));
                var pos=match.prop(teammate.getUUID())==null?teammate.blockPosition():match.prop(teammate.getUUID()).blockPosition();
                var labels=List.of(match.name(p.getUUID()),"(●´꒳`●)","(｡>﹏<｡)","(❀╹◡╹)","凸(｀0´)凸","٩( ᐛ )و","Σ(￣□￣;)",pos.getX()+", "+pos.getY()+", "+pos.getZ());prop.text(labels.get(p.getRandom().nextInt(labels.size())));
            }
            case "minecraft:infested_stone"->{
                if((match.hunting()||match.hiding())&&match.seekers().isEmpty()&&r.mobs.stream().noneMatch(m->m.isAlive()&&!m.isRemoved()))found(p,prop);
            }
            case "gungang:mannequin",EUNBI,HANBI->prop.mode((prop.mode()+1)%6);
            // 174: every other hider still hiding is pulled in around this anchor.
            case "minecraft:respawn_anchor"->{
                int moved=0;
                for(var target:match.bodies()){
                    if(target==p||!match.hider(target.getUUID())||match.prop(target.getUUID())==null||target.level()!=p.level())continue;
                    var to=around(target,prop.position(),moved);if(to==null)continue;
                    match.relocate(target,to);moved++;
                    level(p).sendParticles(ParticleTypes.REVERSE_PORTAL,to.x,to.y+.5,to.z,20,.3,.4,.3,.05);
                }
                if(moved==0){PropHuntMatch.tell(p,"불러올 팀원이 없습니다.");return true;}
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_SET_SPAWN,net.minecraft.sounds.SoundSource.BLOCKS,1.2f,1);
                level(p).sendParticles(ParticleTypes.PORTAL,prop.getX(),prop.getY()+.8,prop.getZ(),40,.4,.4,.4,.4);
                PropHuntMatch.tell(p,"리스폰 정박기: 팀원 "+moved+"명을 불러왔습니다.");r.cooldown=now+cooldown(item);
            }
            // 176: new random armour, or a new pose.
            case "minecraft:armor_stand"->{
                if(p.getRandom().nextBoolean())prop.text(armor(p.getRandom()));else prop.mode((prop.mode()+1+p.getRandom().nextInt(ARMOR_POSES-1))%ARMOR_POSES);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
            }
            // 177: day ↔ night over the whole map until this disguise ends.
            case "minecraft:daylight_detector"->{
                if(dayOwner!=null&&!dayOwner.equals(p.getUUID()))return true;
                long time=Math.floorMod(MapEnvironment.dayTime(),24000L);boolean day=time<12000||time>=23000;
                MapEnvironment.override(day?18000L:6000L);dayOwner=p.getUUID();
                prop.state(prop.state().setValue(DaylightDetectorBlock.INVERTED,day));
                PropHuntMatch.tell(p,day?"햇빛 감지기: 밤이 되었습니다":"햇빛 감지기: 낮이 되었습니다");r.cooldown=now+cooldown(item);
            }
            // 178: a player writes in the book through their client (PropHuntClient); a bot scribbles a line.
            case "minecraft:lectern"->{if(!(p instanceof ServerPlayer))prop.text(LECTERN.get(p.getRandom().nextInt(LECTERN.size())));}
            // 179: an eye of ender flies where the hider looks; the hider follows it there.
            case "minecraft:end_portal_frame"->{
                var dir=p.getLookAngle();var from=prop.getBoundingBox().getCenter().add(0,.4,0);var end=from.add(dir.scale(48));
                var hit=p.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                var land=hit.getType()==HitResult.Type.MISS?end:hit.getLocation().subtract(dir.scale(.6));
                var spot=landing(p,land);
                if(spot==null){PropHuntMatch.tell(p,"엔더의 눈이 내려앉을 곳이 없습니다.");return true;}
                r.eyeFrom=from;r.eyeTo=land;r.eyeLand=spot;r.eyeStart=now;r.eyeTicks=Math.max(6,(int)(from.distanceTo(land)/1.2));r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.ENDER_EYE_LAUNCH,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            }
            // 188: tears and whimpering for a couple of seconds.
            case "minecraft:crying_obsidian"->{r.cry=now+50;r.cooldown=now+cooldown(item);}
            // 187: hearts, a lovely chime and an emoticon popping up over the block.
            case "gungang:prettiest_block"->{
                var level=level(p);var top=prop.getBoundingBox().getCenter().add(0,.7,0);
                level.sendParticles(ParticleTypes.HEART,top.x,top.y,top.z,10,.5,.35,.5,0);
                level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.ALLAY_AMBIENT_WITH_ITEM,net.minecraft.sounds.SoundSource.BLOCKS,1.2f,1.25f);
                level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.BLOCKS,1.2f,1.6f);
                popup(level,top.add(0,.5,0),"(˶ㅇᗜㅇ˶) ⑉♥",now+40);r.cooldown=now+cooldown(item);
            }
            // 189: drops a lit TNT that goes off 5 s later (80 to seekers in 4 blocks, no block damage).
            case "minecraft:tnt_minecart"->{
                var tnt=new net.minecraft.world.entity.item.PrimedTnt(p.level(),prop.getX(),prop.getY()+.3,prop.getZ(),null);
                tnt.setFuse(120);tnt.addTag(PropSummons.TAG);p.level().addFreshEntity(tnt);temps.add(new Temp(tnt,now+100,true,p.getUUID()));
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.TNT_PRIMED,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
                r.cooldown=now+cooldown(item);
            }
            case "minecraft:beacon"->prop.toggle();
            case "minecraft:end_crystal"->{
                // The nearest seeker the bolt can actually reach (a clear line from the crystal), else nothing is spent.
                var from=prop.position().add(0,1,0);
                var target=match.seekers().stream().filter(q->q.level()==prop.level()&&q.distanceToSqr(from)<=24*24
                        &&p.level().clip(new ClipContext(from,q.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)
                        .min(Comparator.comparingDouble(q->q.distanceToSqr(from))).orElse(null);
                if(target==null){PropHuntMatch.say(p,Component.literal("엔드 수정: 24블록 안에 보이는 술래가 없습니다"),true);return true;}
                r.bolts.add(new Bolt(from,target.getUUID()));r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,net.minecraft.sounds.SoundSource.PLAYERS,1.2f,1.4f);
                level(p).sendParticles(ParticleTypes.DRAGON_BREATH,from.x,from.y,from.z,20,.2,.2,.2,.02);
            }
            case "minecraft:sculk_sensor"->{prop.toggle();prop.state(prop.state().setValue(SculkSensorBlock.PHASE,prop.open()?SculkSensorPhase.ACTIVE:SculkSensorPhase.INACTIVE));if(prop.open())scream(level(p),prop);}
            case "minecraft:sculk_shrieker"->{prop.toggle();prop.state(prop.state().setValue(SculkShriekerBlock.SHRIEKING,prop.open()));if(prop.open())scream(level(p),prop);}
            case "gungang:hologram_projector"->{int size=(prop.mode()/5+1)%HOLOGRAM_SIZES.length;prop.mode(size*5+hologramSpeed(prop.mode()));PropHuntMatch.tell(p,"홀로그램 크기 "+HOLOGRAM_SIZES[size]+"%");}
            case "gungang:shutter"->PropInteraction.shutter(prop);
            case "minecraft:sea_pickle"->prop.state(prop.state().cycle(SeaPickleBlock.PICKLES));
            case "minecraft:turtle_egg"->prop.state(prop.state().cycle(TurtleEggBlock.EGGS));
            case "minecraft:note_block"->{
                int note=SONATA[r.note];r.note=(r.note+1)%SONATA.length;
                // The block state and particle colour only cover the vanilla range; the pitch does not.
                int shown=net.minecraft.util.Mth.clamp(note+12,0,24);prop.state(prop.state().setValue(BlockStateProperties.NOTE,shown));
                p.level().playSound(null,prop.getX(),prop.getY()+.5,prop.getZ(),GimmickBlocks.PROP_NOTE.get(),net.minecraft.sounds.SoundSource.RECORDS,3,(float)Math.pow(2,note/12d));
                level(p).sendParticles(ParticleTypes.NOTE,prop.getX(),prop.getY()+1.2,prop.getZ(),0,shown/24d,0,0,1);
            }
            case "minecraft:jukebox"->{
                // A disc flies up out of the top; nobody can pick it up and it is gone after three seconds.
                var discs=net.minecraftforge.registries.ForgeRegistries.ITEMS.tags().getTag(net.minecraft.tags.ItemTags.MUSIC_DISCS).stream().toList();if(discs.isEmpty())return true;
                var disc=new net.minecraft.world.entity.item.ItemEntity(p.level(),prop.getX(),prop.getY()+1.05,prop.getZ(),new net.minecraft.world.item.ItemStack(discs.get(p.getRandom().nextInt(discs.size()))),(p.getRandom().nextDouble()-.5)*.1,.75,(p.getRandom().nextDouble()-.5)*.1);
                disc.setNeverPickUp();p.level().addFreshEntity(disc);r.discs.add(disc);r.cooldown=now+20;
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.DISPENSER_LAUNCH,net.minecraft.sounds.SoundSource.BLOCKS,1,1.2f);
            }
            case "minecraft:brewing_stand"->{
                // One random 15 s effect for every hider still hiding; no particles, so it gives nobody away.
                var effects=List.of(net.minecraft.world.effect.MobEffects.JUMP,net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,net.minecraft.world.effect.MobEffects.INVISIBILITY);var names=List.of("점프 강화","신속","투명화");
                int pick=p.getRandom().nextInt(effects.size());
                for(var q:match.bodies())if(match.hider(q.getUUID())&&match.prop(q.getUUID())!=null){q.addEffect(new net.minecraft.world.effect.MobEffectInstance(effects.get(pick),300,0,false,false,true));PropHuntMatch.say(q,Component.literal("양조기: "+names.get(pick)+" 15초"),true);}
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW,net.minecraft.sounds.SoundSource.BLOCKS,1,1);r.cooldown=now+1000;
            }
            case "minecraft:bell"->{
                // Like a raid bell: three seconds after the ring, seekers within 48 blocks glow for three seconds.
                // No cooldown: ring freely. Rings while the resonance is pending do not push its glow back.
                PropInteraction.ring(prop);if(r.resonate<0)r.resonate=now+60;
            }
            // 199: a lightning storm over seekers within 24 blocks for four seconds.
            case "minecraft:conduit"->{
                if(nearest(level(p),prop.position(),STORM_RANGE)==null){PropHuntMatch.say(p,Component.literal("전달체: 24블록 안에 술래가 없습니다"),true);return true;}
                r.storm=now+STORM_TICKS;r.stormNext=now;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.CONDUIT_ACTIVATE,net.minecraft.sounds.SoundSource.BLOCKS,2,1);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER,net.minecraft.sounds.SoundSource.WEATHER,3,1);
            }
            // 39: E opens a three-second burst that throws whatever stands in the stream into the air.
            case "gungang:nozzle"->{
                r.burst=now+BURST_TICKS;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,net.minecraft.sounds.SoundSource.BLOCKS,1.5f,.7f);
            }
            case "minecraft:bubble_column"->{
                prop.state(prop.state().cycle(BubbleColumnBlock.DRAG_DOWN));boolean down=prop.state().getValue(BubbleColumnBlock.DRAG_DOWN);
                p.level().playSound(null,prop.blockPosition(),down?net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE:net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
                PropHuntMatch.say(p,Component.literal(down?"거품 기둥: 끌어내림":"거품 기둥: 밀어올림"),true);
            }
            default->{return false;}
        }
        return true;
    }
    /** R on a hologram projector steps the spin speed instead of turning the block. */
    public boolean rotate(LivingEntity p,PropEntity prop){
        if(!prop.item().equals("gungang:hologram_projector"))return false;
        int speed=(hologramSpeed(prop.mode())+1)%5;prop.mode(prop.mode()/5*5+speed);PropHuntMatch.say(p,Component.literal("홀로그램 회전 "+HOLOGRAM_SPEEDS[speed]),true);return true;
    }
    /** The sculk shriek (or sensor clicking) with its particles, repeated while toggled on. */
    private static void scream(ServerLevel level,PropEntity prop){
        if(prop.item().equals("minecraft:sculk_shrieker")){
            level.playSound(null,prop.getX(),prop.getY()+.5,prop.getZ(),net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,net.minecraft.sounds.SoundSource.BLOCKS,2,.6f+level.random.nextFloat()*.4f);
            for(int i=0;i<10;i++)level.sendParticles(new net.minecraft.core.particles.ShriekParticleOption(i*5),prop.getX(),prop.getY()+.5,prop.getZ(),1,0,0,0,0);
        }else{
            level.playSound(null,prop.getX(),prop.getY()+.5,prop.getZ(),net.minecraft.sounds.SoundEvents.SCULK_CLICKING,net.minecraft.sounds.SoundSource.BLOCKS,2,.6f+level.random.nextFloat()*.4f);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,prop.getX(),prop.getY()+.6,prop.getZ(),12,.3,.2,.3,.02);
        }
    }
    /**
     * Hot spring disguises: the geyser erupts when a seeker comes within 3 blocks (3 s on, 4 s off) and sets seekers in it alight;
     * the nozzle and bath toad streams heal hiders. Effects apply while hunting; the water is always shown.
     */
    private void thermal(LivingEntity p,PropEntity prop,Runtime r,int now){
        // Gate on the level's clock, not the match's (server tick count): spray() sends its particles when game time is a
        // multiple of 4, and with an odd offset between the two clocks an even match tick never landed on one.
        if(!(prop.state().getBlock() instanceof ThermalEmitterBlock block)||level(p).getGameTime()%2!=0)return;
        var level=level(p);boolean hunting=match.hunting();var base=prop.position();
        if(block.geyser()){
            var plume=new AABB(base.x-1.5,base.y,base.z-1.5,base.x+1.5,base.y+8,base.z+1.5);var seekers=match.seekers();
            if(hunting&&now>=r.eruptReady&&seekers.stream().anyMatch(s->s.level()==level&&s.getBoundingBox().intersects(plume.inflate(3,0,3)))){
                r.erupt=now+60;r.eruptReady=now+140;level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.BLOCKS,1.2f,.65f);
            }
            if(now<r.erupt)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var start=base.add(x,.3,z);ThermalEmitterEntity.spray(level,start,start.add(0,7.7,0),true,q->hunting&&seekers.contains(q));}
            else if(now%10==0)level.sendParticles(ParticleTypes.CLOUD,base.x,base.y+.3,base.z,2,.4,.1,.4,.02);
        }else{
            var forward=Vec3.atLowerCornerOf(prop.state().getValue(ThermalEmitterBlock.FACING).getNormal());
            var start=base.add(0,block.kind.equals("bath_toad")?.42:.65,0).add(forward.scale(.48));
            ThermalEmitterEntity.spray(level,start,start.add(forward.scale(3)).add(0,-.35,0),false,q->hunting&&match.hider(q.getUUID()));
        }
    }
    private static final List<String> LECTERN=List.of("술래는 여기 없음","오늘의 운세: 술래에게 들킬 확률 0%","(｡•̀ᴗ-)✧ 찾아봐~","이 책을 읽는 당신은 이미 늦었다","Lorem ipsum dolor sit amet");
    /** A free spot for {@code target}'s body in a ring around {@code centre}; the n-th caller gets the n-th place first. */
    private static Vec3 around(LivingEntity target,Vec3 centre,int n){
        for(int ring=0;ring<3;ring++)for(int i=0;i<8;i++){
            double angle=(n*3+i)*Math.PI/4,radius=1.3+ring*.8;
            for(double dy:new double[]{0,.5,1,-.5}){
                var to=centre.add(Math.cos(angle)*radius,dy,Math.sin(angle)*radius);
                if(target.level().noCollision(target,target.getBoundingBox().move(to.subtract(target.position()))))return to;
            }
        }
        return null;
    }
    /** Where a body fits at (or right around) a point the eye of ender reached. */
    private static Vec3 landing(LivingEntity p,Vec3 point){
        double h=p.getBbHeight();
        for(double dy:new double[]{-h/2,0,-h,.5,1})for(double dx:new double[]{0,.5,-.5})for(double dz:new double[]{0,.5,-.5}){
            var to=point.add(dx,dy,dz);
            if(p.level().noCollision(p,p.getBoundingBox().move(to.subtract(p.position()))))return to;
        }
        return null;
    }
    /** A floating line of text for a moment (a text display, removed with the match's summons). */
    private void popup(ServerLevel level,Vec3 at,String text,int until){
        var display=net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(level);if(display==null)return;
        var tag=new net.minecraft.nbt.CompoundTag();
        tag.putString("text",net.minecraft.network.chat.Component.Serializer.toJson(Component.literal(text).withStyle(s->s.withColor(0xFF8AD0).withBold(true))));
        tag.putString("billboard","center");tag.putInt("background",0x30000000);tag.putBoolean("shadow",true);
        display.load(tag);display.setPos(at);display.addTag(PropSummons.TAG);
        if(level.addFreshEntity(display))temps.add(new Temp(display,until,false,null));
    }
    /** Pop-ups float up and fade out; a TNT cart's TNT blows at its time (the vanilla blast, which breaks blocks, never runs). */
    private void tickTemps(){
        int now=match.now();
        for(var it=temps.iterator();it.hasNext();){
            var t=it.next();var e=t.entity();
            if(e.isRemoved()){it.remove();continue;}
            if(!t.tnt())e.setPos(e.getX(),e.getY()+.015,e.getZ());
            if(now<t.until())continue;
            if(t.tnt()&&match.hunting())match.blast((ServerLevel)e.level(),e.position(),TNT_CART_RADIUS,TNT_DAMAGE,t.owner());
            e.discard();it.remove();
        }
    }
    /** 184: the beam from the doll's head along the hider's view, unlimited range, 50 damage a second to the first seeker in it. */
    private void beam(LivingEntity p,PropEntity prop,int now){
        var level=level(p);var dir=p.getLookAngle();var from=prop.position().add(0,1.55,0).add(dir.scale(.4));var end=from.add(dir.scale(256));
        var hit=level.clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()!=HitResult.Type.MISS)end=hit.getLocation();
        LivingEntity target=null;double best=from.distanceToSqr(end);Vec3 at=end;
        for(var seeker:match.seekers()){
            if(seeker.level()!=level)continue;
            var clip=seeker.getBoundingBox().inflate(.3).clip(from,end);
            if(clip.isPresent()&&from.distanceToSqr(clip.get())<best){best=from.distanceToSqr(clip.get());target=seeker;at=clip.get();}
        }
        // 5 damage every other tick (50 a second); the hit pause is cleared so every share lands.
        if(target!=null&&now%2==0){target.invulnerableTime=0;target.hurt(p.damageSources().magic(),BEAM_DPS/10);}
        double length=from.distanceTo(at);int points=(int)Math.min(200,length/.35);
        var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f,.55f,.85f),1.1f);
        for(int i=0;i<=points;i++){var pos=from.add(dir.scale(length*i/Math.max(1,points)));level.sendParticles(dust,pos.x,pos.y,pos.z,1,0,0,0,0);}
        if(target!=null)level.sendParticles(ParticleTypes.END_ROD,at.x,at.y,at.z,3,.1,.1,.1,.02);
        if(now%8==0)level.playSound(null,from.x,from.y,from.z,net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,net.minecraft.sounds.SoundSource.PLAYERS,1,1.8f);
    }
    private LivingEntity nearest(ServerLevel level,Vec3 pos,double range){return match.seekers().stream().filter(p->p.level()==level&&p.distanceToSqr(pos)<=range*range).min(Comparator.comparingDouble(p->p.distanceToSqr(pos))).orElse(null);}
    static final int STORM_TICKS=80,STORM_INTERVAL=3,BURST_TICKS=60,BURST_COOLDOWN=10*20;
    static final double STORM_RANGE=24,STRIKE_RADIUS=2.5,BURST_TALL=2,BURST_LAUNCH=1.25;
    static final float STRIKE_DAMAGE=10;
    /** 199: a strike every few ticks, mostly right on a seeker in range, otherwise somewhere around them. Hits seekers only. */
    private void storm(LivingEntity p,PropEntity prop,Runtime r,int now){
        if(now>=r.storm||now<r.stormNext)return;
        r.stormNext=now+STORM_INTERVAL;
        var level=level(p);var random=p.getRandom();
        var targets=match.seekers().stream().filter(q->q.level()==level&&q.distanceToSqr(prop)<=STORM_RANGE*STORM_RANGE).toList();
        if(targets.isEmpty())return;
        var target=targets.get(random.nextInt(targets.size()));double spread=random.nextFloat()<.7f?1:4;
        var at=target.position().add((random.nextDouble()*2-1)*spread,0,(random.nextDouble()*2-1)*spread);
        // A bolt, even visual-only, scrubs copper and powers lightning rods where it lands: no strike entity there (no map edits).
        var ground=level.getBlockState(BlockPos.containing(at.x,at.y-1e-6,at.z)).getBlock();
        if(ground instanceof WeatheringCopper||ground instanceof LightningRodBlock)level.sendParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y+.5,at.z,30,.3,.8,.3,.3);
        else{var bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt==null)return;bolt.moveTo(at.x,at.y,at.z);bolt.setVisualOnly(true);level.addFreshEntity(bolt);}
        for(var seeker:targets)if(seeker.position().distanceToSqr(at)<=STRIKE_RADIUS*STRIKE_RADIUS){seeker.invulnerableTime=0;seeker.hurt(p.damageSources().magic(),STRIKE_DAMAGE);}
    }
    /**
     * 194: while its candle burns, the birthday cake plays "Happy Birthday" on the wide-range note sound, on a loop.
     * Notes are semitones from the harp's F#4; lengths in ticks.
     */
    private static final int[][] BIRTHDAY={{-6,6},{-6,2},{-4,8},{-6,8},{-1,8},{-2,16},{-6,6},{-6,2},{-4,8},{-6,8},{1,8},{-1,16},
            {-6,6},{-6,2},{6,8},{3,8},{-1,8},{-2,8},{-4,16},{4,6},{4,2},{3,8},{-1,8},{1,8},{-1,16},{Integer.MIN_VALUE,24}};
    private void song(LivingEntity p,PropEntity prop,Runtime r,int now){
        var state=prop.state();
        if(!(state.getBlock() instanceof CandleCakeBlock)||!state.getValue(BlockStateProperties.LIT)){r.songNote=0;r.songAt=0;return;}
        if(now<r.songAt)return;
        var note=BIRTHDAY[r.songNote];r.songNote=(r.songNote+1)%BIRTHDAY.length;r.songAt=now+note[1];
        if(note[0]==Integer.MIN_VALUE)return;
        p.level().playSound(null,prop.getX(),prop.getY()+.5,prop.getZ(),GimmickBlocks.PROP_NOTE.get(),net.minecraft.sounds.SoundSource.RECORDS,2,(float)Math.pow(2,note[0]/12d));
        level(p).sendParticles(ParticleTypes.NOTE,prop.getX(),prop.getY()+1,prop.getZ(),0,net.minecraft.util.Mth.clamp(note[0]+12,0,24)/24d,0,0,1);
    }
    /** 39: the burst stream throws seekers and other hiders' disguises standing in it into the air. */
    private void burst(LivingEntity p,PropEntity prop,Runtime r,int now){
        if(now>=r.burst||!(prop.state().getBlock() instanceof NozzleBlock nozzle))return;
        var level=level(p);var pos=prop.blockPosition();
        nozzle.particles(level,pos,BURST_TALL,4);
        if(now%2==0)level.sendParticles(nozzle.acid()?NozzleBlock.ACID_SPRAY:ParticleTypes.SPLASH,pos.getX()+.5,pos.getY()+1.2,pos.getZ()+.5,12,.15,.4,.15,.2);
        var column=nozzle.column(pos,BURST_TALL);
        for(var body:match.bodies()){
            if(body==p||body.level()!=level||body.getDeltaMovement().y>BURST_LAUNCH*.5)continue;
            boolean seeker=!match.hider(body.getUUID());
            if(seeker&&!match.seekers().contains(body))continue; // frozen (hiding time) or out
            var disguise=match.prop(body.getUUID());
            var box=seeker||disguise==null?body.getBoundingBox():disguise.getBoundingBox();
            if(box.intersects(column))match.launch(body,BURST_LAUNCH);
        }
    }
    public void tick(LivingEntity p,PropEntity prop){
        var r=data(p.getUUID());int now=match.now();String item=prop.item();
        boolean moving=r.lastPosition!=null&&p.position().subtract(r.lastPosition).horizontalDistanceSqr()>.0001;r.lastPosition=p.position();
        if(r.flight&&now>=r.active)stopFlight(p,r);
        if(r.flight){p.setDeltaMovement(p.getDeltaMovement().multiply(1,0,1));p.fallDistance=0;if(Math.abs(p.getY()-r.flightY)>.001){p.setPos(p.getX(),r.flightY,p.getZ());pin(p,p.getX(),r.flightY,p.getZ());prop.setPos(p.position());}}
        if(r.stun>now){p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;if(r.stunPosition!=null)pin(p,r.stunPosition.x,r.stunPosition.y,r.stunPosition.z);}
        boolean beacon=match.bodies().stream().map(q->match.prop(q.getUUID())).anyMatch(other->other!=null&&other.item().equals("minecraft:beacon")&&other.open());
        modifier(p,BEACON,beacon?.2:0);
        if(item.equals("minecraft:sugar_cane")){int height=Math.min(3,1+(now-r.born)/1200);if(prop.height()!=height){prop.height(height);p.refreshDimensions();}}
        if(item.equals("minecraft:oak_sign")&&!match.locked(p.getUUID()))prop.state(prop.state().setValue(StandingSignBlock.ROTATION,Math.floorMod(Math.round(p.getYRot()*16/360),16)));
        if(Set.of("minecraft:minecart","gungang:mannequin","gungang:dried_ghast",EUNBI,HANBI,"minecraft:armor_stand","minecraft:tnt_minecart").contains(item)&&!match.locked(p.getUUID())){prop.setYRot(p.getYRot());prop.setYHeadRot(p.getYRot());prop.setYBodyRot(p.getYRot());}
        if(item.equals("minecraft:bee_nest")&&!r.bees){r.bees=true;for(int i=0;i<3;i++){var mob=PropSummons.spawn(level(p),PropSummonEntity.Kind.BEE,p.getUUID(),prop.position().add(Math.cos(i*2.1),1,Math.sin(i*2.1)),100,36,true);if(mob!=null)r.mobs.add(mob);}}
        r.mobs.removeIf(Entity::isRemoved);for(int i=0;i<r.mobs.size();i++)PropSummons.tick(match,r.mobs.get(i),prop.position(),i);
        r.discs.removeIf(disc->{if(disc.isRemoved()||disc.tickCount>60){disc.discard();return true;}return false;});
        // Dolls: the attack key held spends the gauge (beam / cloak, hunting only); let go, it refills.
        if(doll(item)){
            boolean eunbi=item.equals(EUNBI);if(r.gauge<0){r.gaugeMax=eunbi?BEAM_TICKS:CLOAK_TICKS;r.gauge=r.gaugeMax;}
            boolean using=r.primary&&match.hunting()&&r.gauge>0;
            if(using){r.gauge=Math.max(0,r.gauge-1);if(eunbi)beam(p,prop,now);}
            else r.gauge=Math.min(r.gaugeMax,r.gauge+r.gaugeMax/(eunbi?BEAM_REFILL:CLOAK_REFILL));
            boolean cloak=using&&!eunbi;
            if(cloak!=r.cloak){r.cloak=cloak;p.level().playSound(null,prop.blockPosition(),cloak?net.minecraft.sounds.SoundEvents.ILLUSIONER_MIRROR_MOVE:net.minecraft.sounds.SoundEvents.ILLUSIONER_CAST_SPELL,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.4f);}
            modifier(p,CLOAK,r.cloak?1.4:0);
        }
        // 179: the eye in flight, then the hider lands where it went.
        if(r.eyeLand!=null){
            double t=Math.min(1,(now-r.eyeStart)/(double)r.eyeTicks);var at=r.eyeFrom.lerp(r.eyeTo,t);
            level(p).sendParticles(ParticleTypes.PORTAL,at.x,at.y,at.z,6,.1,.1,.1,.2);level(p).sendParticles(ParticleTypes.END_ROD,at.x,at.y,at.z,1,0,0,0,0);
            if(t>=1){
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
                match.relocate(p,r.eyeLand);r.eyeLand=null;
                p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.ENDER_EYE_DEATH,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
                p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            }
        }
        // 188: crying.
        if(now<r.cry){
            var box=prop.getBoundingBox();var random=p.getRandom();
            for(int i=0;i<3;i++)level(p).sendParticles(random.nextBoolean()?ParticleTypes.FALLING_OBSIDIAN_TEAR:ParticleTypes.DRIPPING_OBSIDIAN_TEAR,box.minX+random.nextDouble()*box.getXsize(),box.minY+random.nextDouble()*box.getYsize(),box.minZ+random.nextDouble()*box.getZsize(),1,0,0,0,0);
            if((r.cry-now)%16==0)p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.WOLF_WHINE,net.minecraft.sounds.SoundSource.BLOCKS,1.2f,.7f+random.nextFloat()*.2f);
        }
        // A brewing stand's invisibility (or a doll's cloak) hides the disguise itself from seekers (PropRenderer); shots still land.
        boolean invisible=p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)||r.cloak;if(prop.isInvisible()!=invisible)prop.setInvisible(invisible);
        if(prop.open()&&(item.equals("minecraft:sculk_sensor")||item.equals("minecraft:sculk_shrieker"))&&now%40==0)scream(level(p),prop);
        thermal(p,prop,r,now);
        if(r.resonate>=0&&now>=r.resonate){
            r.resonate=-1;
            for(var seeker:match.seekers())if(seeker.level()==prop.level()&&seeker.distanceToSqr(prop)<=48*48)seeker.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,60,0,false,false,false));
            p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BELL_RESONATE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
        }
        song(p,prop,r,now);
        burst(p,prop,r,now);
        if(!match.hunting())return;
        storm(p,prop,r,now);
        // Hot spring water heals the hider soaking in it (only while hunting; seekers never touch the healing).
        if(item.equals("gungang:hot_spring_water"))HotSpringEffect.touch(p);
        for(var it=r.bolts.iterator();it.hasNext();){var bolt=it.next();var target=match.seekers().stream().filter(q->q.getUUID().equals(bolt.target)&&q.level()==prop.level()).findFirst().orElse(null);
            if(target==null||++bolt.age>100){it.remove();continue;}
            var goal=target.getBoundingBox().getCenter();var step=goal.subtract(bolt.pos);var next=bolt.pos.add(step.normalize().scale(Math.min(.6,step.length())));
            var hit=p.level().clip(new net.minecraft.world.level.ClipContext(bolt.pos,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.MISS){it.remove();continue;}
            level(p).sendParticles(ParticleTypes.DRAGON_BREATH,next.x,next.y,next.z,5,.07,.07,.07,.01);bolt.pos=next;
            if(next.distanceTo(goal)<.65){target.hurt(p.damageSources().magic(),50);level(p).sendParticles(ParticleTypes.EXPLOSION_EMITTER,next.x,next.y,next.z,1,0,0,0,0);p.level().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.PLAYERS,1,1);it.remove();}
        }
        if(now<r.contact)return;
        for(var target:match.seekers()){
            if(target.level()!=prop.level()||!target.getBoundingBox().inflate(.1).intersects(prop.getBoundingBox()))continue;
            if(item.equals("minecraft:fire")||item.equals("minecraft:soul_fire")){target.hurt(p.damageSources().inFire(),50);target.setSecondsOnFire(10);r.contact=now+20;}
            if(item.equals("minecraft:torchflower")){target.setSecondsOnFire(10);r.contact=now+20;}
            if(item.equals("minecraft:water"))target.clearFire();
            if(item.equals("minecraft:bubble_column")){
                // Rising columns throw seekers up, whirlpools drag them down; the server sends the push since only the client moves the player.
                boolean down=prop.state().getValue(BubbleColumnBlock.DRAG_DOWN);target.clearFire();
                PropHuntMatch.impulse(target,new Vec3(target.getDeltaMovement().x,down?-.6:1.1,target.getDeltaMovement().z));
                p.level().playSound(null,target.blockPosition(),down?net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE:net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
                r.contact=now+10;
            }
            if(item.equals("minecraft:minecart")&&moving){target.hurt(p.damageSources().generic(),50);var away=target.position().subtract(prop.position()).normalize();PropHuntMatch.impulse(target,target.getDeltaMovement().add(away.x*1.4,.5,away.z*1.4));r.stun=now+20;r.stunPosition=p.position();r.contact=now+20;}
        }
    }
    public void struck(LivingEntity owner,PropEntity prop){
        if(!prop.item().equals("minecraft:dragon_egg"))return;
        for(int i=0;i<32;i++){var to=owner.position().add(owner.getRandom().nextInt(17)-8,owner.getRandom().nextInt(9)-4,owner.getRandom().nextInt(17)-8);if(to.distanceToSqr(owner.position())<=64&&owner.level().noCollision(owner,owner.getBoundingBox().move(to.subtract(owner.position())))){match.relocate(owner,to);break;}}
    }
    public void found(LivingEntity owner,PropEntity prop){
        if(owner.getUUID().equals(dayOwner))restoreDay();
        var state=states.get(owner.getUUID());if(state!=null){state.cloak=false;state.primary=false;modifier(owner,CLOAK,0);}
        if(prop==null||!prop.item().equals("minecraft:infested_stone"))return;
        var r=data(owner.getUUID());for(int i=0;i<3;i++){var mob=PropSummons.spawn(level(owner),PropSummonEntity.Kind.SILVERFISH,owner.getUUID(),prop.position().add((i-1)*.4,0,0),50,25,false);if(mob!=null)r.mobs.add(mob);}
    }
    /** Found infested-block minions keep attacking until review/reset. */
    public void orphanTick(){tickTemps();for(var entry:states.entrySet())if(match.prop(entry.getKey())==null){var r=entry.getValue();r.mobs.removeIf(Entity::isRemoved);for(var mob:r.mobs)PropSummons.tick(match,mob,mob.position(),0);}}
}
