package dev.gungang.acoustics.server;

import java.util.Locale;

/** Desired combat distances in blocks. Gun-pack metadata takes precedence over lobby categories. */
record BotCombatProfile(double near,double far,double engage,float strafe,int exposureTicks) {
    static final BotCombatProfile RIFLE=new BotCombatProfile(10,20,30,.45f,30);
    double waypointCost(String kind) {
        if(kind.equals("SNIPER"))return near>=20?0:25;
        if(kind.equals("OBJECTIVE"))return far<=12?0:5;
        return near>=20?15:0;
    }
    static BotCombatProfile of(String gunType,String category) {
        String type=gunType==null?"":gunType.toLowerCase(Locale.ROOT);
        return switch(type) {
            case "shotgun"->new BotCombatProfile(3,8,14,.5f,24);
            case "smg"->new BotCombatProfile(5,12,20,.6f,30);
            case "pistol"->new BotCombatProfile(5,12,20,.45f,30);
            case "sniper","dmr"->new BotCombatProfile(20,34,46,.15f,40);
            case "mg","machine_gun"->new BotCombatProfile(12,24,34,.2f,36);
            case "rpg","launcher"->new BotCombatProfile(16,28,38,.25f,30);
            case "rifle"->RIFLE;
            default->switch(category==null?"":category.toLowerCase(Locale.ROOT)) {
                case "산탄총","shotgun"->of("shotgun","");
                case "기관단총","smg"->of("smg","");
                case "저격총","지정사수소총","sniper","dmr"->of("sniper","");
                case "피스톨","권총","pistol"->of("pistol","");
                default->RIFLE;
            };
        };
    }
}
