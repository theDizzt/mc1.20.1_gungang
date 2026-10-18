package dev.gungang.acoustics.server;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;

final class GadgetSmokeChecksArea {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(MinecraftServer server)throws Exception{
        var level=server.overworld();UUID uuid=UUID.randomUUID();String id="area_smoke_"+uuid.toString().substring(0,8);
        var p=new FakePlayer(level,new com.mojang.authlib.GameProfile(uuid,"AreaEditorTest")){@Override public boolean hasPermissions(int n){return true;}};
        var q=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AreaHudClone"));
        Path file=ServerFiles.root().resolve("server/maps/"+id+".json"),history=ServerFiles.root().resolve("editor-undo/"+uuid+".json");
        var saved=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try{
            for(var pos:BlockPos.betweenClosed(600,239,600,604,244,604)){saved.put(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,pos.getY()==239?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
            p.setPos(602.5,240,602.5);var dispatcher=server.getCommands().getDispatcher();var source=p.createCommandSourceStack().withPermission(2);
            check(dispatcher.execute("gungang hud off",source)==1&&!HudPreferences.enabled(p),"HUD off command and persisted state");
            HudPreferences.clone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(q,p,true));check(!HudPreferences.enabled(q),"HUD survives player clone");
            check(dispatcher.execute("gungang hud on",source)==1&&HudPreferences.enabled(p),"HUD on command");
            check(dispatcher.execute("gungang area wand",source)==1&&p.getInventory().contains(new net.minecraft.world.item.ItemStack(GadgetItems.SPATIAL_WAND.get())),"Wand command");
            check(dispatcher.execute("gungang area pos1 604 243 604",source)==1,"First selection command");
            check(dispatcher.execute("gungang area pos2 600 240 600",source)==1,"Second selection command");
            check(SpatialWand.size(SpatialWand.selection(p).bounds()).contains("5 × 세로(Z) 5 × 높이(Y) 4"),"Inclusive reverse-corner dimensions");
            check(dispatcher.execute("gungang area save "+id+" map",source)==1,"Create map command");
            var map=ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).validate();
            check(!map.enabled()&&map.previewArea().minX()==600&&map.previewArea().maxX()==604&&map.spectatorSpawn().y()==246,"Preview and spectator registered, new map disabled");
            var custom=JsonParser.parseString(Files.readString(file)).getAsJsonObject();custom.addProperty("customSetting","preserve");AreaEditor.atomicWrite(file,ServerFiles.GSON.toJson(custom));
            AreaEditor.save(p,id,"alpha",0);map=ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).validate();
            check(map.alphaSpawn().x()==602.5&&map.alphaSpawn().y()==240&&map.alphaArea().maxX()==605,"Safe spawn center and exclusive volume bounds");
            check(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("customSetting").getAsString().equals("preserve"),"Unrelated JSON preserved");
            AreaEditor.save(p,id,"bravo",0);String before=Files.readString(file);
            AreaEditor.save(p,id,"deathzone",0);map=ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).validate();check(map.deathZones().size()==1,"Death zone saved");
            check(dispatcher.execute("gungang area undo",source)==1&&Files.readString(file).equals(before),"Undo restores exact previous bytes");
            AreaEditor.save(p,id,"capture",0);check(ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).capture()!=null,"Capture registered");
            String after=Files.readString(file);Files.writeString(file,after+" ");boolean conflict=false;try{AreaEditor.undo(p);}catch(IllegalStateException expected){conflict=true;}check(conflict&&Files.readString(file).equals(after+" "),"Undo refuses external changes");Files.writeString(file,after);AreaEditor.undo(p);
            SpatialWand.select(p,new BlockPos(600,244,600),true);SpatialWand.select(p,new BlockPos(604,244,604),false);String unchanged=Files.readString(file);
            check(dispatcher.execute("gungang area save "+id+" alpha",source)==0&&Files.readString(file).equals(unchanged),"Invalid spawn rejected without changing file");
            SpatialWand.select(p,new BlockPos(600,240,600),true);SpatialWand.select(p,new BlockPos(604,243,604),false);
            var bomb=ServerFiles.GSON.toJsonTree(BombRules.example()).getAsJsonObject();bomb.addProperty("id",id);AreaEditor.atomicWrite(file,ServerFiles.GSON.toJson(bomb));AreaEditor.save(p,id,"alpha",0);AreaEditor.save(p,id,"bomb_a",0);
            map=ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).validate();check(map.bomb().attackSpawn().x()==602.5&&map.bomb().areaA().maxX()==604,"Bomb team aliases and inclusive site");
            var escort=ServerFiles.GSON.toJsonTree(EscortRules.example()).getAsJsonObject();escort.addProperty("id",id);AreaEditor.atomicWrite(file,ServerFiles.GSON.toJson(escort));AreaEditor.save(p,id,"escort",0);AreaEditor.save(p,id,"escort_alpha",2);
            map=ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).validate();check(map.escort().scanArea().maxX()==604&&map.escort().respawns().stream().anyMatch(r->r.checkpoint()==2&&r.alpha().x()==602.5),"Escort scan and checkpoint respawn");
            var normandy=ServerFiles.GSON.toJsonTree(NormandyRules.example()).getAsJsonObject();normandy.addProperty("id",id);AreaEditor.atomicWrite(file,ServerFiles.GSON.toJson(normandy));AreaEditor.save(p,id,"normandy",1);AreaEditor.save(p,id,"normandy_spawn",1);AreaEditor.save(p,id,"attack_launch",0);
            map=ServerFiles.GSON.fromJson(Files.readString(file),ArenaMap.class).validate();check(map.normandy().points().get(0).radius()==2.5&&map.normandy().attackLaunch().x()==602.5,"Normandy points and launch");
            check(dispatcher.execute("gungang area enable "+id+" true",source)==1,"Enable edited map");
            var phase=LobbyManager.class.getDeclaredField("phase");phase.setAccessible(true);Object old=phase.get(null);try{phase.set(null,"RUNNING");unchanged=Files.readString(file);check(dispatcher.execute("gungang area save "+id+" deathzone",source)==0&&Files.readString(file).equals(unchanged),"Match edits blocked");}finally{phase.set(null,old);}
            System.out.println("GUNGANG_AREA_SMOKE_PASS command selection, HUD persistence, map/spawns/objectives, exact undo, conflict/invalid/live-match protection");
        }finally{saved.forEach((pos,state)->level.setBlock(pos,state,3));Files.deleteIfExists(file);Files.deleteIfExists(history);SpatialWand.clear(p);LobbyManager.refreshEditedMaps();}
    }
}
