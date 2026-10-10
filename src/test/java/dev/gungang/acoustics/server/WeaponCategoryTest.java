package dev.gungang.acoustics.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponCategoryTest {
    @Test void existingJsonWithoutCategoryStillLoads() {
        var weapon=ServerFiles.GSON.fromJson("{\"id\":\"old\",\"name\":\"Old weapon\"}",Loadouts.Weapon.class);
        assertEquals("미분류",weapon.category());
        assertEquals("미분류",ServerFiles.GSON.fromJson("{\"category\":\"  \"}",Loadouts.Weapon.class).category());
    }
    @Test void categoryIsPreservedInClientViewAndJsonRoundTrip() {
        var weapon=new Loadouts.Weapon("rifle","full private NBT","Rifle","",null,"","  돌격소총  ");
        assertEquals("돌격소총",weapon.category());
        var view=ServerFiles.GSON.fromJson(ServerFiles.GSON.toJson(weapon.view()),Loadouts.Weapon.class);
        assertEquals("돌격소총",view.category());assertEquals("",view.itemNbt());
        assertEquals("미분류",new Loadouts.Weapon("legacy","","Name","",null,"").category());
    }
}
