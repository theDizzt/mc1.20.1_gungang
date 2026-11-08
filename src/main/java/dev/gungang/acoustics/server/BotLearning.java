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
    private static String label="";
    private static int labelUntil,hurtAt=-10000;
    private static long sequence,episode;
    private static Map<String,Object> previous;
    private static double reward;
    private static int nextDecision;
    private static Map<BotTactics.Action,Float> cached=Map.of();
    private static String cachedContext="";
    private static String suggestion="none";

    private static Path directory(){return ServerFiles.root().resolve("bot-learning");}
    static String weapon(ItemStack stack){
        if(stack==null||stack.isEmpty())return "none";
        var tag=stack.getTag();
        return tag!=null&&tag.contains("GunGangLearningWeaponId")?tag.getString("GunGangLearningWeaponId"):
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    static Map<String,Double> features(BotAgent a,BotTactics.Situation s,BotObjective mission){
        return BotPolicy.features(s.health(),s.visible(),s.distance(),s.underFire(),a.bot.isOnFire(),mission,
                mission!=null&&mission.contains(a.bot.getBoundingBox()),weapon(a.primaryStack),weapon(a.secondaryStack),a.team);
    }
    static Map<BotTactics.Action,Float> offsets(BotAgent a,BotTactics.Situation s,BotObjective mission,int now){
        if(a.slot!=0||policy==null||control.equals("off"))return Map.of();
        String context=MatchManager.learningMode()+":"+weapon(a.primaryStack)+":"+weapon(a.secondaryStack)+":"+a.team;
        if(now>=nextDecision||!context.equals(cachedContext)){
            cached=policy.offsets(MatchManager.learningMode(),features(a,s,mission));nextDecision=now+10;cachedContext=context;
            suggestion=cached.entrySet().stream().max(Map.Entry.comparingByValue()).map(e->e.getKey().name()).orElse("none");
        }
        return control.equals("on")?cached:Map.of();
    }
    static void decision(BotAgent a,BotTactics.Situation s,BotObjective mission,BotTactics.Action action,int now){
        if(a.slot!=0||log==null||demonstrator==null||!MatchManager.botRunning())return;
        if(previous!=null&&now-(int)previous.get("tick")<10&&action.name().equals(previous.get("action")))return;
        var row=base("bot",a.bot,now);row.put("features",features(a,s,mission));row.put("action",action.name());
        row.put("controller",control);row.put("policy",policy==null?"none":policy.version);row.put("suggestion",suggestion);
        row.put("offsets",cached);
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
        transition(null,dead,dead?"death":"truncated");previous=null;reward=0;episode++;nextDecision=0;cached=Map.of();
    }
    static void matchEnd(String winner,int alpha,int bravo){
        if(demonstrator==null)return;
        var a=BotManager.agent(0);
        if(previous!=null&&a!=null&&!winner.isEmpty())reward+=winner.equals(MatchManager.freeForAll()!=null?a.id.toString():a.team)?2:-2;
        transition(null,true,"match_end");previous=null;reward=0;episode++;label="";
        Map<String,Object> row=new LinkedHashMap<>();row.put("schema",1);row.put("type","match_end");
        row.put("mode",MatchManager.learningMode());row.put("winner",winner);row.put("alphaScore",alpha);row.put("bravoScore",bravo);emit(row);
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
        if(event.phase!=TickEvent.Phase.END||log==null||demonstrator==null)return;
        var server=BotManager.server();if(server==null)return;int now=server.getTickCount();
        var p=server.getPlayerList().getPlayer(demonstrator);
        if(p==null){stop();return;}
        if(!MatchManager.botRunning()||!p.isAlive()||p.isSpectator()||LobbyManager.respawning(p.getUUID())||!BotManager.combatant(BotManager.side(p))){label="";labelUntil=0;return;}
        if(now%10!=0)return;
        String side=BotManager.side(p);int visible=0;double distance=Double.POSITIVE_INFINITY;
        var entities=new ArrayList<LivingEntity>(p.serverLevel().players());entities.addAll(BotManager.entities());
        for(var e:entities){
            // Disguised hiders need their own observation adapter; never expose their player bodies.
            if(MatchManager.realPropHunt()!=null)break;
            if(e==p||e.level()!=p.level()||!e.isAlive()||e.isSpectator()||!BotManager.combatant(BotManager.side(e))||side.equals(BotManager.side(e))||FreeForAllMatch.blocked(e))continue;
            if(e instanceof ServerPlayer other&&(other.isCreative()||LobbyManager.respawning(other.getUUID())))continue;
            double d=p.distanceTo(e);if(d>64)continue;
            double dot=p.getLookAngle().dot(e.getEyePosition().subtract(p.getEyePosition()).normalize());
            if(dot<.5||!p.hasLineOfSight(e)||SpawnGateProtection.separates(p,e)||SmokeScreen.blocks(p,e))continue;
            visible++;distance=Math.min(distance,d);
        }
        var mission=MatchManager.botObjective(LobbyManager.team(p.getUUID()),0);
        var row=base("human",p,now);
        objective(row,mission,p);
        row.put("features",BotPolicy.features(p.getHealth()/p.getMaxHealth(),visible,distance,now-hurtAt<30,p.isOnFire(),mission,
                mission!=null&&mission.contains(p.getBoundingBox()),weapon(p.getInventory().getItem(0)),weapon(p.getOffhandItem()),LobbyManager.team(p.getUUID())));
        row.put("action",now<labelUntil?label:"");row.put("labelSource",now<labelUntil&&!label.isEmpty()?"manual":"unlabelled");
        row.put("held",weapon(p.getMainHandItem()));row.put("usingItem",p.isUsingItem());row.put("sprinting",p.isSprinting());
        row.put("crouching",p.isCrouching());row.put("secondaryCooldown",SecondaryRuntime.remaining(p,p.getOffhandItem()));
        row.put("velocity",List.of(p.getDeltaMovement().x,p.getDeltaMovement().y,p.getDeltaMovement().z));
        emit(row);
    }
    private static void stop(){
        transition(null,false,"recording_stopped");previous=null;reward=0;episode++;
        if(log!=null)log.close();demonstrator=null;label="";
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){stop();if(log!=null)log.finishOnShutdown();log=null;policy=null;control="off";cached=Map.of();nextDecision=0;suggestion="none";}
    private static int command(CommandSourceStack source,Runnable work){
        try{work.run();source.sendSuccess(()->Component.literal(status()),false);return 1;}
        catch(Exception error){source.sendFailure(Component.literal("Bot learning: "+error.getMessage()));return 0;}
    }
    private static String status(){return "Bot 1 learning="+control+", model="+(policy==null?"none":policy.version)+", suggested="+suggestion+
            ", recording="+(demonstrator!=null)+ (log==null?"":", rows="+log.written+", dropped="+log.dropped+", error="+log.failure+", file="+log.path);}
    static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var commands=Commands.literal("learning").executes(c->command(c.getSource(),()->{}));
        for(String value:List.of("off","shadow","on"))commands.then(Commands.literal(value).executes(c->command(c.getSource(),()->{
            if(!value.equals("off")&&policy==null)throw new IllegalStateException("Load policy.json with reload first");
            transition(null,false,"controller_changed");previous=null;reward=0;episode++;control=value;nextDecision=0;cached=Map.of();suggestion="none";
        })));
        commands.then(Commands.literal("reload").executes(c->command(c.getSource(),()->{
            try{
                Path path=directory().resolve("policy.json");if(Files.size(path)>262144)throw new IllegalArgumentException("Policy exceeds 256 KiB");
                var candidate=BotPolicy.parse(Files.readString(path));
                transition(null,false,"policy_changed");previous=null;reward=0;episode++;policy=candidate;nextDecision=0;cached=Map.of();suggestion="none";
            }catch(java.io.IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
        })));
        commands.then(Commands.literal("record").executes(c->command(c.getSource(),()->{
            if(!(c.getSource().getEntity() instanceof ServerPlayer p))throw new IllegalStateException("Run in game as the demonstrating player");
            if(log!=null&&!log.finished())throw new IllegalStateException("Stop current recording and wait for its writer to finish");
            try{log=new BotLearningLog(directory().resolve("recordings"));}catch(java.io.IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
            demonstrator=p.getUUID();label="";hurtAt=-10000;sequence=0;episode++;previous=null;reward=0;
        })));
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
