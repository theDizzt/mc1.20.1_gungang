package dev.gungang.acoustics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Small, texture-independent pixel emblems: readable at every GUI scale. */
final class MissionButton extends Button {
    static final java.util.List<String> MODES=java.util.List.of("CONTROL","DEATHMATCH","BOMB","ESCORT","FREE_FOR_ALL","NORMANDY","PROP_HUNT");
    private final String mode;
    private final boolean selected,supported;
    MissionButton(int x,int y,int size,String mode,String name,boolean selected,boolean supported,boolean editable,Runnable action){
        super(x,y,size,size,Component.literal(name),b->action.run(),DEFAULT_NARRATION);
        this.mode=mode;this.selected=selected;this.supported=supported;active=supported&&editable;
        setTooltip(Tooltip.create(Component.literal(name+(supported?(selected?" · 선택됨":editable?"":" · 관리자만 변경 가능"):" · 이 맵에서 지원하지 않음"))));
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        int color=!supported?0xFF454740:selected?ArenaScreen.ACCENT:active?ArenaScreen.INK:ArenaScreen.MUTED;
        int edge=selected?ArenaScreen.ACCENT:active&&isHoveredOrFocused()?ArenaScreen.INK:ArenaScreen.BORDER;
        TacticalStyle.surface(g,getX(),getY(),width,height,!supported?0xFF151611:0xFF292C22,ArenaScreen.PANEL,edge);
        if(selected)g.fill(getX()+3,getY()+height-3,getX()+width-3,getY()+height-2,color);
        var pixels=switch(mode){
            case "CONTROL"->new String[]{"..##........","..########..","..##....##..","..##....##..","..########..","..##........","..##........","..##........","..##........",".######.....",".######.....","............"};
            case "DEATHMATCH"->new String[]{"##........##","###......###",".###....###.","..###..###..","...######...","....####....","...######...","..###..###..",".###....###.","###......###","##........##","............"};
            case "BOMB"->new String[]{".......##.#.","......##.#..",".....##.....","....####....","..########..",".##########.",".##..######.",".##..######.",".##########.","..########..","...######...","............"};
            case "ESCORT"->new String[]{"......##....","......###...","..########..","..#########.","..########..","..##..###...","##########..","##......##..","##########..","..##..##....","..##..##....","............"};
            case "FREE_FOR_ALL"->new String[]{"....####....","...######...","...######...","....####....",".....##.....","..########..",".##########.",".##.####.##.","....####....","....#..#....","...##..##...","............"};
            case "NORMANDY"->new String[]{".....##.....",".....##.....","..##.##.##..","..########..",".....##.....","##...##...##","###..##..###",".##########.","..########..","....####....",".....##.....","............"};
            default->new String[]{"....####....","..##....##..",".##......##.","##..#..#..##","##........##",".##..##..##.","..##....##..","...######...","....####....","...##..##...","..##....##..","............"};
        };
        float scale=Math.max(1,(width-8)/12f);g.pose().pushPose();
        g.pose().translate(getX()+(width-12*scale)/2,getY()+(height-12*scale)/2,0);g.pose().scale(scale,scale,1);
        for(int y=0;y<pixels.length;y++)for(int x=0;x<pixels[y].length();x++)if(pixels[y].charAt(x)=='#')g.fill(x,y,x+1,y+1,color);
        g.pose().popPose();
    }
}
