package dev.gungang.acoustics.server;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/**
 * Primary 38 magic book. Its mana, element (조정관) and the abilities' cooldowns live in the stack's tag, synced to the
 * holder for the HUD; every ability is server-authoritative ({@link MagicBook}). Mana regenerates on its own (no reload)
 * and from health packs. While it is carried, secondaries cannot be used.
 */
public final class MagicBookItem extends Item {
    public enum Element {
        YELLOW("황",0xFFE8C547),BLUE("청",0xFF5AB4FF),RED("적",0xFFFF5A4A),WHITE("백",0xFFF2F4F8),BLACK("흑",0xFF9A6BD8);
        public final String label;public final int color;
        Element(String label,int color){this.label=label;this.color=color;}
        public Element next(){return values()[(ordinal()+1)%values().length];}
        public Element previous(){return values()[(ordinal()+values().length-1)%values().length];}
    }
    public static final String MANA="BookMana",CAPACITY="BookCapacity",ELEMENT="BookElement",COOLDOWNS="BookCooldowns",FLYING="BookFlying",FLIGHT_SPEED="BookFlightSpeed";

    public MagicBookItem(){super(new Properties().stacksTo(1));}
    public static boolean is(ItemStack stack){return stack!=null&&stack.getItem() instanceof MagicBookItem;}
    /** Mana and cooldown updates must not lower and re-raise the held book. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){return slotChanged||oldStack.getItem()!=newStack.getItem();}
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,Level level,net.minecraft.core.BlockPos pos,Player player){return false;}
    @Override public boolean onEntitySwing(ItemStack stack,net.minecraft.world.entity.LivingEntity entity){return true;}

    public static int capacity(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(CAPACITY)?Math.max(1,stack.getTag().getInt(CAPACITY)):ExtraGadgetConfig.MAGIC_BOOK_DEFAULTS.manaCapacity();}
    public static int mana(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(MANA)?Math.max(0,stack.getTag().getInt(MANA)):capacity(stack);}
    static void setMana(ItemStack stack,int mana){stack.getOrCreateTag().putInt(MANA,Math.max(0,Math.min(capacity(stack),mana)));}
    public static Element element(ItemStack stack){
        int value=stack.hasTag()?stack.getTag().getInt(ELEMENT):0;
        return Element.values()[Math.floorMod(value,Element.values().length)];
    }
    /** Game time each ability is ready again: element ordinal × 2 + (0 left, 1 right). */
    public static long cooldown(ItemStack stack,Element element,boolean right){
        if(!stack.hasTag())return 0;var all=stack.getTag().getLongArray(COOLDOWNS);int i=element.ordinal()*2+(right?1:0);
        return i<all.length?all[i]:0;
    }
    static void setCooldown(ItemStack stack,Element element,boolean right,long until){
        var tag=stack.getOrCreateTag();var all=tag.getLongArray(COOLDOWNS);
        if(all.length<Element.values().length*2)all=java.util.Arrays.copyOf(all,Element.values().length*2);
        all[element.ordinal()*2+(right?1:0)]=until;tag.putLongArray(COOLDOWNS,all);
    }
    /** The server lets the holder fly (흑 right click held, mana left): the client moves the body. */
    public static boolean flying(ItemStack stack){return stack.hasTag()&&stack.getTag().getBoolean(FLYING);}
    public static double flightSpeed(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(FLIGHT_SPEED)?stack.getTag().getDouble(FLIGHT_SPEED):ExtraGadgetConfig.MAGIC_BOOK_DEFAULTS.flightSpeed();}

    /** Carried anywhere in the inventory: secondaries are locked while the book is the primary. */
    public static boolean carried(Player p){
        if(p==null)return false;
        if(is(p.getMainHandItem())||is(p.getOffhandItem()))return true;
        for(var stack:p.getInventory().items)if(is(stack))return true;
        return false;
    }
    public static ItemStack carriedStack(Player p){
        if(is(p.getMainHandItem()))return p.getMainHandItem();
        for(var stack:p.getInventory().items)if(is(stack))return stack;
        return ItemStack.EMPTY;
    }

    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
        if(level.isClientSide||!(entity instanceof Player||entity instanceof BotEntity))return;
        var c=ExtraGadgetConfig.magicBook;var tag=stack.getOrCreateTag();
        if(tag.getInt(CAPACITY)!=c.manaCapacity())tag.putInt(CAPACITY,c.manaCapacity());
        if(tag.getDouble(FLIGHT_SPEED)!=c.flightSpeed())tag.putDouble(FLIGHT_SPEED,c.flightSpeed());
        if(!tag.contains(MANA)||tag.getInt(MANA)>c.manaCapacity())tag.putInt(MANA,c.manaCapacity());
        // No reload: one point back every manaRegenSeconds.
        int every=Math.max(1,(int)Math.round(c.manaRegenSeconds()*20));
        if(level.getGameTime()%every==0&&tag.getInt(MANA)<c.manaCapacity())tag.putInt(MANA,tag.getInt(MANA)+1);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        lines.add(Component.literal(element(stack).label+" · "+mana(stack)+"/"+capacity(stack)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
