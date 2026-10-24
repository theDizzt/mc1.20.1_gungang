package dev.gungang.acoustics.server;

/** Bounded discrete flight estimate, with move-then-drag/gravity matching projectiles. */
final class BotBallistics {
    record Flight(double ticks,double drop){}
    static Flight flight(double distance,double speed,double gravity,double drag) {
        if(speed<=0||!Double.isFinite(speed))return new Flight(0,0);
        double travelled=0,fall=0,vertical=0;
        for(int tick=0;tick<80;tick++) {
            if(travelled+speed>=distance) {
                double fraction=Math.max(0,(distance-travelled)/speed);
                return new Flight(tick+fraction,fall+vertical*fraction);
            }
            travelled+=speed;fall+=vertical;speed*=drag;vertical=vertical*drag+gravity;
        }
        return new Flight(80,fall);
    }
    private BotBallistics(){}
}
