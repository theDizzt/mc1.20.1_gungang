package dev.gungang.acoustics.server;

import java.nio.file.*;
import java.util.List;
import com.google.gson.JsonObject;

public final class BombEquipment {
    public record Timing(double plantSeconds,double fuseSeconds,double defuseSeconds){
        public Timing{GadgetConfig.bounded(plantSeconds,.05,300);GadgetConfig.bounded(fuseSeconds,.05,3600);GadgetConfig.bounded(defuseSeconds,.05,300);}
        public int plantTicks(){return (int)Math.ceil(plantSeconds*20);}
        public int fuseTicks(){return (int)Math.ceil(fuseSeconds*20);}
        public int defuseTicks(){return (int)Math.ceil(defuseSeconds*20);}
    }
    public static Timing timing=new Timing(5,45,10);
    public static boolean reserved(Loadouts.Weapon weapon){
        return weapon.id().equals("98")||weapon.id().equals("99")||weapon.itemNbt().contains("gungang:bomb")||weapon.itemNbt().contains("gungang:defuser");
    }
    public static Loadouts.Weapon definition(boolean defuser){
        return new Loadouts.Weapon(defuser?"99":"98","{id:'gungang:"+(defuser?"defuser":"bomb")+"',Count:1b}",
                defuser?"폭탄 해체기":"폭탄","아이템으로 지급하지 않습니다. 이 파일은 시간 설정용입니다.",null,
                defuser?"방어팀 누구나 설치된 폭탄을 조준하고 E를 유지해 해체합니다.":"공격팀 누구나 A/B 영역 바닥에 서서 E를 유지해 설치합니다.","폭탄전 전용");
    }
    public static JsonObject example(boolean defuser){
        var json=ServerFiles.GSON.toJsonTree(definition(defuser)).getAsJsonObject();
        if(defuser)json.addProperty("defuseSeconds",10);
        else {json.addProperty("plantSeconds",5);json.addProperty("fuseSeconds",45);}
        return json;
    }
    public static void load(Path directory)throws java.io.IOException{
        JsonObject bomb=null,defuser=null;
        try(var files=Files.list(directory)){
            for(var path:files.filter(p->p.toString().endsWith(".json")).toList()){
                if(Files.size(path)>65536)throw new java.io.IOException("Weapon config too large: "+path);
                var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String id=json.has("id")?json.get("id").getAsString():"";
                if(id.equals("98")){if(bomb!=null)throw new IllegalArgumentException("Duplicate secondary id 98");bomb=json;}
                if(id.equals("99")){if(defuser!=null)throw new IllegalArgumentException("Duplicate secondary id 99");defuser=json;}
            }
        }
        if(bomb==null){bomb=example(false);ConfigLayout.writeNew(directory.resolve("98_bomb.json"),ServerFiles.GSON.toJson(bomb));}
        if(defuser==null){defuser=example(true);ConfigLayout.writeNew(directory.resolve("99_defuser.json"),ServerFiles.GSON.toJson(defuser));}
        timing=new Timing(value(bomb,"plantSeconds",5),value(bomb,"fuseSeconds",45),value(defuser,"defuseSeconds",10));
    }
    private static double value(JsonObject json,String key,double fallback){return json.has(key)?json.get(key).getAsDouble():fallback;}
    private BombEquipment(){}
}
