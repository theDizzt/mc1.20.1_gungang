package dev.gungang.acoustics.server;

/** Datapack capture rates integrated every tick, with double-speed neutralization. */
public final class CaptureGame {
    private double capture;
    private int alphaScore, bravoScore, scoreClock = 20;
    public void tick(boolean alphaPresent, boolean bravoPresent, int stepTicks, int scoreTicks) {
        double step = 1.0 / Math.max(1,stepTicks);
        if (alphaPresent && !bravoPresent) capture = capture < 0 ? Math.min(0,capture+2*step) : capture+step;
        else if (bravoPresent && !alphaPresent) capture = capture > 0 ? Math.max(0,capture-2*step) : capture-step;
        capture = Math.max(-100,Math.min(100,capture));
        if (Math.abs(capture)>100-1e-8) capture=Math.copySign(100,capture);
        if (--scoreClock <= 0) {
            scoreClock = scoreTicks;
            if (capture == 100) alphaScore++;
            if (capture == -100) bravoScore++;
        }
    }
    public float progress() { return (float)capture; }
    public int scoringOwner() { return capture == 100 ? 1 : capture == -100 ? -1 : 0; }
    public int alphaScore() { return alphaScore; }
    public int bravoScore() { return bravoScore; }
    public String winner(int goal) { return alphaScore >= goal ? "ALPHA" : bravoScore >= goal ? "BRAVO" : ""; }
    public String leader() { return alphaScore > bravoScore ? "ALPHA" : bravoScore > alphaScore ? "BRAVO" : "DRAW"; }
}
