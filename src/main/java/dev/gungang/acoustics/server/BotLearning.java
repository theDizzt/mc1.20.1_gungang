package dev.gungang.acoustics.server;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gungang.acoustics.GungangAcoustics;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Opt-in data collection and a bounded residual policy for slot zero only. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class BotLearning {
    private static final Gson JSON=new Gson();
    private static BotPolicy policy;
    private static String control="off";
    private static BotLearningLog log;
    private static UUID demonstrator;
    private static UUID recordingOwner;
    private static long recordedMatch=-1;
    private static String recordingError="";
    private static BotDemonstration demonstration;
    private static net.minecraft.world.phys.Vec3 lastHumanPosition;
    private static int lastHumanTick=-1;
    private static String lastHeld="";
    private static BotGun.Observation lastWeaponObservation;
    private static final Map<String,Integer> fullest=new HashMap<>();
    private static String label="";
    private static int labelUntil,hurtAt=-10000;
    private static long sequence,episode;
    private static Map<String,Object> previous;
    private static double reward;
    private static int nextDecision;
    private static Map<BotTactics.Action,Float> cached=Map.of();
    private static String cachedContext="";
    private static String suggestion="none";
    private static Map<String,BotPolicy.Prediction> predicted=Map.of();
    private static int fireHeldAt=-1,secondaryHeldAt=-1;

    private static Path directory(){return ServerFiles.root().resolve("bot-learning");}
    static String weapon(ItemStack stack){
        if(stack==null||stack.isEmpty())return "none";
        var tag=stack.getTag();
        return tag!=null&&tag.contains("GunGangLearningWeaponId")?tag.getString("GunGangLearningWeaponId"):
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    static Map<String,Double> features(BotAgent a,BotTactics.Situation s,BotObjective mission){
        var f=new TreeMap<>(BotPolicy.features(s.health(),s.visible(),s.distance(),s.underFire(),a.bot.isOnFire(),mission,
                mission!=null&&mission.contains(a.bot.getBoundingBox()),weapon(a.primaryStack),weapon(a.secondaryStack),a.team));
        weaponFeatures(f,a.bot,BotGun.observe(a.bot),a.usingSecondary,a.gadgets.readyForLearning(a.bot.level().getGameTime()));return Map.copyOf(f);
    }
    private static void weaponFeatures(Map<String,Double> f,LivingEntity actor,BotGun.Observation observation,boolean secondary,boolean ready){
        String held=weapon(actor.getMainHandItem());String key=(actor.getUUID().equals(BotManager.ID)?"bot:":"human:")+held;
        if(fullest.size()>256)fullest.clear();int max=fullest.merge(key,Math.max(0,observation.rounds()),Math::max);
        f.put("ammo",observation.rounds()<0?1d:max==0?0d:observation.rounds()/(double)max);
        f.put("ammoKnown",observation.rounds()>=0?1d:0d);f.put("reloading",observation.reloading()?1d:0d);
        f.put("reloadKnown",observation.reloadKnown()?1d:0d);f.put("secondaryReady",ready?1d:0d);f.put("usingSecondary",secondary?1d:0d);
        if(BotPolicy.feature("held:"+held))f.put("held:"+held,1d);
    }
    static Map<BotTactics.Action,Float> offsets(BotAgent a,BotTactics.Situation s,BotObjective mission,int now){
        if(a.slot!=0||policy==null||control.equals("off"))return Map.of();
        String context=context(a);
        if(now>=nextDecision||!context.equals(cachedContext)){
            var f=features(a,s,mission);
            predicted=policy.predict(MatchManager.learningMode(),f,weapon(a.primaryStack)+"|"+weapon(a.secondaryStack));
            if(!predicted.containsKey("fire")||!predicted.get("fire").action().equals("NO"))fireHeldAt=-1;
            if(!predicted.containsKey("secondary")||!predicted.get("secondary").action().equals("NO"))secondaryHeldAt=-1;
            var adjusted=new EnumMap<BotTactics.Action,Float>(BotTactics.Action.class);adjusted.putAll(policy.offsets(MatchManager.learningMode(),f));
            var move=predicted.get("movement");
            if(move!=null){
                BotTactics.Action action=switch(move.action()){
                    case "APPROACH"->s.visible()>0?BotTactics.Action.PUSH:null;
                    case "WITHDRAW"->s.visible()>0?(BotTactics.score(BotTactics.Action.RETREAT,s)>0?BotTactics.Action.RETREAT:BotTactics.Action.COVER):null;
                    case "LATERAL"->s.visible()>0?BotTactics.Action.ENGAGE:null;
                    case "HOLD"->s.visible()>0?BotTactics.Action.ENGAGE:mission!=null&&mission.contains(a.bot.getBoundingBox())?BotTactics.Action.OBJECTIVE:null;
                    case "TO_OBJECTIVE"->mission!=null?BotTactics.Action.OBJECTIVE:null;
                    default->null;
                };
                if(action!=null)adjusted.merge(action,8f,Float::sum);
            }
            cached=Map.copyOf(adjusted);nextDecision=now+10;cachedContext=context;
            suggestion=cached.entrySet().stream().max(Map.Entry.comparingByValue()).map(e->e.getKey().name()).orElse("none");
        }
        return control.equals("on")?cached:Map.of();
    }
    private static boolean predicts(BotAgent a,String head,String action,int now){
        return a.slot==0&&control.equals("on")&&context(a).equals(cachedContext)&&now<=nextDecision&&predicted.containsKey(head)&&predicted.get(head).action().equals(action);
    }
    private static String context(BotAgent a){return MatchManager.learningMode()+":"+weapon(a.primaryStack)+":"+weapon(a.secondaryStack)+":"+weapon(a.bot.getMainHandItem())+":"+a.team;}
    static boolean allowFire(BotAgent a,int now){
        if(a.slot!=0)return true;
        if(a.bot.getHealth()<a.bot.getMaxHealth()*.35f||a.perception.danger(a.bot.position())!=null||!predicts(a,"fire","NO",now)){fireHeldAt=-1;return true;}
        if(fireHeldAt<0)fireHeldAt=now;
        return now-fireHeldAt>=10;
    }
    static boolean requestReload(BotAgent a,int now){return predicts(a,"reload","YES",now);}
    static boolean allowSecondary(BotEntity bot,int now){
        var a=BotManager.agent(bot);if(a==null||a.slot!=0)return true;
        if(bot.getHealth()<bot.getMaxHealth()*.35f||a.perception.danger(bot.position())!=null||!predicts(a,"secondary","NO",now)){secondaryHeldAt=-1;return true;}
        if(secondaryHeldAt<0)secondaryHeldAt=now;
        return now-secondaryHeldAt>=10;
    }
    static void decision(BotAgent a,BotTactics.Situation s,BotObjective mission,BotTactics.Action action,int now){
        if(a.slot!=0||log==null||demonstrator==null||!MatchManager.botRunning())return;
        if(previous!=null&&now-(int)previous.get("tick")<10&&action.name().equals(previous.get("action")))return;
        var row=base("bot",a.bot,now);row.put("features",features(a,s,mission));row.put("action",action.name());
        row.put("controller",control);row.put("policy",policy==null?"none":policy.version);row.put("suggestion",suggestion);
        row.put("offsets",cached);
        row.put("controls",predicted);
        row.put("ammo",s.ammo());row.put("reloading",s.reloading());row.put("usingSecondary",a.usingSecondary);
        row.put("knownHazard",s.hazard());
        objective(row,mission,a.bot);
        row.put("secondaryState",a.gadgets.describe(a.bot));
        if(previous!=null) {
            int dt=now-(int)previous.get("tick");
            boolean cut=dt>40||!Objects.equals(row.get("mode"),previous.get("mode"))||!Objects.equals(row.get("map"),previous.get("map"));
            transition(row,false,cut?"gap":"step");
        }
        previous=row;reward=0;emit(row);
    }
    private static void transition(Map<String,Object> next,boolean terminal,String reason){
        if(previous==null)return;
        Map<String,Object> row=new LinkedHashMap<>();row.put("schema",1);row.put("type","transition");
        row.put("episode",episode);row.put("state",previous);row.put("next",next);row.put("reward",reward);
        row.put("terminal",terminal);row.put("reason",reason);emit(row);
    }
    static void end(BotAgent a,boolean dead){
        if(a.slot!=0)return;
        if(dead)reward-=1;
        transition(null,dead,dead?"death":"truncated");previous=null;reward=0;episode++;nextDecision=0;cached=Map.of();predicted=Map.of();fireHeldAt=secondaryHeldAt=-1;
    }
    static void matchEnd(String winner,int alpha,int bravo){
        if(demonstrator==null)return;
        var a=BotManager.agent(0);
        if(previous!=null&&a!=null&&!winner.isEmpty())reward+=winner.equals(MatchManager.freeForAll()!=null?a.id.toString():a.team)?2:-2;
        transition(null,true,"match_end");previous=null;reward=0;episode++;label="";
        Map<String,Object> row=new LinkedHashMap<>();row.put("schema",1);row.put("type","match_end");
        row.put("mode",MatchManager.learningMode());row.put("winner",winner);row.put("alphaScore",alpha);row.put("bravoScore",bravo);emit(row);
        closeRecording("match_end");
    }
    private static void objective(Map<String,Object> row,BotObjective mission,LivingEntity actor){
        if(mission==null)return;
        row.put("objectiveAction",mission.act().name());row.put("objectiveLabel",mission.label());
        row.put("objectiveDistance",actor.position().distanceTo(mission.center()));
    }
    static void event(LivingEntity actor,String event){
        if(log==null||demonstrator==null||actor==null||actor.getServer()==null||!MatchManager.botRunning()||(!actor.getUUID().equals(demonstrator)&&!actor.getUUID().equals(BotManager.ID)))return;
        var row=base("event",actor,actor.getServer().getTickCount());row.put("event",event);
        row.put("held",weapon(actor.getMainHandItem()));emit(row);
        if(event.equals("shot_cue"))observedAction(actor,"fire");
    }
    static void observedAction(LivingEntity actor,String action){
        if(actor!=null&&!actor.level().isClientSide&&actor.getUUID().equals(demonstrator)&&demonstration!=null)demonstration.action(action);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){
        if(log==null||demonstrator==null||event.isCanceled()||!MatchManager.botRunning()||event.getEntity().level().isClientSide)return;
        var victim=event.getEntity();var attacker=event.getSource().getEntity();
        if(victim.getUUID().equals(demonstrator))hurtAt=victim.getServer().getTickCount();
        double amount=Math.min(event.getAmount(),victim.getHealth());
        if(victim.getUUID().equals(BotManager.ID))reward-=Math.min(1,amount/Math.max(1,victim.getMaxHealth()));
        if(attacker instanceof LivingEntity source&&source.getUUID().equals(BotManager.ID)&&BotManager.combatant(BotManager.side(victim))&&!BotManager.side(source).equals(BotManager.side(victim)))
            reward+=Math.min(1,amount/Math.max(1,victim.getMaxHealth()));
        if(victim.getUUID().equals(demonstrator)||victim.getUUID().equals(BotManager.ID)){
            var row=base("damage",victim,victim.getServer().getTickCount());row.put("amount",amount);row.put("cause",event.getSource().getMsgId());emit(row);
        }
        if(attacker instanceof LivingEntity source&&(source.getUUID().equals(demonstrator)||source.getUUID().equals(BotManager.ID))){
            var row=base("damage_dealt",source,source.getServer().getTickCount());row.put("amount",amount);row.put("cause",event.getSource().getMsgId());emit(row);
        }
    }
    private static Map<String,Object> base(String type,LivingEntity entity,int tick){
        Map<String,Object> row=new LinkedHashMap<>();row.put("schema",1);row.put("type",type);row.put("tick",tick);
        row.put("episode",episode);row.put("mode",MatchManager.learningMode());
        row.put("actor",entity.getUUID().equals(BotManager.ID)?"bot1":"human");
        row.put("map",BotManager.arena()==null?"none":BotManager.arena().id());
        row.put("position",List.of(entity.getX(),entity.getY(),entity.getZ()));
        row.put("yaw",entity.getYRot());row.put("pitch",entity.getXRot());return row;
    }
    private static void emit(Map<String,Object> row){if(log!=null&&demonstrator!=null){row.put("sequence",sequence++);log.write(JSON.toJson(row));}}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||recordingOwner==null)return;
        var server=BotManager.server();if(server==null)return;int now=server.getTickCount();
        var p=server.getPlayerList().getPlayer(recordingOwner);
        if(demonstrator!=null&&recordedMatch!=MatchManager.learningMatch())closeRecording("match_changed");
        if(!MatchManager.botRunning()){if(demonstrator!=null)closeRecording("match_inactive");resetHuman();return;}
        if(demonstrator==null&&recordedMatch!=MatchManager.learningMatch()&&p!=null&&BotManager.combatant(BotManager.side(p))&&(log==null||log.finished()))openRecording(p);
        if(demonstrator==null)return;
        if(p==null||!p.isAlive()||p.isSpectator()||LobbyManager.respawning(p.getUUID())||!BotManager.combatant(BotManager.side(p))
                ||BotManager.arena()==null||!p.level().dimension().location().toString().equals(BotManager.arena().dimension())
                ||MatchManager.bomb()!=null&&!MatchManager.bomb().activePlay()){
            resetHuman();return;
        }
        String primary=weapon(p.getInventory().getItem(0)),secondary=weapon(p.getOffhandItem()),held=weapon(p.getMainHandItem());
        String context=MatchManager.learningMatch()+":"+p.level().dimension().location()+":"+LobbyManager.team(p.getUUID())+":"+primary+":"+secondary+":"+held;
        boolean discontinuity=lastHumanTick>=0&&(now-lastHumanTick!=1||lastHumanPosition.distanceToSqr(p.position())>64);
        if(discontinuity||demonstration!=null&&!context.equals(demonstration.context))resetHuman();
        var observation=BotGun.observe(p);
        if(lastWeaponObservation!=null&&lastHeld.equals(held)){
            if(observation.reloadKnown()&&observation.reloading()&&!lastWeaponObservation.reloading())observedAction(p,"reload");
            if(observation.rounds()>=0&&lastWeaponObservation.rounds()>observation.rounds()&&!observation.reloading())observedAction(p,"fire");
        }
        if(demonstration!=null)demonstration.observe(p.onGround()&&!p.isPassenger()&&!p.isInWater()&&p.hurtTime==0,observation.reloadKnown(),observation.rounds()>=0);
        lastWeaponObservation=observation;lastHeld=held;lastHumanTick=now;lastHumanPosition=p.position();
        if(demonstration!=null&&now-demonstration.tick<10)return;
        if(demonstration!=null){
            var sample=new LinkedHashMap<>(demonstration.start);sample.put("type","demonstration");sample.put("observationVersion",2);
            var targets=new TreeMap<>(demonstration.targets(p.position()));
            if(secondaryObservable(p.getOffhandItem()))targets.putIfAbsent("secondary","NO");
            sample.put("targets",targets);sample.put("endTick",now);
            sample.put("endPosition",List.of(p.getX(),p.getY(),p.getZ()));sample.put("endYaw",p.getYRot());sample.put("endPitch",p.getXRot());
            emit(sample);
        }
        String side=BotManager.side(p);int visible=0;double distance=Double.POSITIVE_INFINITY;
        net.minecraft.world.phys.Vec3 nearest=null;
        var entities=new ArrayList<LivingEntity>(p.serverLevel().players());entities.addAll(BotManager.entities());
        for(var e:entities){
            // Disguised hiders need their own observation adapter; never expose their player bodies.
            if(MatchManager.realPropHunt()!=null)break;
            if(e==p||e.level()!=p.level()||!e.isAlive()||e.isSpectator()||!BotManager.combatant(BotManager.side(e))||side.equals(BotManager.side(e))||FreeForAllMatch.blocked(e))continue;
            if(e instanceof ServerPlayer other&&(other.isCreative()||LobbyManager.respawning(other.getUUID())))continue;
            double d=p.distanceTo(e);if(d>64)continue;
            double dot=p.getLookAngle().dot(e.getEyePosition().subtract(p.getEyePosition()).normalize());
            if(dot<.5||!p.hasLineOfSight(e)||SpawnGateProtection.separates(p,e)||SmokeScreen.blocks(p,e))continue;
            visible++;if(d<distance){distance=d;nearest=e.position();}
        }
        var mission=MatchManager.botObjective(LobbyManager.team(p.getUUID()),0);
        var row=base("human",p,now);
        objective(row,mission,p);
        var f=new TreeMap<>(BotPolicy.features(p.getHealth()/p.getMaxHealth(),visible,distance,now-hurtAt<30,p.isOnFire(),mission,
                mission!=null&&mission.contains(p.getBoundingBox()),primary,secondary,LobbyManager.team(p.getUUID())));
        weaponFeatures(f,p,observation,SecondaryRuntime.secondary(p.getMainHandItem()),SecondaryRuntime.ready(p,p.getOffhandItem()));
        row.put("features",Map.copyOf(f));row.put("loadout",primary+"|"+secondary);row.put("context",context);
        row.put("action",now<labelUntil?label:"");row.put("labelSource",now<labelUntil&&!label.isEmpty()?"manual":"unlabelled");
        row.put("held",weapon(p.getMainHandItem()));row.put("usingItem",p.isUsingItem());row.put("sprinting",p.isSprinting());
        row.put("crouching",p.isCrouching());row.put("secondaryCooldown",SecondaryRuntime.remaining(p,p.getOffhandItem()));
        row.put("velocity",List.of(p.getDeltaMovement().x,p.getDeltaMovement().y,p.getDeltaMovement().z));
        emit(row);
        demonstration=new BotDemonstration(new LinkedHashMap<>(row),p.position(),nearest,mission==null?null:mission.center(),now,context,observation.rounds()>=0);
        demonstration.observe(p.onGround()&&!p.isPassenger()&&!p.isInWater()&&p.hurtTime==0,observation.reloadKnown(),observation.rounds()>=0);
    }
    private static boolean secondaryObservable(ItemStack stack){
        return stack.getItem() instanceof GadgetItem item&&Set.of("molotov","arrow_grenade","landmine","bear_trap","turret","orbital_strike","barrier","void_anchor","air_assault","jetpack","recovery_potion","adrenaline").contains(item.kind);
    }
    private static void resetHuman(){demonstration=null;lastHumanPosition=null;lastHumanTick=-1;lastWeaponObservation=null;lastHeld="";label="";labelUntil=0;}
    private static void openRecording(ServerPlayer p){
        recordedMatch=MatchManager.learningMatch();recordingError="";
        try{log=new BotLearningLog(directory().resolve("recordings"));}
        catch(java.io.IOException error){recordingError=error.toString();p.sendSystemMessage(Component.literal("Bot recording failed: "+recordingError));return;}
        demonstrator=p.getUUID();resetHuman();hurtAt=-10000;sequence=0;episode++;previous=null;reward=0;fullest.clear();
        var row=base("recording_start",p,p.getServer().getTickCount());row.put("observationVersion",2);emit(row);
        p.sendSystemMessage(Component.literal("Bot learning: recording match → "+log.path.getFileName()));
    }
    private static void closeRecording(String reason){
        if(demonstrator==null)return;
        transition(null,false,reason);previous=null;reward=0;episode++;
        Map<String,Object> row=new LinkedHashMap<>();row.put("schema",1);row.put("type","recording_end");row.put("reason",reason);row.put("dropped",log==null?0:log.dropped);emit(row);
        if(log!=null)log.close();demonstrator=null;resetHuman();
    }
    private static void saveRecordingMode(UUID owner){
        try{
            Files.createDirectories(directory());var data=new com.google.gson.JsonObject();data.addProperty("schema",1);
            if(owner!=null)data.addProperty("player",owner.toString());
            Path target=directory().resolve("recording-mode.json"),temp=directory().resolve("recording-mode.json.tmp");
            Files.writeString(temp,JSON.toJson(data));
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot save recording mode: "+error.getMessage(),error);}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        recordingOwner=null;recordedMatch=-1;recordingError="";
        Path path=directory().resolve("recording-mode.json");
        if(!Files.exists(path))return;
        try{
            if(Files.size(path)>4096)throw new IllegalArgumentException("Recording mode file is too large");
            var data=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if(data.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported recording mode schema");
            if(data.has("player"))recordingOwner=UUID.fromString(data.get("player").getAsString());
        }catch(java.io.IOException|RuntimeException error){recordingError=error.toString();com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Recording mode could not be restored",error);}
    }
    private static void stop(){saveRecordingMode(null);closeRecording("recording_stopped");recordingOwner=null;resetHuman();}
    private static void enableRecording(CommandSourceStack source){
        if(!(source.getEntity() instanceof ServerPlayer p))throw new IllegalStateException("Run in game as the demonstrating player");
        if(recordingOwner!=null&&!recordingOwner.equals(p.getUUID()))throw new IllegalStateException("Another player's recording mode is on; stop it first");
        if(recordingOwner==null){saveRecordingMode(p.getUUID());recordingOwner=p.getUUID();recordedMatch=-1;recordingError="";}
        // Idempotent: repeatedly enabling the mode never splits an active match file.
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){closeRecording("server_stopping");recordingOwner=null;if(log!=null)log.finishOnShutdown();log=null;policy=null;control="off";cached=Map.of();predicted=Map.of();nextDecision=0;suggestion="none";fullest.clear();recordedMatch=-1;recordingError="";fireHeldAt=secondaryHeldAt=-1;}
    private static int command(CommandSourceStack source,Runnable work){
        try{work.run();source.sendSuccess(()->Component.literal(status()),false);return 1;}
        catch(Exception error){source.sendFailure(Component.literal("Bot learning: "+error.getMessage()));return 0;}
    }
    private static String status(){return "Bot 1 learning="+control+", model="+(policy==null?"none":policy.version)+", suggested="+suggestion+
            ", recordingMode="+(recordingOwner!=null?"ON":"OFF")+", recording="+(demonstrator!=null)+", recordingError="+recordingError+ (log==null?"":", rows="+log.written+", dropped="+log.dropped+", error="+log.failure+", file="+log.path);}
    static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var commands=Commands.literal("learning").executes(c->command(c.getSource(),()->{}));
        for(String value:List.of("off","shadow","on"))commands.then(Commands.literal(value).executes(c->command(c.getSource(),()->{
            if(!value.equals("off")&&policy==null)throw new IllegalStateException("Load policy.json with reload first");
            transition(null,false,"controller_changed");previous=null;reward=0;episode++;control=value;nextDecision=0;cached=Map.of();predicted=Map.of();suggestion="none";fireHeldAt=secondaryHeldAt=-1;
        })));
        commands.then(Commands.literal("reload").executes(c->command(c.getSource(),()->{
            try{
                Path path=directory().resolve("policy.json");if(Files.size(path)>262144)throw new IllegalArgumentException("Policy exceeds 256 KiB");
                var candidate=BotPolicy.parse(Files.readString(path));
                transition(null,false,"policy_changed");previous=null;reward=0;episode++;policy=candidate;nextDecision=0;cached=Map.of();predicted=Map.of();suggestion="none";fireHeldAt=secondaryHeldAt=-1;
            }catch(java.io.IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
        })));
        commands.then(Commands.literal("record").executes(c->command(c.getSource(),()->enableRecording(c.getSource())))
                .then(Commands.literal("on").executes(c->command(c.getSource(),()->enableRecording(c.getSource()))))
                .then(Commands.literal("off").executes(c->command(c.getSource(),BotLearning::stop)))
                .then(Commands.literal("status").executes(c->command(c.getSource(),()->{}))));
        commands.then(Commands.literal("stop").executes(c->command(c.getSource(),BotLearning::stop)));
        commands.then(Commands.literal("label").then(Commands.argument("action",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(BotTactics.Action.values()).map(Enum::name),b))
                .executes(c->command(c.getSource(),()->{
                    if(demonstrator==null||!(c.getSource().getEntity() instanceof ServerPlayer p)||!demonstrator.equals(p.getUUID()))throw new IllegalStateException("Start your recording first");
                    label=BotTactics.Action.valueOf(StringArgumentType.getString(c,"action").toUpperCase(Locale.ROOT)).name();
                    labelUntil=c.getSource().getServer().getTickCount()+200;
                }))));
        root.then(commands);
    }
    private BotLearning() { }
}
