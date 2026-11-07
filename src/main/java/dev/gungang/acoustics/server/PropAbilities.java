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
            CLOAK=UUID.fromString("c10a6028-bc47-4995-9a62-d91fdc065319"),LIGHT=UUID.fromString("119b6028-bc47-4995-9a62-d91fdc065319"),SPONGE=UUID.fromString("5b0e6028-bc47-4995-9a62-d91fdc065319"),
            ROLL=UUID.fromString("b1a1d028-bc47-4995-9a62-d91fdc065319");
    /** 248 billiard ball 1.8x, 252 Sanga skateboard 1.6x; 250 statue money rain; 247 jackpot; 245 dice roll. */
    static final double BALL_SPEED=.8,SKATE_SPEED=.6,RAIN_RADIUS=8,AUTO_SPEED=.2,AUTO_RANGE=28,PHOTO_RANGE=32;static final float AUTO_DAMAGE=3.5f;static final float RAIN_DAMAGE=80;static final int RAIN_TICKS=400,JACKPOT_TICKS=100,DICE_TICKS=20;
    /** 209 dry sponge speed bonus; 214 cartography table map time; 218/219 piston push speed. */
    /** 242 light 3x speed; 239 warhead blast radius; 152 lightning rod charge time and strike radius; 235 fountain jet height. */
    static final double LIGHT_SPEED=2,NUKE_RADIUS=64,ROD_RADIUS=8,FOUNTAIN_TALL=3;static final int ROD_TICKS=60*20,NUKE_CLOUD=80;
    static final String NUKE="gungang:nuke_warhead",AQUARIUM="gungang:aquarium",LIGHT_BLOCK="minecraft:light";
    static final double SPONGE_SPEED=.25,PISTON_PUSH=1.6,MONEY_SPEED=1.4;static final String CLOCK="gungang:desk_clock",MONEY_CART="gungang:money_cart";static final int SCOUT_TICKS=160;
    static final String EUNBI="gungang:eunbi_doll",HANBI="gungang:hanbi_doll";
    /** 184 beam: 5 s of use refilled over 10 s, 50 damage a second; 191 cloak: 8 s refilled over 16 s, 2.4× speed. */
    static final int BEAM_TICKS=100,BEAM_REFILL=200,CLOAK_TICKS=160,CLOAK_REFILL=320;
    static final float BEAM_DPS=50,TNT_DAMAGE=120;
    /** 139 end crystal: beams on every seeker within 24 blocks for 10 s, 16 damage a second each. */
    static final int CRYSTAL_TICKS=200;static final double CRYSTAL_RANGE=24;static final float CRYSTAL_DPS=16;
    static final double TNT_CART_RADIUS=4;
    static boolean doll(String item){return item.equals(EUNBI)||item.equals(HANBI);}
    /** Short-lived entities an ability leaves in the world: emoticon pop-ups and TNT about to go off. */
    private record Temp(Entity entity,int until,boolean tnt,UUID owner){}
    private final List<Temp> temps=new ArrayList<>();
    /** The hider whose daylight detector has turned the map's time, or null. */
    private UUID dayOwner;
    static final class Runtime {
        int cooldown,active,stun,born,contact,note,song,erupt,eruptReady,resonate=-1,cry,eyeStart,eyeTicks;
        /** 199 lightning storm until / next strike; 194 birthday song next note / position; 39 nozzle burst until. */
        int storm,stormNext,songAt,songNote,burst;boolean mayfly,flying,flight,bees,primary,cloak;
        /** Doll gauge in ticks of use left (-1: not a doll) and its maximum. */
        float gauge=-1,gaugeMax;
        Vec3 eyeFrom,eyeTo,eyeLand;
        Vec3 stunPosition,lastPosition;double flightY;
        final List<PropSummonEntity> mobs=new ArrayList<>();
        /** 214: the minimap shows until this tick; 219: teammates stuck to the sticky piston, held until {@code stuckUntil}. */
        int scoutUntil,stuckUntil;final List<UUID> stuck=new ArrayList<>();
        /** 232 capsule used; 239 warhead spent and when it went off; 152 since when the rod has been charging. */
        boolean gacha,nukeSpent;int nukeAt=-1,rodSince=-1;
        /** 139: seekers the crystal beams are on, until {@code crystalUntil}. */
        List<UUID> crystal=new ArrayList<>();int crystalUntil;
        final List<net.minecraft.world.entity.item.ItemEntity> discs=new ArrayList<>();
        /** 246 hanging from the ceiling at {@code hangAt}; 245 dice rolling until; 247 reels stopping at / jackpot until and its seekers; 250 rain until. */
        /** 255: vertical speed, next shot, stuck since / sidestep until and its side. */
        double autoVy;int autoShot,autoStuck,autoDetour,autoSide=1;Vec3 autoLast;
        boolean hang,jackpotWon;Vec3 hangAt;int diceUntil,slotAt=-1,jackpotUntil,rainUntil;List<UUID> jackpot=new ArrayList<>();
        final List<net.minecraft.world.entity.item.ItemEntity> rain=new ArrayList<>();final Map<UUID,Integer> rainHit=new HashMap<>();
        void clearDiscs(){discs.forEach(Entity::discard);discs.clear();}
    }
    private final PropHuntMatch match;
    private final Map<UUID,Runtime> states=new HashMap<>();
    PropAbilities(PropHuntMatch match){this.match=match;}
    private Runtime data(UUID id){return states.computeIfAbsent(id,k->{var r=new Runtime();r.born=match.now();return r;});}
    public static int cooldown(String item){return switch(item){case "minecraft:spawner"->84*20;
        case "minecraft:respawn_anchor"->30*20;case "minecraft:end_portal_frame","minecraft:tnt_minecart"->5*20;case "minecraft:crying_obsidian","gungang:prettiest_block"->3*20;case "minecraft:daylight_detector"->2*20;case "minecraft:command_block","minecraft:end_crystal","minecraft:tnt","gungang:gas_barrel"->30*20;case "minecraft:dispenser","minecraft:jukebox"->20;case "minecraft:brewing_stand"->50*20;case "minecraft:conduit"->90*20;case "minecraft:cartography_table"->30*20;case "minecraft:piston","minecraft:sticky_piston"->10;case "gungang:money_cart"->60;case "minecraft:bell"->15*20;case "gungang:garden_fountain"->BURST_COOLDOWN;case PartyBlocks.DICE_ID->30;case PartyBlocks.SLOT->200;case PartyBlocks.STATUE_ID->600;case "gungang:nozzle"->BURST_COOLDOWN;case "gungang:small_health_pack"->ExtraGadgetConfig.ticks(GadgetConfig.smallHealthPack.cooldownSeconds());case "gungang:large_health_pack"->ExtraGadgetConfig.ticks(GadgetConfig.largeHealthPack.cooldownSeconds());default->0;};}
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
        // Assorted types wear one of their looks at random (wood kinds, ores, walls ...); 215 farmland grows a random crop.
        if(PropCatalog.assorted(item))prop.state(PropCatalog.randomState(item,n->p.getRandom().nextInt(n)));
        if(item.equals("minecraft:farmland"))prop.mode(p.getRandom().nextInt(PropGeometry.CROPS));
        // 224: one random fish (cod, salmon, tropical fish, pufferfish or a small squid) in the tank.
        if(item.equals(AQUARIUM)&&prop.state().hasProperty(ToyBlocks.Aquarium.FISH))prop.state(prop.state().setValue(ToyBlocks.Aquarium.FISH,1+p.getRandom().nextInt(5)));
        if(item.equals("minecraft:armor_stand")){prop.text(armor(p.getRandom()));prop.mode(p.getRandom().nextInt(ARMOR_POSES));}
        if(doll(item)){r.gaugeMax=item.equals(EUNBI)?BEAM_TICKS:CLOAK_TICKS;r.gauge=r.gaugeMax;}
        // 255: wears a seeker's skin like the mannequin; it drives itself, so a held lock is let go.
        if(item.equals(PartyBlocks.AUTO)){match.unlock(p);var seekers=match.players().stream().filter(q->!match.hider(q.getUUID())).toList();prop.skin(seekers.isEmpty()?p.getUUID():seekers.get(p.getRandom().nextInt(seekers.size())).getUUID());}
        if(item.equals("gungang:mannequin")){var seekers=match.players().stream().filter(q->!match.hider(q.getUUID())).toList();prop.skin(seekers.isEmpty()?p.getUUID():seekers.get(p.getRandom().nextInt(seekers.size())).getUUID());}
        double maximum=maxHealth(item);float health=p.getHealth();boolean resized=p.getMaxHealth()!=maximum;
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);p.setHealth(resized?p.getMaxHealth():Math.min(health,p.getMaxHealth()));
        modifier(p,CART,item.equals("minecraft:minecart")||item.equals("minecraft:tnt_minecart")?1.5:item.equals(MONEY_CART)?MONEY_SPEED:0);
        modifier(p,SPONGE,item.equals("minecraft:sponge")?SPONGE_SPEED:0);
        modifier(p,LIGHT,item.equals(LIGHT_BLOCK)?LIGHT_SPEED:0);
        modifier(p,ROLL,item.equals(PartyBlocks.BALL)?BALL_SPEED:item.equals(PartyBlocks.SANGA)?SKATE_SPEED:0);
        // 248: a random ball to start with; 245: a random face up.
        if(item.equals(PartyBlocks.BALL)&&prop.state().hasProperty(PartyBlocks.Ball.BALL))prop.state(prop.state().setValue(PartyBlocks.Ball.BALL,p.getRandom().nextInt(PartyBlocks.Ball.KINDS)));
        if(item.equals(PartyBlocks.DICE_ID)&&prop.state().hasProperty(PartyBlocks.Dice.VALUE))prop.state(prop.state().setValue(PartyBlocks.Dice.VALUE,1+p.getRandom().nextInt(6)));
    }
    /** Sturdy disguises (tagged "견고"): mannequins and dolls, (crying) obsidian, end crystal, geyser, war table, armory 255; big grass block 400; bedrock 999; every other block 50. */
    public static double maxHealth(String item){
        return Set.of("gungang:mannequin",EUNBI,HANBI,"minecraft:obsidian","minecraft:crying_obsidian","minecraft:end_crystal","gungang:geyser","gungang:war_table","gungang:armory").contains(item)?255
                :Set.of("gungang:easel","gungang:garden_fountain",PartyBlocks.CHANDELIER_ID,PartyBlocks.WELL,PartyBlocks.TREE_ID,PartyBlocks.SANGA).contains(item)?255
                :item.equals(PartyBlocks.STATUE_ID)?999:item.equals(PartyBlocks.AUTO)?255:item.equals(NUKE)||item.equals("gungang:capsule_machine")?100
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
     * 155: each E press plays the next note of the chosen piece at its written pitch; R picks the next piece.
     * Pitches are semitones from the harp's own F#4 (C5 = 6). The wide-range gungang:prop_note plays six octaves
     * (F#1 to F#7, see SoundEngineMixin), so no piece is folded into the vanilla note block's two, and the disguise's
     * block state follows the widened note block (NoteRange's octave), so its state matches the note played.
     */
    record Song(String name,int[] notes){}
    static final List<Song> SONGS=List.of(
            // Mozart, Piano Sonata No. 16 (K. 545), first movement: the theme, then the rising-and-falling scales.
            new Song("모차르트 피아노 소나타 16번",notes("C5 E5 G5 B4 C5 D5 C5 A5 G5 C6 G5 F5 E5 F5 E5 "
                    +"A4 B4 C5 D5 E5 F5 G5 A5 G5 F5 E5 D5 C5 B4 A4 G4 A4 B4 C5 D5 E5 F5 G5 F5 E5 D5 C5 B4 A4 G4 "
                    +"F4 G4 A4 B4 C5 D5 E5 F5 E5 D5 C5 B4 A4 G4 F4 E4 F4 G4 A4 B4 C5 D5 E5 D5 C5 B4 A4 G4 F4 E4")),
            new Song("엘리제를 위하여",notes("E5 D#5 E5 D#5 E5 B4 D5 C5 A4 C4 E4 A4 B4 E4 G#4 B4 C5 E4 "
                    +"E5 D#5 E5 D#5 E5 B4 D5 C5 A4 C4 E4 A4 B4 E4 C5 B4 A4 "
                    +"B4 C5 D5 E5 G4 F5 E5 D5 F4 E5 D5 C5 E4 D5 C5 B4 E4 E5 E5 E6 D#5 E5 D#5 E5 D#5 E5 "
                    +"D#5 E5 D#5 E5 B4 D5 C5 A4 C4 E4 A4 B4 E4 C5 B4 A4")),
            // Pachelbel, Canon in D: the first violin's quarter-note and eighth-note lines.
            new Song("파헬벨 카논",notes("F#5 E5 D5 C#5 B4 A4 B4 C#5 D5 C#5 B4 A4 G4 F#4 G4 E4 "
                    +"D4 F#4 A4 G4 F#4 D4 F#4 E4 D4 B3 D4 A4 G4 B4 A4 G4 "
                    +"F#4 D4 E4 C#5 D5 F#5 A5 A4 B4 G4 A4 F#4 D4 D5 D5 C#5")),
            // Chopin, Etude Op. 25 No. 9 "Butterfly" in G-flat major: the opening octave figure.
            new Song("쇼팽 나비 에튀드",notes("Bb4 Bb5 Cb6 C6 Db5 Db6 Eb6 Db6 Gb4 Gb5 Ab5 A5 Bb4 Bb5 Cb6 Bb5 "
                    +"Eb4 Eb5 E5 F5 Gb4 Gb5 F5 Gb5 Ab4 Ab5 Bb5 Ab5 Ab4 Ab5 Bb5 Cb6")),
            // Beethoven, "Moonlight" Sonata, first movement: the bass and triplets of bars 1-4, then the melody.
            new Song("베토벤 월광 소나타",notes("C#3 G#3 C#4 E4 G#3 C#4 E4 G#3 C#4 E4 G#3 C#4 E4 "
                    +"B2 G#3 C#4 E4 G#3 C#4 E4 A2 A3 C#4 E4 F#2 A3 D4 F#4 "
                    +"G#2 G#3 C4 F#4 G#3 C#4 E4 G#3 C#4 D#4 F#3 C4 D#4 C#3 E3 G#3 C#4 "
                    +"G#4 G#4 G#4 G#4 G#4 G#4 A4 G#4 F#4 B4 E4 G#4 G#4 G#4 G#4 G#4 G#4 A4 G#4 F#4 B4 E4")),
            // Chopin, Etude Op. 10 No. 5 "Black Key": the right hand's opening black-key figure.
            new Song("쇼팽 흑건",notes("Gb6 Bb6 Db6 Gb6 Eb6 Gb6 Db6 Gb6 Bb5 Db6 Gb5 Bb5 "
                    +"Gb5 Bb5 Db5 Gb5 Eb5 Gb5 Db5 Gb5 Bb4 Db5 Gb4 Bb4 "
                    +"Db4 Db5 Ab4 Db5 Ab4 Ab5 Ab4 Ab5 Eb5 Ab5 Eb5 Eb6 "
                    +"Eb5 Eb6 Ab5 Eb6 Ab5 Ab6 Ab5 Ab6 Db6 Ab6 Db6 Db7")),
            new Song("작은 별",notes("C5 C5 G5 G5 A5 A5 G5 F5 F5 E5 E5 D5 D5 C5 G5 G5 F5 F5 E5 E5 D5 "
                    +"G5 G5 F5 F5 E5 E5 D5 C5 C5 G5 G5 A5 A5 G5 F5 F5 E5 E5 D5 D5 C5")));
    /** Space-separated note names (C4, F#5, Bb3, Cb6 ...) as semitones from F#4. */
    static int[] notes(String text){
        var parts=text.trim().split("\\s+");var out=new int[parts.length];
        for(int i=0;i<parts.length;i++){
            String n=parts[i];int pc="C D EF G A B".indexOf(n.charAt(0));int k=1;
            if(pc<0)throw new IllegalArgumentException(n);
            if(n.charAt(k)=='#'){pc++;k++;}else if(n.charAt(k)=='b'){pc--;k++;}
            out[i]=12*(Integer.parseInt(n.substring(k))+1)+pc-66;
        }
        return out;
    }
    private static void modifier(LivingEntity p,UUID id,double value){if(p instanceof Mob&&value!=0)value=Math.sqrt(1+value)-1; // path-walking bots move attribute²
        var attr=p.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;var old=attr.getModifier(id);if(old!=null&&old.getAmount()==value)return;if(old!=null)attr.removeModifier(id);if(value!=0)attr.addTransientModifier(new AttributeModifier(id,"prop_ability",value,AttributeModifier.Operation.MULTIPLY_TOTAL));}
    public void remove(LivingEntity p){
        var r=states.remove(p.getUUID());if(r!=null){r.mobs.forEach(Entity::discard);r.clearDiscs();stopFlight(p,r);unhang(p,r);}
        modifier(p,ROLL,0);modifier(p,CART,0);modifier(p,BEACON,0);modifier(p,CLOAK,0);modifier(p,SPONGE,0);modifier(p,LIGHT,0);
        // A new block, leaving the match or being found puts the map's time back.
        if(p.getUUID().equals(dayOwner))restoreDay();
    }
    public void clear(){for(var p:match.bodies())remove(p);states.values().forEach(r->{r.mobs.forEach(Entity::discard);r.clearDiscs();});states.clear();clearTemps();restoreDay();}
    public void resetTransient(){for(var p:match.bodies()){var r=states.get(p.getUUID());if(r!=null){r.mobs.forEach(Entity::discard);r.mobs.clear();r.crystal.clear();r.crystalUntil=0;var prop=match.prop(p.getUUID());if(prop!=null&&(prop.item().equals("minecraft:end_crystal")||prop.item().equals(EUNBI)))prop.text("");r.clearDiscs();r.active=0;r.stun=0;r.erupt=0;r.eyeLand=null;r.cry=0;r.storm=0;r.burst=0;r.primary=false;r.cloak=false;modifier(p,CLOAK,0);stopFlight(p,r);unhang(p,r);r.rainUntil=0;r.rain.clear();r.jackpotUntil=0;r.jackpot.clear();r.slotAt=-1;r.diceUntil=0;}}clearTemps();}
    private void clearTemps(){temps.forEach(t->t.entity().discard());temps.clear();}
    private void restoreDay(){if(dayOwner!=null){dayOwner=null;MapEnvironment.override(null);}}
    private static void stopFlight(LivingEntity p,Runtime r){if(!r.flight)return;r.flight=false;p.setNoGravity(false);p.fallDistance=0;if(p instanceof ServerPlayer sp){sp.getAbilities().mayfly=r.mayfly;sp.getAbilities().flying=r.flying;sp.onUpdateAbilities();}}
    private static void unhang(LivingEntity p,Runtime r){if(!r.hang)return;r.hang=false;r.hangAt=null;p.setNoGravity(false);p.fallDistance=0;}
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
            // 112 sign and 230 hanging sign: a random line of text.
            case "minecraft:oak_sign","minecraft:oak_hanging_sign"->{
                var all=match.bodies().stream().filter(q->match.hider(q.getUUID())).toList();var teammate=all.isEmpty()?p:all.get(p.getRandom().nextInt(all.size()));
                var pos=match.prop(teammate.getUUID())==null?teammate.blockPosition():match.prop(teammate.getUUID()).blockPosition();
                var labels=List.of(match.name(p.getUUID()),"(●´꒳`●)","(｡>﹏<｡)","(❀╹◡╹)","凸(｀0´)凸","٩( ᐛ )و","Σ(￣□￣;)",pos.getX()+", "+pos.getY()+", "+pos.getZ());prop.text(labels.get(p.getRandom().nextInt(labels.size())));
            }
            case "minecraft:infested_stone"->{
                if((match.hunting()||match.hiding())&&match.seekers().isEmpty()&&r.mobs.stream().noneMatch(m->m.isAlive()&&!m.isRemoved()))found(p,prop);
            }
            case "gungang:mannequin",EUNBI,HANBI,PartyBlocks.SANGA->prop.mode((prop.mode()+1)%6);
            // 245: the die tumbles for a second (a little hop), then its top face pops up as text.
            case PartyBlocks.DICE_ID->{
                r.diceUntil=now+DICE_TICKS;r.cooldown=now+cooldown(item);
                if(p.onGround())PropHuntMatch.impulse(p,new Vec3(p.getDeltaMovement().x,.32,p.getDeltaMovement().z));
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BONE_BLOCK_HIT,net.minecraft.sounds.SoundSource.BLOCKS,1,1.5f);
            }
            // 246: up to the nearest ceiling within 12 blocks and held there; E again lets go.
            case PartyBlocks.CHANDELIER_ID->{
                if(r.hang){unhang(p,r);PropHuntMatch.say(p,Component.literal("샹들리에: 떨어집니다"),true);return true;}
                var box=p.getBoundingBox();var from=new Vec3(p.getX(),box.maxY+.01,p.getZ());
                var hit=p.level().clip(new ClipContext(from,from.add(0,12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(hit.getType()==HitResult.Type.MISS){PropHuntMatch.say(p,Component.literal("샹들리에: 12블록 안에 천장이 없습니다"),true);return true;}
                var at=new Vec3(p.getX(),hit.getLocation().y-p.getBbHeight()-.001,p.getZ());
                if(!p.level().noCollision(p,box.move(at.subtract(p.position())))){PropHuntMatch.say(p,Component.literal("샹들리에: 매달릴 자리가 없습니다"),true);return true;}
                match.unlock(p);r.hang=true;r.hangAt=at;p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);pin(p,at.x,at.y,at.z);prop.setPos(at);
                p.level().playSound(null,BlockPos.containing(at),net.minecraft.sounds.SoundEvents.CHAIN_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1,.8f);
                PropHuntMatch.say(p,Component.literal("샹들리에: 천장에 매달렸습니다 (E: 떨어지기)"),true);
            }
            // 247: three reels; 777 (1 in 64) is the jackpot, paid out when they stop (tick).
            case PartyBlocks.SLOT->{
                if(r.slotAt>now)return true;
                var random=p.getRandom();int a=PartyBlocks.reel(random),b=PartyBlocks.reel(random),c=PartyBlocks.reel(random);
                prop.text(a+","+b+","+c+","+p.level().getGameTime());r.slotAt=now+PartyBlocks.SPIN_TICKS;r.jackpotWon=a==0&&b==0&&c==0;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.LEVER_CLICK,net.minecraft.sounds.SoundSource.BLOCKS,1,.7f);
            }
            // 248: another ball.
            case PartyBlocks.BALL->{if(prop.state().hasProperty(PartyBlocks.Ball.BALL)){
                int next=(prop.state().getValue(PartyBlocks.Ball.BALL)+1+p.getRandom().nextInt(PartyBlocks.Ball.KINDS-1))%PartyBlocks.Ball.KINDS;
                prop.state(prop.state().setValue(PartyBlocks.Ball.BALL,next));
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,net.minecraft.sounds.SoundSource.BLOCKS,1,1.9f);
                PropHuntMatch.say(p,Component.literal("당구공: "+PartyBlocks.Ball.name(next)),true);}}
            // 249: the bucket goes down the well or comes back up.
            case PartyBlocks.WELL->{prop.toggle();p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.CHAIN_PLACE,net.minecraft.sounds.SoundSource.BLOCKS,1,prop.open()?.7f:1f);}
            // 250: twenty seconds of money raining around the statue (80 to each seeker it hits, while hunting).
            case PartyBlocks.STATUE_ID->{
                r.rainUntil=now+RAIN_TICKS;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),DeskBlocks.CASH.get(),net.minecraft.sounds.SoundSource.BLOCKS,3,.8f);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,net.minecraft.sounds.SoundSource.BLOCKS,2,.6f);
                PropHuntMatch.say(p,Component.literal("명예횡령상: 돈벼락 20초"),true);
            }
            // 251: the fairy lights on the disguise off and on (a placed tree always shines).
            case PartyBlocks.TREE_ID->{prop.toggle();p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,net.minecraft.sounds.SoundSource.BLOCKS,.8f,prop.open()?.9f:1.4f);
                PropHuntMatch.say(p,Component.literal(prop.open()?"크리스마스 트리: 장식등 꺼짐":"크리스마스 트리: 장식등 켜짐"),true);}
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
            // 214: the map with every seeker and teammate on it, for a few seconds.
            case "minecraft:cartography_table"->{
                r.scoutUntil=now+SCOUT_TICKS;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
                PropHuntMatch.say(p,Component.literal("지도 제작대: "+SCOUT_TICKS/20+"초 동안 술래·팀원 위치 표시"),true);
            }
            // 215: E plants the next crop.
            case "minecraft:farmland"->{prop.mode((prop.mode()+1)%PropGeometry.CROPS);p.refreshDimensions();
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.CROP_PLANTED,net.minecraft.sounds.SoundSource.BLOCKS,1,1);}
            case "minecraft:piston","minecraft:sticky_piston"->{piston(p,prop,r,now);r.cooldown=now+cooldown(item);}
            // 221: E sets or clears the alarm (it beeps while set, see tick).
            case CLOCK->{if(prop.state().hasProperty(DeskBlocks.Clock.ALARM)){prop.state(prop.state().cycle(DeskBlocks.Clock.ALARM));
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,net.minecraft.sounds.SoundSource.BLOCKS,.5f,1.6f);
                PropHuntMatch.say(p,Component.literal(prop.state().getValue(DeskBlocks.Clock.ALARM)?"탁상 시계: 알람 켜짐":"탁상 시계: 알람 꺼짐"),true);}}
            // 222: the cash machine sound.
            case MONEY_CART->{r.cooldown=now+cooldown(item);p.level().playSound(null,prop.blockPosition(),DeskBlocks.CASH.get(),net.minecraft.sounds.SoundSource.BLOCKS,1.2f,1);}
            case "minecraft:end_crystal"->{
                // 139: locks onto every seeker within 24 blocks, walls or not, and burns them with crystal beams for 10 s.
                var from=prop.position().add(0,1,0);
                var targets=match.seekers().stream().filter(q->q.level()==prop.level()&&q.isAlive()&&q.distanceToSqr(from)<=CRYSTAL_RANGE*CRYSTAL_RANGE).map(Entity::getUUID).toList();
                if(targets.isEmpty()){PropHuntMatch.say(p,Component.literal("엔드 수정: 24블록 안에 술래가 없습니다"),true);return true;}
                r.crystal=new ArrayList<>(targets);r.crystalUntil=now+CRYSTAL_TICKS;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,net.minecraft.sounds.SoundSource.PLAYERS,1.2f,1.4f);
                level(p).sendParticles(ParticleTypes.DRAGON_BREATH,from.x,from.y,from.z,20,.2,.2,.2,.02);
                PropHuntMatch.say(p,Component.literal("엔드 수정: 술래 "+targets.size()+"명 공격 중"),true);
            }
            case "minecraft:sculk_sensor"->{prop.toggle();prop.state(prop.state().setValue(SculkSensorBlock.PHASE,prop.open()?SculkSensorPhase.ACTIVE:SculkSensorPhase.INACTIVE));if(prop.open())scream(level(p),prop);}
            case "minecraft:sculk_shrieker"->{prop.toggle();prop.state(prop.state().setValue(SculkShriekerBlock.SHRIEKING,prop.open()));if(prop.open())scream(level(p),prop);}
            case "gungang:hologram_projector"->{int size=(prop.mode()/5+1)%HOLOGRAM_SIZES.length;prop.mode(size*5+hologramSpeed(prop.mode()));PropHuntMatch.tell(p,"홀로그램 크기 "+HOLOGRAM_SIZES[size]+"%");}
            case "gungang:shutter"->PropInteraction.shutter(prop);
            case "minecraft:sea_pickle"->prop.state(prop.state().cycle(SeaPickleBlock.PICKLES));
            case "minecraft:turtle_egg"->prop.state(prop.state().cycle(TurtleEggBlock.EGGS));
            case "minecraft:note_block"->{
                var song=SONGS.get(Math.floorMod(r.song,SONGS.size()));
                int note=song.notes()[r.note%song.notes().length];r.note=(r.note+1)%song.notes().length;
                // The disguise's state takes the written note over the widened six octaves (NoteRange: semitone 0 is the
                // harp's F#3, the song's 0 is F#4); the particle colour follows the note within its octave, as vanilla.
                var tuned=prop.state().hasProperty(NoteRange.OCTAVE)?NoteRange.withSemitone(prop.state(),note+12)
                        :prop.state().setValue(BlockStateProperties.NOTE,net.minecraft.util.Mth.clamp(note+12,0,24));
                prop.state(tuned);int shown=tuned.getValue(BlockStateProperties.NOTE);
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
                // 15 s cooldown between rings.
                PropInteraction.ring(prop);if(r.resonate<0)r.resonate=now+60;r.cooldown=now+cooldown(item);
            }
            // 199: a lightning storm over seekers within 24 blocks for four seconds.
            case "minecraft:conduit"->{
                if(nearest(level(p),prop.position(),STORM_RANGE)==null){PropHuntMatch.say(p,Component.literal("전달체: 24블록 안에 술래가 없습니다"),true);return true;}
                r.storm=now+STORM_TICKS;r.stormNext=now;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.CONDUIT_ACTIVATE,net.minecraft.sounds.SoundSource.BLOCKS,2,1);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER,net.minecraft.sounds.SoundSource.WEATHER,3,1);
            }
            // 231: takes on the look of the blocks around it (no cooldown).
            case "gungang:chameleon_block"->chameleon(p,prop);
            // 232: once per disguise, every other hiding teammate gets a new random block.
            case "gungang:capsule_machine"->{
                if(r.gacha){PropHuntMatch.say(p,Component.literal("캡슐 뽑기: 이미 사용했습니다"),true);return true;}
                int changed=match.reroll(p);
                if(changed==0){PropHuntMatch.say(p,Component.literal("캡슐 뽑기: 블록을 바꿀 팀원이 없습니다"),true);return true;}
                r.gacha=true;
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.ITEM_PICKUP,net.minecraft.sounds.SoundSource.BLOCKS,1,.6f);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1.4f);
                PropHuntMatch.say(p,Component.literal("캡슐 뽑기: 팀원 "+changed+"명의 블록이 바뀌었습니다"),true);
            }
            // 242: E steps the brightness 15 → 12 → 9 → 6 → 3 → 0 → 15.
            case LIGHT_BLOCK->{if(prop.state().hasProperty(LightBlock.LEVEL)){int level=prop.state().getValue(LightBlock.LEVEL);int next=level==0?15:Math.max(0,level-3);
                prop.state(prop.state().setValue(LightBlock.LEVEL,next));PropHuntMatch.say(p,Component.literal("빛 밝기 "+next),true);}}
            // 235: the fountain's jet, like the nozzle's burst.
            case "gungang:garden_fountain"->{
                r.burst=now+BURST_TICKS;r.cooldown=now+cooldown(item);
                p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,net.minecraft.sounds.SoundSource.BLOCKS,1.5f,.9f);
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
    /** R on a hologram projector steps the spin speed, and on a note block picks the next piece, instead of turning the block. */
    public boolean rotate(LivingEntity p,PropEntity prop){
        if(prop.item().equals("minecraft:note_block")){
            if(p instanceof net.minecraft.world.entity.Mob)return false; // bots keep turning it
            var r=data(p.getUUID());r.song=(r.song+1)%SONGS.size();r.note=0;
            PropHuntMatch.say(p,Component.literal("♪ "+SONGS.get(r.song).name()+" ("+(r.song+1)+"/"+SONGS.size()+")"),true);return true;
        }
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
        if(target!=null&&now%2==0){target.invulnerableTime=0;match.credit(target,p.getUUID());target.hurt(p.damageSources().magic(),BEAM_DPS/10);}
        // The beam itself is drawn by clients (PropObjectRenderer.laser): start and end relative to the doll, in TEXT.
        var a=from.subtract(prop.position());var b=at.subtract(prop.position());
        prop.text(String.format(java.util.Locale.ROOT,"%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",a.x,a.y,a.z,b.x,b.y,b.z));
        if(target!=null)level.sendParticles(ParticleTypes.END_ROD,at.x,at.y,at.z,3,.1,.1,.1,.02);
        else if(hit.getType()!=HitResult.Type.MISS&&now%2==0)level.sendParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,2,.05,.05,.05,.15);
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
        for(var seeker:targets)if(seeker.position().distanceToSqr(at)<=STRIKE_RADIUS*STRIKE_RADIUS){seeker.invulnerableTime=0;match.credit(seeker,p.getUUID());seeker.hurt(p.damageSources().magic(),STRIKE_DAMAGE);}
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
    /**
     * 139: every 2 ticks each locked seeker still alive and within range takes its share of 16 damage a second. The
     * crystal's text carries their entity ids so clients draw the vanilla crystal beam to each of them.
     */
    private void crystal(LivingEntity p,PropEntity prop,Runtime r,int now){
        if(!prop.item().equals("minecraft:end_crystal"))return;
        var from=prop.position().add(0,1,0);var ids=new StringBuilder();
        if(match.hunting()&&now<r.crystalUntil){
            for(var id:r.crystal){
                var target=match.seekers().stream().filter(q->q.getUUID().equals(id)&&q.level()==prop.level()&&q.isAlive()).findFirst().orElse(null);
                if(target==null||target.distanceToSqr(from)>CRYSTAL_RANGE*CRYSTAL_RANGE)continue;
                if(!ids.isEmpty())ids.append(',');ids.append(target.getId());
                if(now%2==0){target.invulnerableTime=0;match.credit(target,p.getUUID());target.hurt(p.damageSources().magic(),CRYSTAL_DPS/10);}
            }
            if(now%20==0)p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,net.minecraft.sounds.SoundSource.PLAYERS,1,1.6f);
        }else r.crystal.clear();
        if(!prop.text().contentEquals(ids))prop.text(ids.toString());
    }
    /** 222: the cart turns to its hider's heading and, while it moves, spills cash nobody can pick up (gone in 3 s). */
    private void moneyCart(LivingEntity p,PropEntity prop,Runtime r,boolean moving){
        if(!match.locked(p.getUUID())&&prop.state().hasProperty(HorizontalDirectionalBlock.FACING)){
            var facing=net.minecraft.core.Direction.fromYRot(p.getYRot());
            if(prop.state().getValue(HorizontalDirectionalBlock.FACING)!=facing)prop.state(prop.state().setValue(HorizontalDirectionalBlock.FACING,facing));
        }
        if(!moving||p.tickCount%5!=0)return;
        var random=p.getRandom();var money=random.nextInt(3)==0?net.minecraft.world.item.Items.GOLD_NUGGET:net.minecraft.world.item.Items.EMERALD;
        var drop=new net.minecraft.world.entity.item.ItemEntity(p.level(),prop.getX(),prop.getY()+.8,prop.getZ(),new net.minecraft.world.item.ItemStack(money),(random.nextDouble()-.5)*.15,.2,(random.nextDouble()-.5)*.15);
        drop.setNeverPickUp();p.level().addFreshEntity(drop);r.discs.add(drop);
    }
    /** 242: as light the hider shakes off every harmful effect and never burns. */
    private static void lightBody(LivingEntity p){
        for(var effect:List.copyOf(p.getActiveEffects()))if(effect.getEffect().getCategory()==net.minecraft.world.effect.MobEffectCategory.HARMFUL)p.removeEffect(effect.getEffect());
        if(p.isOnFire())p.clearFire();
    }
    /**
     * 152: a lightning rod left charged (E) for a minute during the hunt calls down lightning on itself: every seeker
     * within 8 blocks dies, and the rod goes quiet until it is charged again.
     */
    private void rod(LivingEntity p,PropEntity prop,Runtime r,int now){
        var powered=BlockStateProperties.POWERED;
        if(!match.hunting()||!prop.state().hasProperty(powered)||!prop.state().getValue(powered)){r.rodSince=-1;return;}
        if(r.rodSince<0)r.rodSince=now;
        int held=now-r.rodSince;
        if(held==ROD_TICKS-200)PropHuntMatch.say(p,Component.literal("피뢰침: 10초 뒤 벼락이 칩니다"),true);
        if(held<ROD_TICKS)return;
        var level=level(p);var at=prop.position();
        var bolt=EntityType.LIGHTNING_BOLT.create(level);if(bolt!=null){bolt.moveTo(at.x,at.y+1,at.z);bolt.setVisualOnly(true);level.addFreshEntity(bolt);}
        for(var seeker:match.seekers())if(seeker.level()==level&&seeker.position().distanceToSqr(at)<=ROD_RADIUS*ROD_RADIUS)match.eliminate(seeker,p);
        prop.state(prop.state().setValue(powered,false));r.rodSince=-1;
    }
    /**
     * 239: the warhead goes off at the first hit: a huge blast and every seeker within 64 blocks dies. It only goes off
     * once per disguise; the hider keeps the disguise.
     */
    public void detonate(LivingEntity p,PropEntity prop){
        var r=data(p.getUUID());if(r.nukeSpent||!match.hunting())return;
        r.nukeSpent=true;r.nukeAt=match.now();
        var level=level(p);var at=prop.position().add(0,1.5,0);
        level.sendParticles(ParticleTypes.FLASH,at.x,at.y,at.z,6,1,1,1,0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,at.x,at.y,at.z,40,7,4,7,0);
        level.sendParticles(ParticleTypes.EXPLOSION,at.x,at.y,at.z,120,12,6,12,0);
        level.sendParticles(ParticleTypes.LAVA,at.x,at.y,at.z,80,4,2,4,.6);
        for(var sound:List.of(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,net.minecraft.sounds.SoundEvents.DRAGON_FIREBALL_EXPLODE))
            level.playSound(null,at.x,at.y,at.z,sound,net.minecraft.sounds.SoundSource.BLOCKS,30,.5f);
        for(var seeker:match.seekers())if(seeker.level()==level&&seeker.position().distanceToSqr(at)<=NUKE_RADIUS*NUKE_RADIUS)match.eliminate(seeker,p);
        PropHuntMatch.say(p,Component.literal("핵탄두 폭발!"),true);
    }
    /** 239: the mushroom cloud rising over the warhead for four seconds after the blast. */
    private static void mushroom(LivingEntity p,PropEntity prop,int age){
        var level=level(p);double x=prop.getX(),z=prop.getZ(),top=prop.getY()+3+age*.25;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,x,prop.getY()+2+age*.12,z,4,.6,.4,.6,.02);
        if(age%2==0){
            for(int i=0;i<16;i++){double a=i*Math.PI/8,rad=2.5+age*.06;level.sendParticles(ParticleTypes.LARGE_SMOKE,x+Math.cos(a)*rad,top,z+Math.sin(a)*rad,1,.3,.3,.3,.01);}
            level.sendParticles(ParticleTypes.FLAME,x,top,z,10,1.5,.5,1.5,.02);
        }
    }
    /**
     * 231: one of the blocks touching the disguise becomes its look, a different one each press where possible. Touching
     * is measured against the disguise's real box, so a prop standing on a slab or a modded half block, or pressed
     * against a thin wall panel, still finds it; modded blocks with a block entity or a waterlogged state count too.
     */
    private static void chameleon(LivingEntity p,PropEntity prop){
        var level=p.level();var touching=new ArrayList<net.minecraft.world.level.block.state.BlockState>();
        var box=prop.getBoundingBox().inflate(.05);
        for(var pos:net.minecraft.core.BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX),net.minecraft.util.Mth.floor(box.minY),net.minecraft.util.Mth.floor(box.minZ),
                net.minecraft.util.Mth.floor(box.maxX),net.minecraft.util.Mth.floor(box.maxY),net.minecraft.util.Mth.floor(box.maxZ))){
            var s=chameleonLook(level,pos.immutable(),prop.getBoundingBox());
            if(s!=null&&!touching.contains(s))touching.add(s);
        }
        if(touching.size()>1)touching.remove(prop.state());
        var best=touching.isEmpty()?null:touching.get(p.getRandom().nextInt(touching.size()));
        if(best==null){PropHuntMatch.say(p,Component.literal("카멜레온: 맞닿은 블록이 없습니다"),true);return;}
        prop.state(best);p.refreshDimensions();
        level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1.4f);
        PropHuntMatch.say(p,Component.literal("카멜레온: ").append(best.getBlock().getName()),true);
    }
    /**
     * The look a block lends the chameleon, or null: a single-cell block with something to stand on or bump into that
     * meets one of the disguise's faces (an edge or a corner alone does not count). Its model or its block entity's renderer draws it; a waterlogged block comes dry.
     * Liquids, double blocks, beds and the mod's own multi-cell pieces are left out.
     */
    private static net.minecraft.world.level.block.state.BlockState chameleonLook(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.AABB box){
        if(!level.isLoaded(pos))return null;
        var s=level.getBlockState(pos);var block=s.getBlock();
        if(s.isAir()||block instanceof net.minecraft.world.level.block.LiquidBlock||block instanceof net.minecraft.world.level.block.BedBlock
                ||block instanceof FurnitureBlock||block instanceof MultiPartBlock||s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF))return null;
        var shape=s.getRenderShape();
        if(shape!=net.minecraft.world.level.block.RenderShape.MODEL&&!(block instanceof EntityBlock))return null;
        net.minecraft.world.phys.shapes.VoxelShape collision;
        try{collision=s.getCollisionShape(level,pos);}catch(RuntimeException error){return null;}
        // Grown out through one face at a time and pulled in a hair on the others, so a diagonal neighbour never touches.
        double reach=.05,in=-1e-3;
        var faces=java.util.List.of(box.inflate(reach,in,in),box.inflate(in,reach,in),box.inflate(in,in,reach));
        if(collision.isEmpty()||collision.toAabbs().stream().map(part->part.move(pos)).noneMatch(part->faces.stream().anyMatch(part::intersects)))return null;
        var waterlogged=net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        if(s.hasProperty(waterlogged))s=s.setValue(waterlogged,false);
        return s.getFluidState().isEmpty()?s:null;
    }
    /** 242: the mouse wheel steps the light's brightness by one (0..15). */
    public void brightness(LivingEntity p,PropEntity prop,int delta){
        if(!prop.item().equals(LIGHT_BLOCK)||!prop.state().hasProperty(LightBlock.LEVEL))return;
        int level=prop.state().getValue(LightBlock.LEVEL),next=Math.max(0,Math.min(15,level+delta));if(next==level)return;
        prop.state(prop.state().setValue(LightBlock.LEVEL,next));PropHuntMatch.say(p,Component.literal("빛 밝기 "+next),true);
    }
    /** 209: a dry sponge runs 1.25x; once it touches water, a puddle or any liquid it turns wet for good and slows to normal. */
    private void sponge(LivingEntity p,PropEntity prop){
        boolean dry=prop.state().is(Blocks.SPONGE);
        if(dry&&soaked(p,prop)){
            dry=false;prop.state(Blocks.WET_SPONGE.defaultBlockState());
            p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BUCKET_FILL,net.minecraft.sounds.SoundSource.BLOCKS,1,1.2f);
            level(p).sendParticles(ParticleTypes.SPLASH,prop.getX(),prop.getY()+1,prop.getZ(),20,.3,.2,.3,.1);
        }
        modifier(p,SPONGE,dry?SPONGE_SPEED:0);
    }
    private static boolean soaked(LivingEntity p,PropEntity prop){
        if(p.isInWater()||p.isInLava()||p.isInFluidType())return true;
        var level=p.level();var pos=BlockPos.containing(prop.getX(),prop.getY()+.1,prop.getZ());
        return level.getBlockState(pos).getBlock() instanceof PuddleBlock||!level.getFluidState(pos).isEmpty();
    }
    /**
     * 218/219: E fires the piston the way it faces (R turns it through all six directions; never moving map blocks): seekers and teammates in the two
     * cells ahead are shoved away. A sticky piston instead grabs the teammates there: they ride its head and are pulled
     * back with it when it retracts (E again).
     */
    private void piston(LivingEntity p,PropEntity prop,Runtime r,int now){
        var state=prop.state();if(!(state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock))return;
        boolean sticky=prop.item().equals("minecraft:sticky_piston");var EXTENDED=net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED;
        if(state.getValue(EXTENDED)){
            prop.state(state.setValue(EXTENDED,false));p.refreshDimensions();
            p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.PISTON_CONTRACT,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1);
            r.stuckUntil=now+10; // pulled in next to the base, then let go
            return;
        }
        var facing=state.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING);
        prop.state(state.setValue(EXTENDED,true));p.refreshDimensions();
        p.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.PISTON_EXTEND,net.minecraft.sounds.SoundSource.BLOCKS,.8f,1);
        var step=new Vec3(facing.getStepX(),facing.getStepY(),facing.getStepZ());
        var cell=new net.minecraft.world.phys.AABB(prop.getX()-.5,prop.getY(),prop.getZ()-.5,prop.getX()+.5,prop.getY()+1,prop.getZ()+.5).move(step);
        var reach=cell.expandTowards(step).inflate(.05);
        var seekers=match.seekers();r.stuck.clear();
        for(var body:match.bodies()){
            if(body==p||body.level()!=p.level())continue;
            var disguise=match.prop(body.getUUID());boolean hider=match.hider(body.getUUID());
            if(hider?disguise==null:!seekers.contains(body))continue;
            if(!(hider?disguise.getBoundingBox():body.getBoundingBox()).intersects(reach))continue;
            if(hider)match.unlock(body);
            if(sticky&&hider){r.stuck.add(body.getUUID());continue;}
            PropHuntMatch.impulse(body,step.scale(PISTON_PUSH).add(0,facing.getAxis().isHorizontal()?.35:0,0));
        }
        r.stuckUntil=Integer.MAX_VALUE;
    }
    /** 219: holds the stuck teammates against the sticky piston's head (two cells out, or one once retracted). */
    private void stuck(PropEntity prop,Runtime r,int now){
        var state=prop.state();
        if(now>r.stuckUntil||!(state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock)){r.stuck.clear();return;}
        var facing=state.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING);
        int reach=state.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED)?2:1;
        var anchor=prop.position().add(facing.getStepX()*reach,facing.getStepY()*reach,facing.getStepZ()*reach);
        r.stuck.removeIf(id->{
            var body=match.bodies().stream().filter(q->q.getUUID().equals(id)).findFirst().orElse(null);
            if(body==null||match.prop(id)==null||body.position().distanceTo(anchor)>4)return true;
            PropHuntMatch.impulse(body,anchor.subtract(body.position()).scale(.5));body.fallDistance=0;return false;
        });
    }
    /** 214: what the cartography table's map shows its hider, or null when it is not open. */
    public PropHuntSnapshot.Scout scout(UUID id){
        var r=states.get(id);int now=match.now();if(r==null||r.scoutUntil<=now)return null;
        var marks=new ArrayList<BombMatch.Mate>();
        for(var body:match.bodies()){
            var disguise=match.prop(body.getUUID());boolean hider=match.hider(body.getUUID());
            // Seekers still held in their waiting room (hiding time) are shown too.
            if(hider?disguise==null:!body.isAlive()||body.isSpectator())continue;
            var at=hider?disguise.position():body.position();
            marks.add(new BombMatch.Mate(hider?"ALPHA":"BRAVO",body.getUUID().toString(),at.x,at.z,body.getYRot()));
        }
        // The block list's test session has no map: no preview image, the minimap just frames everyone.
        var map=match.map();
        return new PropHuntSnapshot.Scout(map==null?"":map.id(),map==null?null:map.previewArea(),marks,(r.scoutUntil-now+19)/20);
    }
    /** 39: the burst stream throws seekers and other hiders' disguises standing in it into the air. */
    private void burst(LivingEntity p,PropEntity prop,Runtime r,int now){
        if(now>=r.burst)return;
        var level=level(p);var pos=prop.blockPosition();net.minecraft.world.phys.AABB column;
        if(prop.state().getBlock() instanceof NozzleBlock nozzle){
            nozzle.particles(level,pos,BURST_TALL,4);
            if(now%2==0)level.sendParticles(nozzle.acid()?NozzleBlock.ACID_SPRAY:ParticleTypes.SPLASH,pos.getX()+.5,pos.getY()+1.2,pos.getZ()+.5,12,.15,.4,.15,.2);
            column=nozzle.column(pos,BURST_TALL);
        }else if(prop.state().getBlock() instanceof ToyBlocks.Fountain){
            // 235: a jet out of the top bowl, falling back around the basin. The 2x2 fountain's centre is the
            // anchor cell's south-east corner, half a block from the prop's position.
            double x=prop.getX()+.5,y=prop.getY()+1.75,z=prop.getZ()+.5;
            for(double h=0;h<FOUNTAIN_TALL;h+=.35)level.sendParticles(ParticleTypes.SPLASH,x,y+h,z,3,.08,.1,.08,.05);
            if(now%2==0)level.sendParticles(ParticleTypes.FALLING_WATER,x,y+FOUNTAIN_TALL,z,16,.5,.2,.5,0);
            column=new net.minecraft.world.phys.AABB(x-.4,y,z-.4,x+.4,y+FOUNTAIN_TALL,z+.4);
        }else return;
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
        if(r.hang&&r.hangAt!=null){p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;if(p.position().distanceToSqr(r.hangAt)>1e-4){pin(p,r.hangAt.x,r.hangAt.y,r.hangAt.z);prop.setPos(r.hangAt);}}
        party(p,prop,r,now);
        if(item.equals(PartyBlocks.AUTO))auto(p,prop,r,now);
        if(item.equals(PartyBlocks.PHOTO_ID))photo(p,prop,now);
        if(r.stun>now){p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;if(r.stunPosition!=null)pin(p,r.stunPosition.x,r.stunPosition.y,r.stunPosition.z);}
        boolean beacon=match.bodies().stream().map(q->match.prop(q.getUUID())).anyMatch(other->other!=null&&other.item().equals("minecraft:beacon")&&other.open());
        modifier(p,BEACON,beacon?.2:0);
        if(item.equals("minecraft:sponge"))sponge(p,prop);
        if(item.equals(LIGHT_BLOCK))lightBody(p);
        if(item.equals("minecraft:lightning_rod"))rod(p,prop,r,now);
        if(r.nukeAt>=0&&now-r.nukeAt<NUKE_CLOUD)mushroom(p,prop,now-r.nukeAt);
        if(item.equals(CLOCK)&&prop.state().hasProperty(DeskBlocks.Clock.ALARM)&&prop.state().getValue(DeskBlocks.Clock.ALARM)&&(now-r.born)%DeskBlocks.ALARM_PERIOD==0)
            DeskBlocks.beep(p.level(),prop.getX(),prop.getY()+.3,prop.getZ());
        if(item.equals(MONEY_CART))moneyCart(p,prop,r,moving);
        if(!r.stuck.isEmpty())stuck(prop,r,now);
        if(item.equals("minecraft:sugar_cane")){int height=Math.min(3,1+(now-r.born)/1200);if(prop.height()!=height){prop.height(height);p.refreshDimensions();}}
        if(item.equals("minecraft:oak_sign")&&!match.locked(p.getUUID()))prop.state(prop.state().setValue(StandingSignBlock.ROTATION,Math.floorMod(Math.round(p.getYRot()*16/360),16)));
        if(Set.of("minecraft:minecart",MONEY_CART,"gungang:mannequin","gungang:dried_ghast",EUNBI,HANBI,"minecraft:armor_stand","minecraft:tnt_minecart",PartyBlocks.SANGA,PartyBlocks.PHOTO_ID).contains(item)&&!match.locked(p.getUUID())){prop.setYRot(p.getYRot());prop.setYHeadRot(p.getYRot());prop.setYBodyRot(p.getYRot());}
        if(item.equals("minecraft:bee_nest")&&!r.bees){r.bees=true;for(int i=0;i<3;i++){var mob=PropSummons.spawn(level(p),PropSummonEntity.Kind.BEE,p.getUUID(),prop.position().add(Math.cos(i*2.1),1,Math.sin(i*2.1)),100,36,true);if(mob!=null)r.mobs.add(mob);}}
        r.mobs.removeIf(Entity::isRemoved);for(int i=0;i<r.mobs.size();i++)PropSummons.tick(match,r.mobs.get(i),prop.position(),i);
        r.discs.removeIf(disc->{if(disc.isRemoved()||disc.tickCount>60){disc.discard();return true;}return false;});
        // Dolls: the attack key held spends the gauge (beam / cloak, hunting only); let go, it refills.
        if(doll(item)){
            boolean eunbi=item.equals(EUNBI);if(r.gauge<0){r.gaugeMax=eunbi?BEAM_TICKS:CLOAK_TICKS;r.gauge=r.gaugeMax;}
            boolean using=r.primary&&match.hunting()&&r.gauge>0;
            if(using){r.gauge=Math.max(0,r.gauge-1);if(eunbi)beam(p,prop,now);}
            else r.gauge=Math.min(r.gaugeMax,r.gauge+r.gaugeMax/(eunbi?BEAM_REFILL:CLOAK_REFILL));
            if(eunbi&&!using&&!prop.text().isEmpty())prop.text("");
            boolean cloak=using&&!eunbi;
            if(cloak!=r.cloak&&cloak){
                // 191: a burst of black smoke as the doll vanishes.
                var box=prop.getBoundingBox();double cx=box.getCenter().x,cy=box.minY+box.getYsize()*.5,cz=box.getCenter().z;
                double sx=box.getXsize()*.4,sy=box.getYsize()*.4,sz=box.getZsize()*.4;
                level(p).sendParticles(ParticleTypes.SQUID_INK,cx,cy,cz,24,sx,sy,sz,.04);
                level(p).sendParticles(ParticleTypes.LARGE_SMOKE,cx,cy,cz,18,sx,sy,sz,.03);
                level(p).sendParticles(ParticleTypes.SMOKE,cx,cy,cz,30,sx,sy,sz,.05);
            }
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
        // A brewing stand's invisibility (or a doll's cloak) hides the disguise itself from everyone while it lasts (PropRenderer); shots still land.
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
        crystal(p,prop,r,now);
        if(!match.hunting())return;
        storm(p,prop,r,now);
        // Hot spring water heals the hider soaking in it (only while hunting; seekers never touch the healing).
        if(item.equals("gungang:hot_spring_water"))HotSpringEffect.touch(p);
        if(now<r.contact)return;
        for(var target:match.seekers()){
            if(target.level()!=prop.level()||!target.getBoundingBox().inflate(.1).intersects(prop.getBoundingBox()))continue;
            if(item.equals("minecraft:fire")||item.equals("minecraft:soul_fire")){match.credit(target,p.getUUID());target.hurt(p.damageSources().inFire(),50);target.setSecondsOnFire(10);r.contact=now+20;}
            if(item.equals("minecraft:torchflower")){match.credit(target,p.getUUID());target.setSecondsOnFire(10);r.contact=now+20;}
            if(item.equals("minecraft:water"))target.clearFire();
            if(item.equals("minecraft:bubble_column")){
                // Rising columns throw seekers up, whirlpools drag them down; the server sends the push since only the client moves the player.
                boolean down=prop.state().getValue(BubbleColumnBlock.DRAG_DOWN);target.clearFire();
                PropHuntMatch.impulse(target,new Vec3(target.getDeltaMovement().x,down?-.6:1.1,target.getDeltaMovement().z));
                p.level().playSound(null,target.blockPosition(),down?net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE:net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
                r.contact=now+10;
            }
            if(item.equals("minecraft:minecart")&&moving){match.credit(target,p.getUUID());target.hurt(p.damageSources().generic(),50);var away=target.position().subtract(prop.position()).normalize();PropHuntMatch.impulse(target,target.getDeltaMovement().add(away.x*1.4,.5,away.z*1.4));r.stun=now+20;r.stunPosition=p.position();r.contact=now+20;}
        }
    }
    /**
     * 255: while hunting, the body walks itself at the nearest seeker (hopping up steps, sidestepping when it stalls),
     * stops at 8 blocks in sight and fires the minigun: ten shots a second without reloading, 3.5 each, slight spread.
     */
    private void auto(LivingEntity p,PropEntity prop,Runtime r,int now){
        if(!match.hunting()){r.autoVy=0;return;}
        var level=level(p);
        var target=match.seekers().stream().filter(q->q.level()==level&&q.isAlive()).min(Comparator.comparingDouble(q->q.distanceToSqr(p))).orElse(null);
        if(target==null)return;
        var eye=p.position().add(0,1.3,0);var aim=target.getBoundingBox().getCenter();
        boolean sight=eye.distanceTo(aim)<=AUTO_RANGE&&level.clip(new ClipContext(eye,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
        var flat=new Vec3(target.getX()-p.getX(),0,target.getZ()-p.getZ());double distance=flat.length();
        var dir=distance<1e-4?Vec3.ZERO:flat.scale(1/distance);
        if(now<r.autoDetour)dir=new Vec3(-dir.z*r.autoSide,0,dir.x*r.autoSide).add(dir.scale(.3)).normalize();
        double speed=sight&&distance<8?0:AUTO_SPEED;
        if(p.onGround())r.autoVy=p.horizontalCollision&&speed>0?.42:0;else r.autoVy=Math.max(-1,r.autoVy-.08);
        p.move(MoverType.SELF,new Vec3(dir.x*speed,r.autoVy,dir.z*speed));p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        // Stalled against something for a second: sidestep for a moment.
        if(speed>0&&r.autoLast!=null&&p.position().subtract(r.autoLast).horizontalDistanceSqr()<.0004){if(++r.autoStuck>20){r.autoStuck=0;r.autoDetour=now+30;r.autoSide=p.getRandom().nextBoolean()?1:-1;}}else r.autoStuck=0;
        r.autoLast=p.position();
        float heading=(float)Math.toDegrees(Math.atan2(-(aim.x-p.getX()),aim.z-p.getZ()));
        prop.setYRot(heading);prop.setYHeadRot(heading);prop.setYBodyRot(heading);
        pin(p,p.getX(),p.getY(),p.getZ());prop.setPos(p.position());
        if(!sight||now<r.autoShot)return;
        r.autoShot=now+2;
        var random=p.getRandom();var muzzle=p.position().add(0,1.2,0).add(dir.scale(.8));
        var shot=aim.subtract(muzzle).normalize().add((random.nextDouble()-.5)*.06,(random.nextDouble()-.5)*.06,(random.nextDouble()-.5)*.06).normalize();
        var end=muzzle.add(shot.scale(AUTO_RANGE));
        var block=level.clip(new ClipContext(muzzle,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        var hit=target.getBoundingBox().inflate(.1).clip(muzzle,end);
        if(hit.isPresent()){end=hit.get();target.invulnerableTime=0;match.credit(target,p.getUUID());target.hurt(p.damageSources().generic(),AUTO_DAMAGE);}
        double length=muzzle.distanceTo(end);
        for(double t=.8;t<length;t+=1.6){var at=muzzle.add(shot.scale(t));level.sendParticles(ParticleTypes.CRIT,at.x,at.y,at.z,1,0,0,0,0);}
        level.sendParticles(ParticleTypes.SMOKE,muzzle.x,muzzle.y,muzzle.z,1,.05,.05,.05,.01);
        if(now%4==0)level.playSound(null,BlockPos.containing(muzzle),TurretSounds.SHOT.get(),net.minecraft.sounds.SoundSource.PLAYERS,1.2f,1.25f);
    }
    /** 254: whoever else looks at the photo's face (in sight, within 32 blocks) gets nauseous for ten seconds. */
    private void photo(LivingEntity p,PropEntity prop,int now){
        if(now%10!=0||!match.hunting()&&!match.hiding())return;
        double yaw=Math.toRadians(prop.getYRot());var front=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));var centre=prop.position().add(0,.7,0);var level=level(p);
        for(var q:match.bodies()){
            if(q==p||!q.isAlive()||q.isSpectator()||q.level()!=level||q.distanceToSqr(centre)>PHOTO_RANGE*PHOTO_RANGE)continue;
            var eye=q.getEyePosition();var to=centre.subtract(eye);
            if(to.dot(front)>=0||q.getLookAngle().dot(to.normalize())<.9)continue;
            if(level.clip(new ClipContext(eye,centre,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,q)).getType()!=HitResult.Type.MISS)continue;
            q.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION,200,0,false,false,true));
        }
    }
    /** 245 dice, 247 slot machine and its jackpot, 250 money rain. */
    private void party(LivingEntity p,PropEntity prop,Runtime r,int now){
        var level=level(p);var random=p.getRandom();
        if(r.diceUntil>0){
            if(now<r.diceUntil){
                if((r.diceUntil-now)%3==0&&prop.state().hasProperty(PartyBlocks.Dice.VALUE)){
                    prop.state(prop.state().setValue(PartyBlocks.Dice.VALUE,1+random.nextInt(6)));
                    level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BONE_BLOCK_HIT,net.minecraft.sounds.SoundSource.BLOCKS,.7f,1.6f+random.nextFloat()*.3f);
                }
            }else{
                r.diceUntil=0;int value=prop.state().hasProperty(PartyBlocks.Dice.VALUE)?prop.state().getValue(PartyBlocks.Dice.VALUE):1;
                level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.BLOCKS,1,1.2f);
                popup(level,prop.getBoundingBox().getCenter().add(0,prop.getBoundingBox().getYsize()/2+.6,0),"주사위 "+value,now+40);
                PropHuntMatch.say(p,Component.literal("주사위: "+value),true);
            }
        }
        if(r.slotAt>=0){
            if(now<r.slotAt){if((r.slotAt-now)%4==0)level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.6f);}
            else{
                r.slotAt=-1;
                if(!r.jackpotWon)level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),net.minecraft.sounds.SoundSource.BLOCKS,1,.6f);
                else{
                    r.jackpotWon=false;r.jackpotUntil=now+JACKPOT_TICKS;
                    r.jackpot=match.seekers().stream().filter(q->q.isAlive()&&q.level()==level).map(Entity::getUUID).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                    level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,net.minecraft.sounds.SoundSource.BLOCKS,4,1);
                    for(var q:match.players()){
                        q.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),net.minecraft.sounds.SoundSource.MASTER,1,1.2f);
                        q.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),net.minecraft.sounds.SoundSource.MASTER,1,1.5f);
                        PropHuntMatch.say(q,Component.literal("777 잭팟! 술래에게 돈벼락이 쏟아집니다").withStyle(net.minecraft.ChatFormatting.GOLD,net.minecraft.ChatFormatting.BOLD),true);
                    }
                    popup(level,prop.getBoundingBox().getCenter().add(0,1.6,0),"JACKPOT 777",now+60);
                }
            }
        }
        // The jackpot: money pours on every seeker for 5 s, who takes a tenth of their health every half second and dies at the end.
        if(r.jackpotUntil>0){
            boolean end=now>=r.jackpotUntil;
            for(var id:r.jackpot){
                var target=match.seekers().stream().filter(q->q.getUUID().equals(id)).findFirst().orElse(null);
                if(target==null||!target.isAlive())continue;
                if(!end&&now%2==0)money(target.level(),target.position().add((random.nextDouble()-.5)*1.6,target.getBbHeight()+2.5+random.nextDouble(),(random.nextDouble()-.5)*1.6),r);
                if(!match.hunting())continue;
                if(end){match.credit(target,p.getUUID());target.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);}
                else if((r.jackpotUntil-now)%10==0){match.credit(target,p.getUUID());target.hurt(p.damageSources().generic(),target.getMaxHealth()/10);}
            }
            if(end){r.jackpotUntil=0;r.jackpot.clear();}
        }
        // The statue's rain: falling cash around it; a seeker hit takes 80 (at most every half second each).
        if(now<r.rainUntil){
            for(int i=0;i<3;i++){
                double a=random.nextDouble()*Math.PI*2,d=Math.sqrt(random.nextDouble())*RAIN_RADIUS;
                var drop=money(level,prop.position().add(Math.cos(a)*d,8+random.nextDouble()*4,Math.sin(a)*d),r);if(drop!=null)r.rain.add(drop);
            }
            if(now%20==0)level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.CHAIN_FALL,net.minecraft.sounds.SoundSource.BLOCKS,1.5f,1.6f);
        }
        r.rain.removeIf(drop->drop.isRemoved()||drop.onGround());
        if(!r.rain.isEmpty()&&match.hunting())for(var seeker:match.seekers()){
            if(seeker.level()!=level||!seeker.isAlive()||r.rainHit.getOrDefault(seeker.getUUID(),0)>now)continue;
            var body=seeker.getBoundingBox().inflate(.2);
            for(var drop:r.rain)if(!drop.isRemoved()&&body.intersects(drop.getBoundingBox())){
                match.credit(seeker,p.getUUID());seeker.hurt(p.damageSources().generic(),RAIN_DAMAGE);r.rainHit.put(seeker.getUUID(),now+10);
                level.playSound(null,seeker.blockPosition(),DeskBlocks.CASH.get(),net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.3f);
                drop.discard();break;
            }
        }
    }
    /** A falling note or coin nobody can pick up (cleared with the discs after 3 s). */
    private static net.minecraft.world.entity.item.ItemEntity money(net.minecraft.world.level.Level level,Vec3 at,Runtime r){
        var random=level.random;var item=random.nextInt(3)==0?net.minecraft.world.item.Items.GOLD_NUGGET:random.nextBoolean()?net.minecraft.world.item.Items.EMERALD:net.minecraft.world.item.Items.PAPER;
        var drop=new net.minecraft.world.entity.item.ItemEntity(level,at.x,at.y,at.z,new net.minecraft.world.item.ItemStack(item),(random.nextDouble()-.5)*.05,-.25,(random.nextDouble()-.5)*.05);
        drop.setNeverPickUp();if(!level.addFreshEntity(drop))return null;r.discs.add(drop);return drop;
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
