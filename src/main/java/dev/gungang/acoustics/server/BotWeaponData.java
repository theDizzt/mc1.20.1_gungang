package dev.gungang.acoustics.server;

import net.minecraft.world.item.ItemStack;

/** Optional TaCZ metadata; unknown packs retain explicit, conservative defaults. No hard mod dependency. */
record BotWeaponData(String type,double speed,double gravity,double drag,double damage,double rpm,int magazine,double reloadSeconds) {
    static final BotWeaponData UNKNOWN=new BotWeaponData("",15,0,1,0,0,0,0);
    static final BotWeaponData BOW=new BotWeaponData("bow",1.8,.05,.99,10,40,1,0);
    static BotWeaponData read(ItemStack stack) {
        try {
            var gunClass=Class.forName("com.tacz.guns.api.item.IGun");
            Object gun=gunClass.getMethod("getIGunOrNull",ItemStack.class).invoke(null,stack);
            if(gun==null)return UNKNOWN;
            Object id=gunClass.getMethod("getGunId",ItemStack.class).invoke(gun,stack);
            var index=(java.util.Optional<?>)Class.forName("com.tacz.guns.api.TimelessAPI")
                    .getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,id);
            if(index.isEmpty())return UNKNOWN;
            Object common=index.get(),data=call(common,"getGunData"),bullet=call(common,"getBulletData");
            String type=(String)call(common,"getType");
            double reload=0;
            try {reload=number(call(call(data,"getReloadData"),"getFeed"),"getEmptyTime");}catch(ReflectiveOperationException ignored){}
            return new BotWeaponData(type,Math.max(.05,number(bullet,"getSpeed")*speedMultiplier()/20),
                    Math.max(0,number(bullet,"getGravity")),Math.max(0,Math.min(1,1-number(bullet,"getFriction"))),
                    number(bullet,"getDamageAmount")*Math.max(1,number(bullet,"getBulletAmount")),
                    number(data,"getRoundsPerMinute"),(int)number(data,"getAmmoAmount"),reload);
        }catch(ReflectiveOperationException|RuntimeException absent){return UNKNOWN;}
    }
    static double speedMultiplier() {
        try {
            Object config=Class.forName("com.tacz.guns.config.common.AmmoConfig").getField("GLOBAL_BULLET_SPEED_MODIFIER").get(null);
            return ((Number)call(config,"get")).doubleValue();
        }catch(ReflectiveOperationException|RuntimeException absent){return 1;}
    }
    private static Object call(Object value,String method)throws ReflectiveOperationException{return value.getClass().getMethod(method).invoke(value);}
    private static double number(Object value,String method)throws ReflectiveOperationException{return ((Number)call(value,method)).doubleValue();}
}
