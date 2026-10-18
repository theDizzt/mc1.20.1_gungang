package dev.gungang.acoustics.server;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;

final class GadgetSmokeChecksModify {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(MinecraftServer server)throws Exception{
        String id="modify_smoke_"+UUID.randomUUID().toString().substring(0,8);Path root=ServerFiles.root();
        Path map=root.resolve("server/maps/"+id+".json"),weapon=root.resolve("server/weapons/"+id+".json"),secondary=root.resolve("server/sub_weapons/"+id+".json");
        var backups=new LinkedHashMap<Path,String>();
        for(var p:List.of(map,weapon,secondary,root.resolve("item/molotov.json"),root.resolve("item/acid_nozzle.json"),root.resolve("item/jetpack.json")))backups.put(p,Files.exists(p)?Files.readString(p):null);
        var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
        try{
            var arena=ServerFiles.GSON.toJsonTree(EscortRules.example()).getAsJsonObject();arena.addProperty("id",id);arena.addProperty("custom","keep");AreaEditor.atomicWrite(map,ServerFiles.GSON.toJson(arena));
            var primary=new Loadouts.Weapon(id,"{id:'minecraft:bow',Count:1b}","테스트 활","원래 설명",new Loadouts.Specs("6","5","4","3","2"),"사용법");
            var backup=new Loadouts.Weapon(id,"{id:'minecraft:iron_sword',Count:1b}","테스트 검","원래 설명",null,"사용법");
            AreaEditor.atomicWrite(weapon,ServerFiles.GSON.toJson(primary));AreaEditor.atomicWrite(secondary,ServerFiles.GSON.toJson(backup));
            check(dispatcher.execute("gungang modify map "+id+" name 황금 광산 호위",source)==1,"Map name with spaces");
            check(dispatcher.execute("gungang modify map "+id+" description \"새 맵 설명\"",source)==1,"Quoted description");
            check(dispatcher.execute("gungang modify map "+id+" escort.speed 1.75",source)==1,"Nested map setting");
            var data=JsonParser.parseString(Files.readString(map)).getAsJsonObject();check(data.get("name").getAsString().equals("황금 광산 호위")&&data.get("description").getAsString().equals("새 맵 설명")&&data.get("custom").getAsString().equals("keep"),"Metadata and unrelated fields preserved");
            check(dispatcher.execute("gungang modify weapon "+id+" specs.damage 32",source)==1,"Display spec edit");
            check(dispatcher.execute("gungang modify weapon "+id+" maxHealth 150",source)==1,"Actual health edit");
            check(dispatcher.execute("gungang modify secondary "+id+" cooldownSeconds 4.5",source)==1,"Secondary cooldown edit");
            var loaded=Loadouts.load();check(loaded.primary().stream().anyMatch(w->w.id().equals(id)&&w.maxHealth()==150&&w.specs().damage().equals("32"))&&loaded.secondary().stream().anyMatch(w->w.id().equals(id)&&w.cooldownSeconds()==4.5),"Runtime loadout reflects edits");
            check(dispatcher.execute("gungang modify item molotov cooldownSeconds 42.5",source)==1&&ExtraGadgetConfig.molotov.cooldownSeconds()==42.5,"Item runtime apply");
            check(dispatcher.execute("gungang modify block acid_nozzle damageIntervalTicks 12",source)==1&&GimmickConfig.acidNozzle.damageIntervalTicks()==12,"Block runtime apply");
            check(dispatcher.execute("gungang modify item jetpack verticalFlight true",source)==1&&ExtraGadgetConfig.jet.verticalFlight(),"Boolean edit");
            String unchanged=Files.readString(root.resolve("item/acid_nozzle.json"));
            check(dispatcher.execute("gungang modify block acid_nozzle damageIntervalTicks 1.5",source)==0&&Files.readString(root.resolve("item/acid_nozzle.json")).equals(unchanged),"Fractional integer rejected");
            unchanged=Files.readString(root.resolve("item/molotov.json"));
            check(dispatcher.execute("gungang modify item molotov radius -1",source)==0&&Files.readString(root.resolve("item/molotov.json")).equals(unchanged)&&ExtraGadgetConfig.molotov.cooldownSeconds()==42.5,"Bounds failure preserves disk and runtime");
            check(dispatcher.execute("gungang modify item molotov radius NaN",source)==0,"Nonfinite number rejected");
            check(dispatcher.execute("gungang modify map "+id+" id renamed",source)==0,"IDs cannot be accidentally changed");
            check(dispatcher.execute("gungang modify item jetpack verticalFlight yes",source)==0,"Boolean typo rejected");
            check(dispatcher.execute("gungang modify map",source)==1&&dispatcher.execute("gungang modify item molotov",source)==1&&dispatcher.execute("gungang modify item molotov cooldownSeconds",source)==1,"Lists and value queries");
            var parsed=dispatcher.parse("gungang modify item molotov cool",source);check(dispatcher.getCompletionSuggestions(parsed).join().getList().stream().anyMatch(s->s.getText().equals("cooldownSeconds")),"Field tab completion");
            boolean denied=false;try{dispatcher.execute("gungang modify item",source.withPermission(0));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}check(denied,"Nonoperator denied");
            String originalWeapon=Files.readString(weapon);Files.writeString(weapon,"{}");
            check(dispatcher.execute("gungang modify item molotov radius 6",source)==0&&Files.readString(root.resolve("item/molotov.json")).equals(unchanged)&&ExtraGadgetConfig.molotov.radius()!=6,"Failed reload rolls back edited file");Files.writeString(weapon,originalWeapon);
            var phase=LobbyManager.class.getDeclaredField("phase");phase.setAccessible(true);Object old=phase.get(null);try{phase.set(null,"RUNNING");check(dispatcher.execute("gungang modify item molotov radius 6",source)==0,"Live match edits blocked");check(dispatcher.execute("gungang modify item molotov radius",source)==1,"Live match queries allowed");}finally{phase.set(null,old);}
            System.out.println("GUNGANG_MODIFY_SMOKE_PASS map/weapon/item/block edits, runtime reload, suggestions, strict types, rollback, permissions");
        }finally{for(var entry:backups.entrySet())if(entry.getValue()==null)Files.deleteIfExists(entry.getKey());else AreaEditor.atomicWrite(entry.getKey(),entry.getValue());LobbyManager.refreshModifiedSettings();}
    }
}
