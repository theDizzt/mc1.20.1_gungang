package dev.gungang.acoustics.client;

import net.minecraft.client.gui.screens.controls.KeyBindsList;

/** Implemented by the Controls screen so a key row can prune the list it sits in. */
public interface KeyRemovalList {
    KeyBindsList gungang$keyBindsList();
}
