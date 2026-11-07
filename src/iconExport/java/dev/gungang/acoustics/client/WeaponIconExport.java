package dev.gungang.acoustics.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.gungang.acoustics.GungangAcoustics;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Development-only export (-PgungangIconExport): renders every registered primary and secondary weapon's inventory
 * (GUI) icon, exactly as the game draws it, onto a transparent 256×256 canvas and saves it under design/weapon-icons.
 * Reads the live server weapon files; needs TaCZ, LR Tactical, the tactical bow and the server's gun packs loaded.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class WeaponIconExport {
    private static final int SIZE=256;
    private static int ticks;private static boolean done;
    private static final Path ROOT=Path.of(System.getProperty("gungang.iconRoot","../.."));
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||done)return;
        var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
        // Gun packs finish their client index shortly after the first resource load.
        if(++ticks<60)return;
        done=true;
        try{
            int count=0,missing=0;
            for(String[] kind:new String[][]{{"weapons","primary"},{"sub_weapons","secondary"}}){
                var out=ROOT.resolve("design/weapon-icons/"+kind[1]);Files.createDirectories(out);
                try(var files=Files.list(ROOT.resolve("server_resource/config/gungang/server/"+kind[0]))){
                    for(var file:files.filter(p->p.toString().endsWith(".json")).sorted().toList()){
                        var json=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                        String id=json.get("id").getAsString(),name=json.has("name")?json.get("name").getAsString():id;
                        var stack=ItemStack.of(net.minecraft.nbt.TagParser.parseTag(json.get("itemNbt").getAsString()));
                        stack.setCount(1);
                        if(stack.isEmpty()){System.out.println("ICON_SKIP "+id+" "+name+" (unknown item)");missing++;continue;}
                        if(mc.getItemRenderer().getModel(stack,null,null,0)==mc.getModelManager().getMissingModel()){System.out.println("ICON_MISSING_MODEL "+id+" "+name);missing++;}
                        var target=out.resolve(id+"_"+name.replaceAll("[\\\\/:*?\"<>|]","_").trim()+".png");
                        render(mc,stack,target);count++;
                    }
                }
            }
            System.out.println("WEAPON_ICON_EXPORT_DONE count="+count+" missing="+missing);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("WEAPON_ICON_EXPORT_FAIL");}
        finally{mc.stop();}
    }
    private static void render(Minecraft mc,ItemStack stack,Path file)throws java.io.IOException{
        var target=new TextureTarget(SIZE,SIZE,true,Minecraft.ON_OSX);
        try{
            target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            var projection=RenderSystem.getProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,SIZE,SIZE,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
            var view=RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();view.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
            g.pose().pushPose();g.pose().scale(SIZE/16f,SIZE/16f,1);g.renderItem(stack,0,0);g.pose().popPose();g.flush();
            view.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,VertexSorting.ORTHOGRAPHIC_Z);
            try(var image=new NativeImage(SIZE,SIZE,false)){
                RenderSystem.bindTexture(target.getColorTextureId());image.downloadTexture(0,false);image.flipY();image.writeToFile(file);
            }
        }finally{target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);}
    }
    private WeaponIconExport(){}
}
