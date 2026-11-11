package dev.gungang.acoustics.server;

/** Immutable shot values: spending charge once fixes both collision and damage for that shot. */
public final class M95ShotRules {
    public record Shot(double cost,int blocks,double hitPadding,boolean ignoresFalloff) { }
    public static Shot select(double available,ExtraGadgetConfig.M95 c){
        if(available>=c.unlimitedThreshold())return new Shot(c.unlimitedThreshold(),Integer.MAX_VALUE,c.unlimitedHitPadding(),!c.unlimitedDamageFalloff());
        if(available>=c.thirdThreshold())return new Shot(c.thirdThreshold(),c.thirdBlocks(),c.thirdHitPadding(),!c.thirdDamageFalloff());
        if(available>=c.largeThreshold())return new Shot(c.largeThreshold(),c.largeBlocks(),c.largeHitPadding(),!c.largeDamageFalloff());
        if(available>=c.smallThreshold())return new Shot(c.smallThreshold(),c.smallBlocks(),c.smallHitPadding(),!c.smallDamageFalloff());
        return new Shot(0,0,0,false);
    }
    private M95ShotRules(){}
}
