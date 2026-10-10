package dev.gungang.acoustics.server;

final class LoadoutRules {
    static boolean allowed(String phase,boolean alive,boolean teamParticipant,boolean inSpawn) {
        if(!alive||phase.equals("FINISHING"))return false;
        return phase.equals("LOBBY") || (teamParticipant && (phase.equals("PREPARING") || (phase.equals("RUNNING") && inSpawn)));
    }
    private LoadoutRules() {}
}
