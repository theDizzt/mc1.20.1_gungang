package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the running mode asks of one team, as a bot reads it: where the mission is and how it stands. Built only from
 * what a player sees on the HUD (owner, progress, contested), never from hidden enemy positions. Modes without a
 * mission give none, and the bot plays as in team deathmatch.
 *
 * @param area box around the zone; @param radius when above 0 the zone is a sphere around {@code centre} (이동점령
 * points, counted by feet distance), else the box itself (쟁탈전); @param guardAll every bot defends a quiet zone
 * instead of half the team; @param act what to do there besides being there (폭탄전 plant/defuse), at {@code target}
 */
record BotObjective(String label,AABB area,Vec3 centre,double radius,Stance stance,boolean guardAll,String doing,Act act,BlockPos target) {
    /** TAKE: not ours yet. HOLD: ours and quiet. CONTEST: enemies are on it or taking it from us. */
    enum Stance {TAKE,HOLD,CONTEST}
    /** PLANT: hold the bomb key standing on the site floor. DEFUSE: hold it facing the planted bomb, when safe. */
    enum Act {NONE,PLANT,DEFUSE}
    /** Standing in the zone the way the mode counts presence. */
    boolean contains(AABB body){return radius>0?contains(new Vec3((body.minX+body.maxX)/2,body.minY,(body.minZ+body.maxZ)/2)):body.intersects(area);}
    boolean contains(Vec3 feet){
        if(radius>0)return feet.distanceToSqr(centre)<=radius*radius;
        return feet.x>area.minX&&feet.x<area.maxX&&feet.z>area.minZ&&feet.z<area.maxZ&&feet.y>area.minY-1.8&&feet.y<area.maxY;
    }
    Vec3 center(){return centre;}

    /** 쟁탈전 point state for {@code team}: progress +100 is ALPHA's, -100 BRAVO's. */
    static BotObjective capture(String name,AABB area,String team,float progress,boolean alphaPresent,boolean bravoPresent){
        int ours=team.equals("ALPHA")?1:-1;
        boolean mine=progress*ours>=100,enemyOn=ours>0?bravoPresent:alphaPresent;
        Stance stance=enemyOn?Stance.CONTEST:mine?Stance.HOLD:Stance.TAKE;
        Vec3 centre=area==null?Vec3.ZERO:new Vec3((area.minX+area.maxX)/2,area.minY,(area.minZ+area.maxZ)/2);
        return new BotObjective(name,area,centre,0,stance,false,switch(stance){case TAKE->"점령";case HOLD->"방어";case CONTEST->"탈환·교전";},Act.NONE,null);
    }
    /**
     * 이동점령 (BRAVO attacks, ALPHA defends) current point. Attackers always take it, contested while defenders stand
     * on it (that freezes the capture). Defenders guard it, all of them, and contest it the moment attackers step on.
     */
    static BotObjective assault(String name,Vec3 centre,double radius,String team,boolean attackersOn,boolean defendersOn){
        boolean attacking=team.equals("BRAVO");
        Stance stance=attacking?(defendersOn?Stance.CONTEST:Stance.TAKE):attackersOn?Stance.CONTEST:Stance.HOLD;
        return new BotObjective(name+" 거점",new AABB(centre,centre).inflate(radius),centre,radius,stance,true,
                switch(stance){case TAKE->"공격";case HOLD->"방어";case CONTEST->attacking?"공격·교전":"저지·교전";},Act.NONE,null);
    }
    /**
     * 호위 cart (ALPHA escorts, BRAVO stops it), a zone moving with the cart. Escorts push while no defender is near
     * and must keep touching it in overtime; defenders stop it by standing near it, and alone at the cart push it
     * back (never past the last checkpoint, where there is nothing to push and they wait).
     */
    static BotObjective escort(Vec3 cart,double radius,String team,boolean escortsOn,boolean defendersOn,boolean backward){
        return escort(cart,radius,team,escortsOn,defendersOn,backward,false);
    }
    /**
     * {@code botsOnly}: the defenders are all bots. With no player to make calls, they rally on the cart at the
     * highest objective priority (instead of drifting after sounds while it is quiet) and guard it together.
     */
    static BotObjective escort(Vec3 cart,double radius,String team,boolean escortsOn,boolean defendersOn,boolean backward,boolean botsOnly){
        boolean escorting=team.equals("ALPHA");
        if(!escorting&&botsOnly)
            return new BotObjective("수레",new AABB(cart,cart).inflate(radius),cart,radius,Stance.CONTEST,true,
                    escortsOn?"수레 저지":backward?"수레 밀어내기":"수레 사수",Act.NONE,null);
        Stance stance=escorting?(defendersOn?Stance.CONTEST:Stance.TAKE):escortsOn?Stance.CONTEST:backward?Stance.TAKE:Stance.HOLD;
        String doing=escorting?(stance==Stance.CONTEST?"수레 교전":"수레 호위"):switch(stance){case CONTEST->"수레 저지";case TAKE->"수레 밀어내기";case HOLD->"수레 앞 대기";};
        return new BotObjective("수레",new AABB(cart,cart).inflate(radius),cart,radius,stance,true,doing,Act.NONE,null);
    }
}
