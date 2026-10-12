import os, json, math, random, uuid, base64, io, zipfile, copy
from pathlib import Path
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import trimesh

ROOT=Path('/mnt/data/combat_table_pack')
ASSET=ROOT/'forge_resources'/'assets'/'combattable'
MODELS=ASSET/'models'/'block'
TEXTURES=ASSET/'textures'/'block'
for p in [ROOT,MODELS,TEXTURES]:p.mkdir(parents=True,exist_ok=True)
rng=random.Random(73371)
S=512; MAPSIZE=320; CELL=32
atlas=Image.new('RGBA',(S,S),(0,0,0,255)); d=ImageDraw.Draw(atlas)

# Terrain parchment map, deliberately procedural so this is an editable original asset.
mapimg=Image.new('RGB',(MAPSIZE,MAPSIZE),(207,184,131))
mp=ImageDraw.Draw(mapimg)
for y in range(0,MAPSIZE,4):
 for x in range(0,MAPSIZE,4):
  nx=(x-150)/160; ny=(y-145)/155
  n=math.sin(x*.043)*.18+math.cos(y*.049)*.16+math.sin((x+y)*.021)*.12+rng.random()*.32
  if n>.38: c=(126+rng.randrange(-12,18),151+rng.randrange(-9,22),83+rng.randrange(-10,14))
  elif n>.23: c=(172+rng.randrange(-12,14),170+rng.randrange(-10,10),111+rng.randrange(-10,14))
  elif n>.11: c=(190+rng.randrange(-9,12),178+rng.randrange(-10,13),126+rng.randrange(-8,13))
  else:c=(207+rng.randrange(-6,9),185+rng.randrange(-6,8),135+rng.randrange(-7,9))
  mp.rectangle((x,y,x+3,y+3),fill=c)
# Water coastline/river with blocky pixelated banks
river=[]
for yy in range(-8,332,5):
 xx=int(88+52*math.sin(yy*.019+1)+28*math.sin(yy*.042))
 river.append((xx,yy))
mp.line(river,fill=(96,145,155),width=22,joint='curve')
mp.line(river,fill=(74,126,157),width=13,joint='curve')
mp.line([(x+3,y) for x,y in river],fill=(115,169,184),width=4,joint='curve')
# Lakes
mp.ellipse((155,181,234,237),fill=(76,132,165),outline=(158,182,164),width=4)
mp.ellipse((182,195,246,234),fill=(90,148,172))
# roads, villages, peaks
for road in [[(12,60),(65,94),(116,111),(170,116),(242,104),(309,59)],[(18,266),(92,223),(158,179),(204,117),(273,34)],[(9,152),(90,156),(140,194),(205,245),(307,279)]]:
 mp.line(road,fill=(102,91,66),width=4,joint='curve')
 mp.line(road,fill=(208,186,142),width=2,joint='curve')
for x,y in [(263,90),(283,112),(265,133),(235,115),(244,73),(281,68),(222,91)]:
 mp.polygon([(x,y+11),(x+9,y-11),(x+16,y+10)],fill=(96,101,97))
 mp.polygon([(x+2,y+6),(x+9,y-9),(x+12,y+5)],fill=(202,198,177))
for x,y in [(36,37),(61,40),(84,68),(51,89),(32,113),(73,120),(154,45),(183,55),(158,83),(189,102),(44,225),(85,273),(260,269),(275,231),(237,263)]:
 for ii in range(6):
  xx=x+rng.randint(-12,12); yy=y+rng.randint(-11,11)
  mp.rectangle((xx-3,yy-5,xx+2,yy+3),fill=rng.choice([(62,99,60),(83,115,64),(107,132,70)]))
  mp.point((xx,yy+4),fill=(68,70,45))
for x,y in [(192,139),(121,67),(29,183),(236,190),(164,276)]:
 mp.rectangle((x-5,y-4,x+5,y+4),fill=(137,116,88),outline=(75,65,55),width=1)
 mp.rectangle((x-3,y-10,x+2,y-4),fill=(111,104,89))
# vintage map grid and vignette
for p in range(16,320,32):
 mp.line([(p,6),(p,314)],fill=(128,107,75),width=1)
 mp.line([(6,p),(314,p)],fill=(128,107,75),width=1)
for i in range(11):
 c=(130+i*6,103+i*7,65+i*6)
 mp.rectangle((i,i,319-i,319-i),outline=c,width=1)
mp.rectangle((12,12,307,307),outline=(91,76,52),width=2)
# compass rose stamped on bottom left
mp.polygon([(29,263),(23,285),(29,279),(35,285)],fill=(76,59,44))
mp.line((29,261,29,297),fill=(75,54,42),width=2)
mp.line((14,279,44,279),fill=(75,54,42),width=2)
mp.text((26,248),'N',fill=(73,52,41))
# subtle salt / parchment aging
for i in range(1150):
 x=rng.randrange(MAPSIZE);y=rng.randrange(MAPSIZE)
 if x<14 or x>305 or y<14 or y>305:continue
 mp.point((x,y),fill=rng.choice([(195,162,114),(219,200,150),(169,152,112)]))
