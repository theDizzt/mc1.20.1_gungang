package dev.gungang.acoustics.server;

import java.util.ArrayList;

/** Personal, short-lived memory of where damage was taken, not where an unseen attacker is. */
final class BotRouteMemory {
    static final int LIFETIME=600, LIMIT=24;
    private record Hit(double x,double y,double z,double weight,int at) { }
    private final ArrayList<Hit> hits=new ArrayList<>();
    void clear(){hits.clear();}
    void hit(double x,double y,double z,double damageFraction,int now) {
        if(!Double.isFinite(damageFraction)||damageFraction<=0)return;
        hits.removeIf(h->now-h.at()>=LIFETIME);
        double weight=Math.min(3,1+damageFraction*5);
        for(int i=0;i<hits.size();i++) {
            var h=hits.get(i);double dx=x-h.x(),dz=z-h.z();
            if(dx*dx+dz*dz<9&&Math.abs(y-h.y())<3) {
                weight=Math.min(6,weight+h.weight()*Math.max(0,1-(now-h.at())/(double)LIFETIME));
                hits.remove(i);break;
            }
        }
        if(hits.size()>=LIMIT)hits.remove(0);
        hits.add(new Hit(x,y,z,weight,now));
    }
    double risk(double x,double y,double z,int now) {
        double risk=0;
        for(var h:hits) {
            int age=now-h.at();if(age<0||age>=LIFETIME||Math.abs(y-h.y())>=3)continue;
            double dx=x-h.x(),dz=z-h.z(),distance=dx*dx+dz*dz;
            if(distance<36)risk=Math.max(risk,h.weight()*(1-age/(double)LIFETIME)*(1-distance/36));
        }
        return risk;
    }
}
