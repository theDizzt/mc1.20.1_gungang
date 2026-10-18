package dev.gungang.acoustics.server;

import net.minecraft.world.item.Item;

/** Legacy bomb/defuser items, kept registered for old worlds. They are no longer issued: planting and defusing use E (BombMatch.key). */
public final class BombItem extends Item {
    public final boolean defuser;
    public BombItem(boolean defuser){super(new Properties().stacksTo(1));this.defuser=defuser;}
}