atlas.paste(mapimg,(0,0))

# Colored texture swatches. Many have deliberate pixel patterns.
mat_names=['wood_top','wood_dark','wood_light','wood_edge','wood_end','wood_shadow',
 'iron','iron_dark','iron_rivet','gold','brass','red_cloth','red_light','blue_cloth','blue_light','parchment',
 'paper_edge','scroll','book_red','book_green','book_brown','book_pages','ink_black','feather',
 'lantern_glow','lantern_frame','stone','stone_light','terrain_green','terrain_blue','compass','ruler',
 'banner_gold','chest','leather','wood_plank','ink_red','ink_blue','metal_shine','map_back','glass','quill','pin_wood','drawer','cloud_white']
base_colors={
 'wood_top':(118,70,40),'wood_dark':(79,47,30),'wood_light':(157,101,54),'wood_edge':(96,57,35),'wood_end':(115,76,45),'wood_shadow':(61,37,27),
 'iron':(78,83,85),'iron_dark':(45,49,55),'iron_rivet':(125,130,129),'gold':(190,137,50),'brass':(160,117,55),
 'red_cloth':(130,37,40),'red_light':(183,52,44),'blue_cloth':(38,71,121),'blue_light':(54,107,174),
 'parchment':(220,191,140),'paper_edge':(165,127,89),'scroll':(213,182,129),
 'book_red':(121,44,39),'book_green':(42,79,54),'book_brown':(94,59,44),'book_pages':(228,205,155),
 'ink_black':(31,35,43),'feather':(226,211,169),'lantern_glow':(252,177,53),'lantern_frame':(42,39,41),
 'stone':(102,104,102),'stone_light':(154,153,144),'terrain_green':(93,124,64),'terrain_blue':(61,121,162),
 'compass':(71,69,63),'ruler':(182,126,62),'banner_gold':(217,161,57),'chest':(109,69,44),'leather':(113,70,47),
 'wood_plank':(137,82,45),'ink_red':(154,35,37),'ink_blue':(42,72,165),'metal_shine':(163,168,169),
 'map_back':(120,80,56),'glass':(249,198,103),'quill':(236,220,192),'pin_wood':(106,64,44),
 'drawer':(102,69,44),'cloud_white':(231,226,202)
}
rects={}
for idx,name in enumerate(mat_names):
 x0=320+(idx%6)*CELL; y0=(idx//6)*CELL
 tile=Image.new('RGB',(CELL,CELL),base_colors[name]); td=ImageDraw.Draw(tile)
 r,g,b=base_colors[name]
 for j in range(100):
  x=rng.randrange(32); y=rng.randrange(32);v=rng.randint(-22,22)
  td.rectangle((x,y,x+rng.randint(0,4),y+rng.randint(0,2)),fill=tuple(max(0,min(255,n+v)) for n in (r,g,b)))
 if 'wood' in name or name in ['drawer','chest']:
  for k in range(1,32,5):td.line((0,k,31,k+rng.choice([-1,0,1])),fill=tuple(max(0,int(a*.68)) for a in (r,g,b)),width=1)
  for k in range(3,32,9):td.line((k,0,k-2,31),fill=tuple(min(255,int(a*1.16)) for a in (r,g,b)),width=1)
 if name in ['iron','iron_dark','iron_rivet','stone','stone_light']:
  for k in range(18):
   x=rng.randrange(32);y=rng.randrange(32);v=rng.choice([-20,-12,17,24]);td.rectangle((x,y,x+2,y+2),fill=tuple(max(0,min(255,a+v)) for a in (r,g,b)))
 if name=='compass':
  td.rectangle((1,1,30,30),fill=(35,39,42),outline=(123,111,86),width=3)
  td.ellipse((5,5,26,26),fill=(211,198,163),outline=(65,59,52),width=2)
  td.polygon([(16,6),(19,17),(14,17)],fill=(176,45,38));td.polygon([(16,26),(13,15),(18,15)],fill=(47,65,85))
  td.ellipse((14,14,18,18),fill=(33,37,41))
 if name=='ruler':
  for k in range(0,32,4): td.line((k,2,k,7 if k%8==0 else 4),fill=(57,46,31),width=1)
 if name=='banner_gold':
  td.polygon([(16,2),(22,11),(19,11),(19,22),(13,22),(13,11),(10,11)],fill=(133,70,27))
 if name=='book_red' or name=='book_green' or name=='book_brown':
  td.rectangle((3,3,28,28),outline=(191,141,52),width=2)
  td.line((8,16,23,16),fill=(191,141,52),width=1)
  td.line((16,8,16,24),fill=(191,141,52),width=1)
 if name=='lantern_glow':
  for rad,color in [(14,(219,134,31)),(10,(244,173,48)),(6,(255,231,117))]:td.ellipse((16-rad,16-rad,16+rad,16+rad),fill=color)
 if name=='parchment':
  for k in range(3,29,6):td.line((3,k,25-rng.randrange(8),k),fill=(178,154,109),width=1)
 if name=='glass':td.rectangle((2,2,29,29),outline=(255,230,160),width=2)
 atlas.paste(tile,(x0,y0));rects[name]=(x0,y0,x0+32,y0+32)
rects['battle_map']=(0,0,320,320)
atlas.save(TEXTURES/'combat_table_atlas.png',optimize=True)

# Model data: group -> cubes. Coordinates centered across 48x16x48.
boxes=[]
def box(name,x0,y0,z0,x1,y1,z1,mat='wood_top',group='frame',up=None,sides=None):
 assert x0<x1 and y0<y1 and z0<z1 and -24.001<=x0<x1<=24.001 and -24.001<=z0<z1<=24.001 and 0<=y0<y1<=16.001, (name,x0,y0,z0,x1,y1,z1)
 boxes.append({'name':name,'bounds':(x0,y0,z0,x1,y1,z1),'mat':mat,'up':up,'sides':sides,'group':group})

# Table: legs, beams, apron, trim and inset table planks
for x in [-21,17]:
 for z in [-21,17]:
  box('carved_corner_leg',x,0,z,x+4,10.5,z+4,'wood_dark','table / legs')
  box('leg_lower_foot',x-.8,0,z-.8,x+4.8,1.0,z+4.8,'wood_edge','table / legs')
  box('leg_metal_band',x-.3,7.2,z-.3,x+4.3,8.2,z+4.3,'iron','table / ironwork')
  box('leg_tenon',x+1,1,z+1,x+3,8,z+3,'wood_light','table / legs')
for x in [-1.8]:
 for z in [-21,17]:
  box('support_leg_middle',x,0,z,x+3.6,9.6,z+4,'wood_dark','table / legs')
for z in [-1.8]:
 for x in [-21,17]:box('support_leg_side',x,0,z,x+4,9.6,z+3.6,'wood_dark','table / legs')
# braces
for z in [-19,19]:box('lower_stretcher_front_back',-19,2.5,z,19,4.6,z+2,'wood_shadow','table / underside')
for x in [-19,19]:box('lower_stretcher_left_right',x,2.5,-19,x+2,4.6,19,'wood_shadow','table / underside')
for z in [-22,19.3]:box('apron_front_back',-22,5.9,z,22,9.7,z+2.7,'wood_dark','table / underside')
for x in [-22,19.3]:box('apron_sides',x,5.9,-20,x+2.7,9.7,20,'wood_edge','table / underside')
box('tabletop_main',-24,9.3,-24,24,11.4,24,'wood_dark','table / top')
# Top surface planks, alternating colors
for i in range(12):
 z=-22.5+i*3.75
 box(f'wide_plank_{i}',-22.7,11.4,z,22.7,11.65,z+3.55,['wood_top','wood_plank','wood_light','wood_top'][i%4],'table / top')
# raised rim border
for z in [-24,22.6]:
 box('table_rim_horizontal',-24,11.4,z,24,12.05,z+1.4,'wood_edge','table / top')
for x in [-24,22.6]:
 box('table_rim_vertical',x,11.4,-22.6,x+1.4,12.05,22.6,'wood_edge','table / top')
# grain seams/brass corner armor
for x in [-24,19.2]:
 for z in [-24,19.2]:
  box('corner_armor',x,10.15,z,x+4.8,12.18,z+4.8,'iron','table / ironwork')
  box('corner_armor_plate',x+.7,12.18,z+.7,x+4.1,12.3,z+4.1,'iron_dark','table / ironwork')
  box('corner_rivet',x+2.0,12.3,z+2,x+2.8,12.45,z+2.8,'iron_rivet','table / ironwork')
# Horizontal iron corner bands and rivets
for z in [-23,21.8]:
 for x in [-16,-4,8]:
  box('top_rim_iron_clasp',x,11.45,z,x+2.5,12.1,z+1.2,'iron','table / ironwork')
# drawer / front decorative panel
box('large_drawer',-9,6.4,22.72,9,9.08,23.82,'drawer','table / drawers')
box('drawer_inset',-7.2,6.9,23.82,7.2,8.6,23.94,'wood_edge','table / drawers')
box('drawer_pull_backing',-2.2,7.1,23.94,2.2,8.18,24,'iron_dark','table / drawers')
box('drawer_pull_gold',-1.4,7.42,23.97,1.4,7.85,24,'gold','table / drawers')
# Red heraldic hangings, integrated into sides
for x in [-17,12]:
 box('hanging_banner_red',x,2.0,23.42,x+5,9.3,23.85,'red_cloth','table / banners',sides='red_cloth')
 box('hanging_banner_gold_top',x-.25,8.95,23.55,x+5.25,9.7,23.97,'gold','table / banners')
 box('hanging_herald_gold_vert',x+2,4.6,23.86,x+3.1,7.3,23.985,'banner_gold','table / banners')
 box('hanging_herald_gold_horiz',x+1.2,5.75,23.86,x+4,6.42,23.985,'gold','table / banners')

# Main battle map, parchment trim and corner clips
box('map_backing',-16.35,11.66,-16.35,16.35,11.91,16.35,'paper_edge','map / parchment')
box('main_illustrated_map',-15.75,11.91,-15.75,15.75,12.04,15.75,'parchment','map / parchment',up='battle_map')
for x in [-16.1,15.1]:
 for z in [-16.1,15.1]:box('map_corner_anchor',x,12.04,z,x+1,12.25,z+1,'brass','map / parchment')

# Red and blue tactical tokens: low enough for max height 16
for side,locs in [('red',[(-10,-8),(-1,-10),(6,-5),(0,2),(-6,4)]),('blue',[(6,8),(11,1),(-7,9),(-2,12)])]:
 mat='red_cloth' if side=='red' else 'blue_cloth'; light='red_light' if side=='red' else 'blue_light'
 for i,(x,z) in enumerate(locs,1):
  box(f'{side}_token_{i}_base',x-1,12.07,z-1,x+1,12.85,z+1,'stone','map / strategy pieces')
  box(f'{side}_token_{i}_colored_band',x-.7,12.86,z-.7,x+.7,13.34,z+.7,mat,'map / strategy pieces')
  box(f'{side}_token_{i}_flagpole',x-.18,13.34,z-.18,x+.18,15.48,z+.18,'pin_wood','map / strategy pieces')
  box(f'{side}_token_{i}_banner',x+.18,14.35,z-.2,x+1.45,15.36,z+.16,light,'map / strategy pieces')
  box(f'{side}_token_{i}_banner_tip',x+.96,14.5,z-.21,x+1.42,14.7,z+.17,mat,'map / strategy pieces')
for i,(x,z) in enumerate([(1,-1),(8,-10),(-12,1)],1):
 box(f'castle_{i}_foundation',x-1.5,12.07,z-1.5,x+1.5,12.52,z+1.5,'stone','map / strongholds')
 box(f'castle_{i}_keep',x-.8,12.52,z-.8,x+.8,14.35,z+.8,'stone_light','map / strongholds')
 for dx,dz in [(-1,-1),(-1,1),(1,-1),(1,1)]:
  box(f'castle_{i}_battlement',x+dx-.3,14.35,z+dz-.3,x+dx+.3,14.75,z+dz+.3,'stone','map / strongholds')
# A mountainous group as relief
for i,(x,z,size) in enumerate([(10,-11,1.3),(12,-9,1.6),(-12,-11,1.2)],1):
 box(f'mountain_{i}_base',x-size,12.06,z-size,x+size,12.55,z+size,'stone','map / geography')
 box(f'mountain_{i}_ridge',x-size*.55,12.55,z-size*.55,x+size*.55,13.55,z+size*.55,'stone_light','map / geography')
 box(f'mountain_{i}_snowcap',x-.35,13.55,z-.35,x+.35,13.9,z+.35,'cloud_white','map / geography')
# Lantern upper left behind map
box('lantern_plinth',-22.8,11.65,-22.5,-18.2,12.2,-17.9,'wood_edge','tools / lantern')
box('lantern_base',-22.3,12.2,-22.0,-18.7,12.65,-18.3,'lantern_frame','tools / lantern')
box('lantern_glowing_core',-21.8,12.65,-21.6,-19.2,15.0,-18.8,'lantern_glow','tools / lantern')
for x in [-22.25,-19.2]:
 for z in [-21.95,-19.05]:box('lantern_iron_post',x,12.65,z,x+.4,15.2,z+.4,'lantern_frame','tools / lantern')
box('lantern_top',-22.4,15.0,-22.1,-18.6,15.52,-18.2,'lantern_frame','tools / lantern')
box('lantern_handle',-21.1,15.52,-20.6,-20.0,15.88,-19.8,'iron','tools / lantern')
# book stacks back edges
for i,(x,z,color) in enumerate([(15.8,-22,'book_red'),(18.1,-22,'book_green'),(-16,-22,'book_brown')],1):
 if x>0:
  box(f'book_{i}_pages',x,11.66,z,x+5.0,12.02,z+3.8,'book_pages','tools / books')
  box(f'book_{i}_cover',x-.25,12.02,z-.3,x+5.25,12.35,z+4.1,color,'tools / books',up=color)
 else:
  box('open_field_manual',x,11.65,z,x+6,12.05,z+3,'book_green','tools / books')
# extra stacked books back right
box('bookstack_lower_pages',15,12.35,-21.4,21,12.75,-17.2,'book_pages','tools / books')
box('bookstack_lower',14.8,12.75,-21.6,21.2,13,-17.0,'book_red','tools / books')
box('bookstack_upper_pages',16.2,13,-21.9,21.8,13.35,-18.2,'book_pages','tools / books')
box('bookstack_upper',16,13.35,-22.1,22,13.62,-18,'book_green','tools / books')
# scrolls; axis-aligned faceted rollers, red wrap bands
for ii,(x,z) in enumerate([(-21,8.5),(18,-7.5)],1):
 box(f'scroll_{ii}_paper',x,11.66,z,x+4,13.08,z+2.4,'scroll','tools / scrolls')
 for k in [0.8,2.8]:box(f'scroll_{ii}_wax_band_{k}',x+k,11.65,z-.08,x+k+.38,13.16,z+2.48,'red_cloth','tools / scrolls')
 for k in [0,3.6]:box(f'scroll_{ii}_roll_end_{k}',x+k,11.87,z+.42,x+k+.38,12.87,z+1.98,'paper_edge','tools / scrolls')
# compass lower left, metal square and inset compass face tile as top
box('compass_case',-21.6,11.67,17.1,-17.1,12.18,21.7,'iron_dark','tools / navigation',up='compass')
box('compass_glass',-20.9,12.18,17.8,-17.8,12.23,21.0,'compass','tools / navigation',up='compass')
# wooden ruler lower edge
box('measuring_ruler',-6.0,11.67,19.4,7,11.97,21.0,'ruler','tools / navigation',up='ruler')
for k in range(0,13):box(f'ruler_tick_{k}',-5.8+k,11.98,19.45,-5.66+k,12.02,20.1 if k%2 else 20.5,'ink_black','tools / navigation')
# writing supplies / folded parchment on right side
box('side_letter',18,11.67,7.0,22.5,11.85,13,'parchment','tools / documents')
box('side_letter_ribbon',19.8,11.86,8,20.2,11.91,11.4,'red_cloth','tools / documents')
box('ink_bottle',-20.0,11.66,-15.4,-17.2,13.3,-12.6,'ink_black','tools / writing')
box('ink_bottle_neck',-19.4,13.3,-14.8,-17.8,13.8,-13.2,'iron_dark','tools / writing')
# quill pixel staircase leaning behind
for i in range(5):
 x=-19.1+i*.48;y=13.8+i*.39;z=-14.0+i*.17
 box(f'quill_step_{i}',x,y,z,x+.6,y+.6,z+.45,'quill','tools / writing')
box('quill_spike',-16.75,15.42,-13.32,-16.4,15.85,-12.98,'feather','tools / writing')
# Knife pointer on the right front side, modeled as metal bands along Z
box('knife_handle',18.5,11.68,14.0,19.9,12.07,19.0,'leather','tools / pointer')
box('knife_guard',17.9,11.68,13.6,20.4,12.11,14.5,'gold','tools / pointer')
box('knife_blade',18.9,11.68,10.3,19.55,11.96,13.6,'metal_shine','tools / pointer')
box('knife_tip',19.0,11.68,9.5,19.45,11.95,10.3,'iron','tools / pointer')
# A small casket in rear center behind map
box('strategy_supply_chest',-4.5,11.66,-22.5,1.5,13.0,-17.3,'chest','tools / supply chest')
box('strategy_chest_lid',-4.7,13,-22.7,1.7,13.35,-17.1,'wood_dark','tools / supply chest')
box('strategy_chest_lock',-2.0,12.12,-17.1,-1,13,-16.8,'gold','tools / supply chest')

print('MODEL CUBES',len(boxes),'ATLAS textures',len(mat_names))

# UV mapping shared by all outputs.
def face_uv(b,face,subb=None):
 x0,y0,z0,x1,y1,z1=b['bounds']
 a0,b0,c0,a1,b1,c1 = subb or b['bounds']
 tile=rects[b['up'] if face=='up' and b['up'] else b['sides'] if face!='up' and b['sides'] else b['mat']]
 u0,v0,u1,v1=tile
 if face in ('north','south'):
  lo=(a0-x0)/(x1-x0);hi=(a1-x0)/(x1-x0)
  top=(y1-b1)/(y1-y0);btm=(y1-b0)/(y1-y0)
 elif face in ('east','west'):
  lo=(c0-z0)/(z1-z0);hi=(c1-z0)/(z1-z0)
  top=(y1-b1)/(y1-y0);btm=(y1-b0)/(y1-y0)
 else:
  lo=(a0-x0)/(x1-x0);hi=(a1-x0)/(x1-x0)
  top=(c0-z0)/(z1-z0);btm=(c1-z0)/(z1-z0)
 return [round(u0+lo*(u1-u0),4),round(v0+top*(v1-v0),4),round(u0+hi*(u1-u0),4),round(v0+btm*(v1-v0),4)]
faces=['north','east','south','west','up','down']
def raw_cube(b):
 xa,ya,za,xb,yb,zb=b['bounds']
 return {'name':b['name'],'type':'cube','uuid':str(uuid.uuid4()),'from':[xa,ya,za],'to':[xb,yb,zb],
         'origin':[(xa+xb)/2,(ya+yb)/2,(za+zb)/2],
         'faces':{f:{'uv':face_uv(b,f),'texture':0} for f in faces}}

# Blockbench project, full 3x3 scene. Format version 4.x outliner for broad compatibility.
elements=[]; groups=[]
for g in dict.fromkeys(b['group'] for b in boxes):
 obj={'name':g,'origin':[0,0,0],'color':len(groups)%8,'uuid':str(uuid.uuid4()),'export':True,'isOpen':len(groups)<3,'visibility':True,'children':[]}
 groups.append(obj)
for b in boxes:
 cube=raw_cube(b)
 elements.append(cube)
 next(g for g in groups if g['name']==b['group'])['children'].append(cube['uuid'])
texdata=base64.b64encode((TEXTURES/'combat_table_atlas.png').read_bytes()).decode('ascii')
proj={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},
      'name':'Combat Table | 3x1x3 Tactical Command Table','model_identifier':'combat_table',
      'resolution':{'width':512,'height':512},'elements':elements,'outliner':groups,
      'textures':[{'path':'','name':'combat_table_atlas.png','id':'0','particle':False,'render_mode':'default',
                   'visible':True,'mode':'bitmap','saved':True,'uuid':str(uuid.uuid4()),'width':512,'height':512,
                   'source':'data:image/png;base64,'+texdata}]}
