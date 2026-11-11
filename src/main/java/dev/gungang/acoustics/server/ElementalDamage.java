package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Per-weapon elemental damage (weapon JSON {@code elements}), replacing the old Skript rules:
 * <ul>
 * <li>{@code burnDamage}, {@code witherDamage}, {@code poisonDamage}: damage of each burning / wither / poison tick on a
 * victim this weapon hit, while that status lasts;</li>
 * <li>{@code bleedDamage} / {@code bleedSeconds}: a hit makes the victim bleed ({@link Bleeding});</li>
 * <li>{@code explosionMultiplier}: scales this weapon's explosion damage to players and bots, on top of the global
 * {@link ExtraGadgetConfig.Combat#explosionPlayerMultiplier()} every explosion gets.</li>
 * </ul>
 * The weapon is recognised from what dealt the damage: a TaCZ bullet's gun id, an lrtactical throwable's id or the
 * GunGang item behind a landmine / ordnance. Item effect settings of their own (molotov, jetpack burnDamage) win.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class ElementalDamage {
    enum Element { BURN, WITHER, POISON }
    private record Mark(double damage,long at) { }
    /** Victim → element → the per-tick damage of the weapon that last hit it. */
    private static final Map<LivingEntity,EnumMap<Element,Mark>> MARKS=new WeakHashMap<>();
    /** Marks older than this are ignored even while the status still runs. */
    private static final long MARK_TICKS=20*60;
    private static Loadouts indexed;
    private static Map<String,Loadouts.Elements> index=Map.of();

    /** Weapon key: "gun:&lt;GunId&gt;", "throw:&lt;ThrowableId&gt;", or the item id. */
    static String key(ItemStack stack){
        if(stack==null||stack.isEmpty())return null;
        var tag=stack.getTag();
        if(tag!=null&&tag.contains("GunId"))return "gun:"+tag.getString("GunId");
        if(tag!=null&&tag.contains("ThrowableId"))return "throw:"+tag.getString("ThrowableId");
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id==null?null:id.toString();
    }
    private static String key(Entity direct){
        if(direct==null)return null;
        if(direct instanceof Las5Damage.BulletAccess bullet)return bullet.getGunId()==null?null:"gun:"+bullet.getGunId();
        if(direct instanceof DeployableEntity)return "gungang:landmine";
        if(direct instanceof OrdnanceEntity ordnance)return ordnance.orbital()?"gungang:orbital_strike":"gungang:molotov";
        if(direct instanceof net.minecraft.world.entity.projectile.ItemSupplier supplier)return key(supplier.getItem());
        // lrtactical throwables (84 grenade) carry their item without implementing ItemSupplier.
        if(direct instanceof net.minecraft.world.entity.projectile.Projectile)try{
            if(direct.getClass().getMethod("getItem").invoke(direct) instanceof ItemStack stack)return key(stack);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError none){}
        return null;
    }
    private static Loadouts.Elements elements(Entity direct){
        String key=key(direct);if(key==null)return null;
        var current=LobbyManager.loaded();
        if(current!=indexed){
            var next=new HashMap<String,Loadouts.Elements>();
            if(current!=null)for(var list:List.of(current.primary(),current.secondary()))for(var w:list){
                if(w.elements()==null)continue;
                try{String k=key(w.stack());if(k!=null)next.putIfAbsent(k,w.elements());}catch(RuntimeException invalid){}
            }
            index=next;indexed=current;
        }
        return index.get(key);
    }
    /** The per-tick damage a weapon mark gives this status on the victim, or -1 (none: vanilla damage). */
    static double markDamage(LivingEntity victim,Element element){
        var marks=MARKS.get(victim);var mark=marks==null?null:marks.get(element);
        return mark==null||victim.level().getGameTime()-mark.at()>MARK_TICKS?-1:mark.damage();
    }
    private static boolean hurtsLikePlayer(LivingEntity victim){return victim instanceof Player||victim instanceof BotEntity;}

    @SubscribeEvent(priority=EventPriority.HIGH) public static void hurt(LivingHurtEvent event){
        var victim=event.getEntity();if(victim.level().isClientSide)return;
        var source=event.getSource();
        if(source.is(DamageTypeTags.IS_EXPLOSION)){
            if(!hurtsLikePlayer(victim))return;
            // Every explosion hurts people by the global multiplier (전역 · 전투); a weapon's own multiplier stacks on it.
            double multiplier=ExtraGadgetConfig.combat.explosionPlayerMultiplier();
            var e=elements(source.getDirectEntity());
            if(e!=null&&e.explosionMultiplier()!=null)multiplier*=e.explosionMultiplier();
            if(multiplier!=1)event.setAmount((float)(event.getAmount()*multiplier));
            return;
        }
        Element element=source.is(DamageTypes.ON_FIRE)?victim.isOnFire()&&!AdrenalineDamage.burnActive(victim)?Element.BURN:null
                :source.is(DamageTypes.WITHER)?victim.hasEffect(MobEffects.WITHER)?Element.WITHER:null
                :(source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC))&&source.getEntity()==null?victim.hasEffect(MobEffects.POISON)?Element.POISON:null:null;
        if(element==null)return;
        var marks=MARKS.get(victim);var mark=marks==null?null:marks.get(element);
        if(mark==null||victim.level().getGameTime()-mark.at()>MARK_TICKS)return;
        event.setAmount((float)mark.damage());
    }
    /** A weapon hit remembers the weapon's status damage on the victim. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent event){
        if(event.isCanceled()||event.getAmount()<=0)return;
        var victim=event.getEntity();if(victim.level().isClientSide)return;
        var e=elements(event.getSource().getDirectEntity());if(e==null)return;
        long now=victim.level().getGameTime();
        var marks=MARKS.computeIfAbsent(victim,v->new EnumMap<>(Element.class));
        if(e.burnDamage()!=null)marks.put(Element.BURN,new Mark(e.burnDamage(),now));
        if(e.witherDamage()!=null)marks.put(Element.WITHER,new Mark(e.witherDamage(),now));
        if(e.poisonDamage()!=null)marks.put(Element.POISON,new Mark(e.poisonDamage(),now));
        if(e.bleedDamage()!=null)Bleeding.apply(victim,event.getSource().getEntity() instanceof LivingEntity attacker?attacker:null,ExtraGadgetConfig.ticks(e.bleedTime()),e.bleedDamage());
    }
    @SubscribeEvent public static void died(net.minecraftforge.event.entity.living.LivingDeathEvent event){MARKS.remove(event.getEntity());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){MARKS.clear();indexed=null;index=Map.of();}
    private ElementalDamage(){}
}
