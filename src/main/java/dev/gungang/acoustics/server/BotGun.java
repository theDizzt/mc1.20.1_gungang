package dev.gungang.acoustics.server;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Optional TaCZ public API, resolved once, without a hard dependency or simulated hitscan damage. */
final class BotGun {
    private static Api api;
    private static boolean resolved;
    private static Method reloadState, reloadType, cancelReload;
    private record Api(Method from,Method gun,Method draw,Method shoot,Method reload,Method bolt,Method aim,Method dummy){}
    private static Api api(){
        if(!resolved){
            resolved=true;
            try{
                var operator=Class.forName("com.tacz.guns.api.entity.IGunOperator");
                var gun=Class.forName("com.tacz.guns.api.item.IGun");
                api=new Api(operator.getMethod("fromLivingEntity",LivingEntity.class),gun.getMethod("getIGunOrNull",ItemStack.class),
                        operator.getMethod("draw",Supplier.class),operator.getMethod("shoot",Supplier.class,Supplier.class),
                        operator.getMethod("reload"),operator.getMethod("bolt"),operator.getMethod("aim",boolean.class),
                        gun.getMethod("setDummyAmmoAmount",ItemStack.class,int.class));
                try {
                    reloadState=operator.getMethod("getSynReloadState");
                    reloadType=reloadState.getReturnType().getMethod("getStateType");
                    cancelReload=operator.getMethod("cancelReload");
                }catch(NoSuchMethodException unsupported){reloadState=null;reloadType=null;}
            }catch(ClassNotFoundException absent){/* Bow-only servers need no gun mod. */}
            catch(ReflectiveOperationException failure){com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Bot TaCZ API unavailable",failure);}
        }
        return api;
    }
    static boolean supports(ItemStack stack){
        try{return api()!=null&&api.gun.invoke(null,stack)!=null;}
        catch(ReflectiveOperationException failure){return false;}
    }
    private static Method count;
    private static boolean countResolved;
    private int fullest;
    /**
     * Magazine fill 0..1 as the bot would know it from its own gun: rounds left over the most it has seen loaded.
     * 1 when the TaCZ build has no ammo-count API.
     */
    float ammo(ItemStack stack) {
        if(!countResolved) {
            countResolved=true;
            try{count=Class.forName("com.tacz.guns.api.item.IGun").getMethod("getCurrentAmmoCount",ItemStack.class);}catch(ReflectiveOperationException absent){count=null;}
        }
        if(count==null||api==null)return 1;
        try {
            int rounds=(Integer)count.invoke(api.gun.invoke(null,stack),stack);
            fullest=Math.max(fullest,rounds);
            return rounds==0?0:rounds/(float)fullest;
        }catch(ReflectiveOperationException|RuntimeException failure) {return 1;}
    }
    private final Object operator;
    private final LivingEntity entity;
    private String type="";
    private BotWeaponData ballistics=BotWeaponData.UNKNOWN;
    private Method cacheProperty,cacheValue;
    private Object ammoSpeedProperty;
    private long nextBallistics;
    BotWeaponData ballistics(){
        long now=entity.level().getGameTime();
        if(cacheProperty!=null&&now>=nextBallistics) {
            nextBallistics=now+10;
            try {
                Object cache=cacheProperty.invoke(operator);
                if(cache!=null&&cacheValue.invoke(cache,ammoSpeedProperty) instanceof Number n&&n.doubleValue()>0) {
                    double speed=n.doubleValue()*BotWeaponData.speedMultiplier()/20;
                    ballistics=new BotWeaponData(ballistics.type(),speed,ballistics.gravity(),ballistics.drag(),
                            ballistics.damage(),ballistics.rpm(),ballistics.magazine(),ballistics.reloadSeconds());
                }
            }catch(ReflectiveOperationException|RuntimeException ignored){/* Base gun-pack data remains usable. */}
        }
        return ballistics;
    }
    String type(){return type;}
    private long requestedAt=Long.MIN_VALUE, retryAt;
    private boolean observedReload;
    boolean reloading() throws ReflectiveOperationException {
        long now=entity.level().getGameTime();
        if(reloadState!=null) {
            Object state=reloadState.invoke(operator);
            if(state!=null) {
                boolean active=!((Enum<?>)reloadType.invoke(state)).name().equals("NOT_RELOADING");
                if(active){observedReload=true;return true;}
                if(observedReload){observedReload=false;requestedAt=Long.MIN_VALUE;}
                // Allow the server's synchronized state a short time to acknowledge a request.
                return requestedAt!=Long.MIN_VALUE&&now-requestedAt<2;
            }
        }
        return requestedAt!=Long.MIN_VALUE&&now-requestedAt<50;
    }
    void reload() throws ReflectiveOperationException {
        long now=entity.level().getGameTime();
        if(now<retryAt||reloading())return;
        api.aim.invoke(operator,false);
        api.reload.invoke(operator);
        requestedAt=now;retryAt=now+20;observedReload=false;
    }
    void unequip() throws ReflectiveOperationException {
        stop();
        if(cancelReload!=null)cancelReload.invoke(operator);
        requestedAt=Long.MIN_VALUE;observedReload=false;
    }
    BotGun(LivingEntity entity)throws ReflectiveOperationException{this(entity,true);}
    BotGun(LivingEntity entity,boolean supply)throws ReflectiveOperationException{
        if(api()==null)throw new IllegalStateException("TaCZ bot API unavailable");
        this.entity=entity;
        operator=api.from.invoke(null,entity);
        var stack=entity.getMainHandItem();
        if(supply)api.dummy.invoke(api.gun.invoke(null,stack),stack,10000);
        api.draw.invoke(operator,(Supplier<ItemStack>)entity::getMainHandItem);
        ammo(stack);
        ballistics=BotWeaponData.read(stack);
        type=ballistics.type();
        try {
            cacheProperty=Class.forName("com.tacz.guns.api.entity.IGunOperator").getMethod("getCacheProperty");
            cacheValue=cacheProperty.getReturnType().getMethod("getCache",Class.forName("com.tacz.guns.api.GunProperty"));
            ammoSpeedProperty=Class.forName("com.tacz.guns.api.GunProperties").getField("AMMO_SPEED").get(null);
        }catch(ReflectiveOperationException ignored){cacheProperty=null;}
        try {
            var gunInterface=Class.forName("com.tacz.guns.api.item.IGun");
            Object id=gunInterface.getMethod("getGunId",ItemStack.class).invoke(api.gun.invoke(null,stack),stack);
            var index=(java.util.Optional<?>)Class.forName("com.tacz.guns.api.TimelessAPI")
                    .getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,id);
            if(index.isPresent())type=(String)index.get().getClass().getMethod("getType").invoke(index.get());
        }catch(ReflectiveOperationException unsupported){/* Older API: use the configured category. */}
    }
    String fire(float pitch,float yaw)throws ReflectiveOperationException{
        if(reloading())return "RELOADING";
        api.aim.invoke(operator,true);
        var result=api.shoot.invoke(operator,(Supplier<Float>)()->pitch,(Supplier<Float>)()->yaw);
        String state=((Enum<?>)result).name();
        if(state.equals("NO_AMMO"))reload();
        else if(state.equals("NEED_BOLT"))api.bolt.invoke(operator);
        else if(java.util.Set.of("NOT_GUN","ID_NOT_EXIST","UNKNOWN_FAIL","NOT_DRAW").contains(state))throw new IllegalStateException("TaCZ bot shoot: "+state);
        return state;
    }
    void stop()throws ReflectiveOperationException{api.aim.invoke(operator,false);}
}