(ROOT/'combat_table_3x1x3.bbmodel').write_text(json.dumps(proj,ensure_ascii=False,separators=(',',':')),encoding='utf-8')

# Split model geometry at seams x = -8, 8 and z = -8,8. Each JSON model is local to its 16x16 block.
rangeXZ=[(-24,-8),(-8,8),(8,24)]
labels=['north','center','south']; xlabels=['west','center','east']
for zi,(zL,zH) in enumerate(rangeXZ):
 for xi,(xL,xH) in enumerate(rangeXZ):
  part=[]
  for b in boxes:
   a0,b0,c0,a1,b1,c1=b['bounds']
   s=(max(a0,xL),b0,max(c0,zL),min(a1,xH),b1,min(c1,zH))
   if not(s[0]<s[3] and s[2]<s[5]):continue
   local=[round(s[0]-xL,4),round(s[1],4),round(s[2]-zL,4),round(s[3]-xL,4),round(s[4],4),round(s[5]-zL,4)]
   face_props={f:{'uv':[round(q/32,5) for q in face_uv(b,f,s)],'texture':'#all'} for f in faces}
   part.append({'name':b['name'],'from':local[:3],'to':local[3:],'faces':face_props})
  mdl={'credit':'Original custom model generated for Minecraft Forge 1.20.1. Visual assets only.',
       'texture_size':[512,512],'textures':{'particle':'combattable:block/combat_table_atlas', 'all':'combattable:block/combat_table_atlas'},'elements':part,
       'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.5,.5,.5]}}}
  (MODELS/f'combat_table_{labels[zi]}_{xlabels[xi]}.json').write_text(json.dumps(mdl,ensure_ascii=False,indent=2),encoding='utf-8')
# Create blueprint so implementing placements in Java is clear
split_readme='''# Combat Table: 3×1×3 asset models

Orientation (view from above; Minecraft north is -Z):

```text
             WEST      CENTER      EAST
NORTH        NW        NC          NE
CENTER       CW        CC          CE
SOUTH        SW        SC          SE
```

Each model lives in `assets/combattable/models/block/` and references the embedded atlas at `assets/combattable/textures/block/combat_table_atlas.png`.
The default exported 3×3 model is centered at (0,0,0) in generic Blockbench coordinates, with x,z spanning -24 to +24 and y 0 to 16 (16 model units = 1 Minecraft block).

This download supplies **model assets only**. A Forge 1.20.1 mod still requires block registration, its nine part blockstates, 3×3 placement/removal logic, and collision/selection shapes. The nine resource JSON models are visual slices, not a working mod on their own.

All geometry fits a 48×16×48 envelope. Rotating the assembled structure is handled by blockstates or custom Java placement code.
'''
(ROOT/'FORGE_MODEL_NOTES.md').write_text(split_readme,encoding='utf-8')

# GLB preview geometry assembled into one textured material atlas.
verts=[]; uvs=[]; fids=[]
def add_quad(points,uvrect,coord_uv):
 u0,v0,u1,v1=uvrect
 # Order points corresponds [top-left,top-right,bottom-right,bottom-left] of face in source texture.
 i=len(verts)
 for v,(u,vv) in zip(points,[(u0,v0),(u1,v0),(u1,v1),(u0,v1)]):
  verts.append(v);uvs.append([u/512,1-vv/512])
 if coord_uv in ('up','south','west'):
  fids.extend([[i,i+2,i+1],[i,i+3,i+2]])
 else:
  fids.extend([[i,i+1,i+2],[i,i+2,i+3]])
for b in boxes:
 x0,y0,z0,x1,y1,z1=b['bounds']
 quads={
 'up':[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
 'down':[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)],
 'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],
 'south':[(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],
 'west':[(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],
 'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)]}
 for ff,pts in quads.items():add_quad(pts,face_uv(b,ff),ff)
mesh=trimesh.Trimesh(vertices=np.array(verts),faces=np.array(fids),process=False)
mat=trimesh.visual.material.PBRMaterial(name='combat_table_atlas',baseColorTexture=atlas,metallicFactor=0,roughnessFactor=1,doubleSided=True)
mesh.visual=trimesh.visual.TextureVisuals(uv=np.asarray(uvs),material=mat)
mesh.export(ROOT/'combat_table_3d.glb')

# Render the *actual model* as an isometric preview with textured visible faces.
W,H=1600,1180
canvas=np.zeros((H,W,3),np.uint8)
canvas[:]=[235,235,234]
# ambient subtle gradient
for yy in range(H):
 v=int(247-(yy/H)*15);canvas[yy,:]=[v,v,v]
scale=15.6
angle=math.radians(33)
cs=math.cos(angle);sn=math.sin(angle)
# camera aligned with positive X, positive Z
# height profile and projection
def pt(x,y,z):
 return (int(W*.5+(x-z)*scale*.82),int(H*.68+(x+z)*scale*.38 -y*scale*.96))
items=[]
for b in boxes:
 x0,y0,z0,x1,y1,z1=b['bounds']
 ffgeom={
  'up':[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
  'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],
  'south':[(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)]}
 for face,points in ffgeom.items():
  cp=np.mean(points,axis=0)
  depth=cp[0]+cp[2]+cp[1]*.86
  items.append((depth,b,face,points))
items.sort(key=lambda s:s[0])
zbuffer=np.full((H,W),-1e10,np.float32)
# fast texture warp per face, with depth testing so the map is not covered by tabletop planks
for dep,b,face,points in items:
 coords=np.float32([pt(*v) for v in points])
 if abs(cv2.contourArea(coords))<0.3:continue
 u0,v0,u1,v1=face_uv(b,face)
 tpatch=np.array(atlas.convert('RGB').crop((math.floor(u0),math.floor(v0),math.ceil(u1),math.ceil(v1))))
 if tpatch.size==0:continue
 # RGB to cv2 BGR not necessary as saved PIL expects RGB array all along
 hh,ww=tpatch.shape[:2];
 ox,oy,bw,bh=cv2.boundingRect(coords.astype(np.int32));ox=max(0,ox);oy=max(0,oy);bw=min(W-ox,bw);bh=min(H-oy,bh)
 if bw<=0 or bh<=0:continue
 quad=coords-np.array([ox,oy],np.float32)
 src=np.array([[0,0],[ww-1,0],[ww-1,hh-1],[0,hh-1]],np.float32)
 M=cv2.getPerspectiveTransform(src,quad)
 warped=cv2.warpPerspective(tpatch,M,(bw,bh),flags=cv2.INTER_NEAREST,borderMode=cv2.BORDER_CONSTANT,borderValue=(0,0,0))
 mask=np.zeros((bh,bw),np.uint8);cv2.fillConvexPoly(mask,np.round(quad).astype(np.int32),255)
 lighting={'up':1.03,'east':.75,'south':.89}[face]
 warped=np.clip(warped.astype(np.float32)*lighting,0,255).astype(np.uint8)
 roi=canvas[oy:oy+bh,ox:ox+bw]
 depvals=np.array([p[0]+p[2]+p[1]*(.76/.96) for p in points],np.float32)
 # orthographic projected depth is an affine function on the surface
 try:
  xyz=np.column_stack([coords[:3,0],coords[:3,1],np.ones(3)])
  aa,bb,cc=np.linalg.solve(xyz,depvals[:3]); yy,xx=np.mgrid[oy:oy+bh,ox:ox+bw]; zz=aa*xx+bb*yy+cc
 except np.linalg.LinAlgError:
  zz=np.full((bh,bw),dep)
 zb=zbuffer[oy:oy+bh,ox:ox+bw]
 visible=(mask>0)&(zz>zb+0.001)
 roi[visible]=warped[visible]
 zb[visible]=zz[visible]
 # outline per silhouette-ish face (thin, not black)
 # cv2.polylines(canvas,[coords.astype(np.int32)],True,(76,65,56),1,cv2.LINE_AA)
# Drop shadow (behind? place subtle below table legs)
preview=Image.fromarray(canvas)
drw=ImageDraw.Draw(preview)
# small heading
try:
 font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',38)
 subtitle=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',22)
except:font=subtitle=ImageFont.load_default()
drw.text((62,50),'COMBAT TABLE',fill=(47,43,42),font=font)
drw.text((62,102),'3 x 1 x 3   |   Minecraft Forge 1.20.1  |  editable Blockbench model',fill=(94,89,83),font=subtitle)
preview.save(ROOT/'combat_table_preview.png',optimize=True)
# Top-down layout preview by stitching pixels from topmost surfaces (simple diagram)
diag=Image.new('RGB',(750,810),(246,244,239));di=ImageDraw.Draw(diag)
mapmini=mapimg.resize((460,460),Image.Resampling.NEAREST)
diag.paste(mapmini,(145,140))
di.rectangle((137,132,612,607),outline=(79,54,39),width=8)
for i in range(4):
 k=140+i*153.33; di.line([(k,132),(k,607)],fill=(111,94,70),width=2);di.line([(137,k),(612,k)],fill=(111,94,70),width=2)
di.text((42,20),'TOP VIEW / MODEL TILE SPLIT',fill=(48,44,38),font=subtitle)
di.text((50,655),'North',fill=(64,60,54),font=subtitle)
di.text((50,697),'Visual reference for 3x3 model parts',fill=(87,82,73),font=subtitle)
diag.save(ROOT/'combat_table_map_layout.png')

# File instructions included for user
readme='''# Tactical Combat Table | 마인크래프트 Forge 1.20.1

3×1×3 (48×16×48 모델 좌표)의 중세 전술 작전 테이블 모델입니다. 실제 파일을 제공하는 제작 초안이며, 콘셉트 이미지와 똑같은 메시를 복원한 것은 아닙니다.

## 가장 먼저 열어볼 파일

- `combat_table_3x1x3.bbmodel`: **Blockbench에서 File > Open Model로 열기**. 텍스처가 내장된 편집 가능한 전체 복셀 모델입니다.
- `combat_table_preview.png`: 실제로 만든 큐브의 아이소메트릭 미리보기.
- `combat_table_3d.glb`: 메시가 포함된 범용 3D 미리보기 파일(Blender 등에서 열 수 있음).
- `forge_resources/assets/combattable/models/block/*.json`: 3×3 위치별 분할된 Minecraft Java 블록 모델 9개.
- `forge_resources/assets/combattable/textures/block/combat_table_atlas.png`: 공용 512×512 텍스처 아틀라스.
- `FORGE_MODEL_NOTES.md`: Forge 프로젝트에 적용할 때의 분할 설명.

## 중요

이 파일들은 **커스텀 블록의 외형 에셋**입니다. Forge Java 코드, 블록 등록, 배치/철거 처리, 블록스테이트 설정, 충돌판정은 포함하지 않았습니다.

`Blockbench` 프로젝트는 `Generic Model` 형식이라서 전체 디자인을 자유롭게 수정할 수 있습니다. 게임에 적용하려면 9개 분할 JSON을 3x3 파트 블록의 모델로 참조하도록 등록해야 합니다.

Blockbench에 모델을 열 때 프로젝트 좌표의 `-24`부터 `+24`까지가 3×3 폭이고, 최대 높이는 16입니다. 파일을 수정한 뒤에는 Forge용 9조각 JSON을 다시 내보내거나 동일한 기준으로 분할해야 합니다. `build_model.py`는 모델 재생성 스크립트로 제공됩니다.

Designed elements: oak/dark oak surface, metal corner clasps, legs, decorative red pennants, illustrated terrain map, tactical team flags (red/blue), forts, mountains, lantern, scrolls, books, compass, ruler, quill, inkwell, pointer dagger, drawer.
'''
(ROOT/'README_KO.md').write_text(readme,encoding='utf-8')
zipname=Path('/mnt/data/Combat_Table_Blockbench_Forge_1.20.1.zip')
with zipfile.ZipFile(zipname,'w',zipfile.ZIP_DEFLATED,compresslevel=8) as zf:
 for p in ROOT.rglob('*'):
  if p.is_file() and not p.name.startswith('.'):
   zf.write(p,p.relative_to(ROOT).as_posix())
print('OUTPUT ZIP',zipname,zipname.stat().st_size)
print('GLB', (ROOT/'combat_table_3d.glb').stat().st_size,'bbmodel', (ROOT/'combat_table_3x1x3.bbmodel').stat().st_size)
