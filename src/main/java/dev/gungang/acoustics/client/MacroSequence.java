package dev.gungang.acoustics.client;

/** Timing independent of Minecraft: delayed input, one completed action, bounded hold/retry. */
public final class MacroSequence {
    public enum Step { WAIT, ACT, HOLD, RESTORE }
    private int ticks;
    private boolean done;
    public Step next(boolean keepUsing) {
        if (++ticks > 1200) return Step.RESTORE;
        if (ticks <= 2) return Step.WAIT;
        if (!done) return ticks > 100 ? Step.RESTORE : Step.ACT;
        return keepUsing ? Step.HOLD : Step.RESTORE;
    }
    public void actionDone() { done = true; }
}
