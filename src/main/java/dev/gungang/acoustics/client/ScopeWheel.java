package dev.gungang.acoustics.client;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While aiming down a TaCZ scope with several magnifications, the mouse wheel steps the magnification: up zooms in,
 * down zooms out, stopping at the ends. TaCZ only offers its zoom key, which cycles forward; the server side of that key
 * adds one to the scope's zoom number (the client reads it modulo the zoom count), so a step back sends count-1 presses.
 * The new number is also written to the client stack at once so quick scrolling builds on it before the server syncs.
 * Reflection only: TaCZ stays optional.
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class ScopeWheel {
    private static boolean failed;
    private static Method fromLocalPlayer,isAim,getIGun,attachmentId,attachmentTag,isEmpty,clientIndex,getZoom,getNumber,setNumber;
    private static Object scope,channel;
    private static java.lang.reflect.Constructor<?> zoomMessage;
    private static Method send;

    /** Before {@link HotbarLock} (lowest), which would otherwise swallow the wheel. */
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void scroll(InputEvent.MouseScrollingEvent event){
        var mc=Minecraft.getInstance();
        if(event.isCanceled()||mc.screen!=null||mc.player==null||mc.player.isSpectator()||event.getScrollDelta()==0||failed||!load())return;
        try{
            if(!(boolean)isAim.invoke(fromLocalPlayer.invoke(null,mc.player)))return;
            var stack=mc.player.getMainHandItem();var gun=getIGun.invoke(null,stack);if(gun==null)return;
            var id=(ResourceLocation)attachmentId.invoke(gun,stack,scope);var tag=(CompoundTag)attachmentTag.invoke(gun,stack,scope);
            if(id==null||tag==null||(boolean)isEmpty.invoke(null,id))return;
            var index=((java.util.Optional<?>)clientIndex.invoke(null,id)).orElse(null);if(index==null)return;
            var zoom=(float[])getZoom.invoke(index);if(zoom==null||zoom.length<2)return;
            event.setCanceled(true);
            int number=(int)getNumber.invoke(null,tag),current=Math.floorMod(number,zoom.length);
            int target=step(zoom,current,event.getScrollDelta()>0);
            if(target==current)return;
            int presses=Math.floorMod(target-current,zoom.length);
            for(int i=0;i<presses;i++)send.invoke(channel,zoomMessage.newInstance());
            setNumber.invoke(null,tag,(int)((number+(long)presses)%2147483646L));
        }catch(ReflectiveOperationException|RuntimeException error){failed=true;}
    }
    /** The next higher (in) or lower (out) magnification after {@code current}, or {@code current} at the end. */
    static int step(float[] zoom,int current,boolean in){
        int best=current;
        for(int i=0;i<zoom.length;i++){
            if(in?zoom[i]>zoom[current]&&(best==current||zoom[i]<zoom[best]):zoom[i]<zoom[current]&&(best==current||zoom[i]>zoom[best]))best=i;
        }
        return best;
    }
    private static boolean load(){
        if(send!=null)return true;
        try{
            var operator=Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            fromLocalPlayer=operator.getMethod("fromLocalPlayer",net.minecraft.client.player.LocalPlayer.class);isAim=operator.getMethod("isAim");
            var iGun=Class.forName("com.tacz.guns.api.item.IGun");
            var type=Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            getIGun=iGun.getMethod("getIGunOrNull",net.minecraft.world.item.ItemStack.class);
            attachmentId=iGun.getMethod("getAttachmentId",net.minecraft.world.item.ItemStack.class,type);
            attachmentTag=iGun.getMethod("getAttachmentTag",net.minecraft.world.item.ItemStack.class,type);
            scope=type.getField("SCOPE").get(null);
            isEmpty=Class.forName("com.tacz.guns.api.DefaultAssets").getMethod("isEmptyAttachmentId",ResourceLocation.class);
            clientIndex=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getClientAttachmentIndex",ResourceLocation.class);
            getZoom=Class.forName("com.tacz.guns.client.resource.index.ClientAttachmentIndex").getMethod("getZoom");
            var accessor=Class.forName("com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor");
            getNumber=accessor.getMethod("getZoomNumberFromTag",CompoundTag.class);setNumber=accessor.getMethod("setZoomNumberToTag",CompoundTag.class,int.class);
            channel=Class.forName("com.tacz.guns.network.NetworkHandler").getField("CHANNEL").get(null);
            zoomMessage=Class.forName("com.tacz.guns.network.message.ClientMessagePlayerZoom").getConstructor();
            send=channel.getClass().getMethod("sendToServer",Object.class);
            return true;
        }catch(ReflectiveOperationException|LinkageError error){failed=true;return false;}
    }
    private ScopeWheel(){}
}
