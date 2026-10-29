package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class GameOptionsScreen extends ArenaScreen {
    private LobbySnapshot state;
    private EditBox minutes,score;
    private String minuteText,scoreText,error="";
    private boolean primary,secondary;
    GameOptionsScreen(LobbySnapshot state){
        super("게임 옵션");this.state=state;var game=state.options().game();
        minuteText=game.minutes()==0?"":Integer.toString(game.minutes());scoreText=game.targetScore()==0?"":Integer.toString(game.targetScore());
        primary=game.duplicatePrimary();secondary=game.duplicateSecondary();
    }
    void update(LobbySnapshot next){state=next;}
    private boolean editable(){return state.administrator()&&state.phase().equals("LOBBY");}
    private boolean scored(){return java.util.Set.of("CONTROL","DEATHMATCH","FREE_FOR_ALL","BOMB").contains(state.selectedMode());}
    @Override protected void init(){
        int x=left()+12,w=body()-24,y=76,field=Math.min(110,w/3);
        minutes=new EditBox(font,x+w-field,y,field,18,Component.literal("커스텀 게임 시간 (분)"));
        minutes.setMaxLength(4);minutes.setFilter(s->s.matches("[0-9]*"));minutes.setValue(minuteText);minutes.setHint(Component.literal("기본값"));minutes.setEditable(editable());minutes.setResponder(s->minuteText=s);addRenderableWidget(minutes);
        score=new EditBox(font,x+w-field,y+28,field,18,Component.literal("커스텀 목표 점수"));
        score.setMaxLength(5);score.setFilter(s->s.matches("[0-9]*"));score.setValue(scoreText);score.setHint(Component.literal("기본값"));score.setEditable(editable()&&scored());score.setResponder(s->scoreText=s);addRenderableWidget(score);
        button("중복 주무기: "+(primary?"허용":"불가능"),x,y+58,w,20,editable(),ACCENT,()->{primary=!primary;rebuildWidgets();});
        button("중복 보조무기: "+(secondary?"허용":"불가능"),x,y+82,w,20,editable(),ACCENT,()->{secondary=!secondary;rebuildWidgets();});
        int bottom=Math.max(y+112,height-36),third=(w-8)/3;
        button("기본값",x,bottom,third,20,editable(),MUTED,()->{minuteText=scoreText="";primary=false;secondary=true;error="";rebuildWidgets();});
        button("적용",x+third+4,bottom,third,20,editable(),ACCENT,this::save);
        button("돌아가기",x+(third+4)*2,bottom,w-(third+4)*2,20,true,MUTED,this::onClose);
    }
    private void save(){
        try{
            var game=new GameOptions(minuteText.isEmpty()?0:Integer.parseInt(minuteText),scored()?(scoreText.isEmpty()?0:Integer.parseInt(scoreText)):0,primary,secondary);
            if(state.selectedMode().equals("PROP_HUNT")&&game.minutes()!=0&&(game.minutes()<2||game.minutes()>60))throw new IllegalArgumentException("숨바꼭질 찾는 시간: 2~60분");
            if(state.selectedMode().equals("BOMB")&&game.targetScore()>50)throw new IllegalArgumentException("폭탄전 목표 승수: 1~50");
            LobbyClient.action("gameOptions",ServerFiles.GSON.toJson(game),"");onClose();
        }catch(IllegalArgumentException e){error=e.getMessage();}
    }
    @Override public void onClose(){minecraft.setScreen(new LobbyScreen(state));LobbyClient.action("lobby","","");}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        shell(g,"빈칸 / 0 = 기존 설정 · 변경하면 참가자의 준비가 해제됩니다.");
        int x=left()+12;
        g.drawString(font,state.selectedMode().equals("PROP_HUNT")?"찾는 시간 (분, 2~60)":"게임 시간 (분)",x,81,INK,false);
        g.drawString(font,!scored()?"목표 점수: 이 모드에서는 미사용":state.selectedMode().equals("BOMB")?"목표 승수 (1~50)":"목표 점수",x,109,scored()?INK:MUTED,false);
        if(height>260)g.drawString(font,"중복 제한은 같은 팀에 적용 · 개인전 / 술래 지급 무기 제외",x,185,MUTED,false);
        if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,body()-24),x,Math.max(184,height-49),0xFFFF6666,false);
        super.render(g,mx,my,partial);
    }
}
