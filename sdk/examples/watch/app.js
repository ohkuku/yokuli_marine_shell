(async function () {
  'use strict';
  const root = document.getElementById('app'), ui = Yokuli.ui;
  let en = false;
  const t = (zh, english) => en ? english : zh;
  try { en = (await Yokuli.system.info()).language !== 'zh-CN'; }
  catch(error) { root.append(ui.status(ui.errorMessage(error),true)); return; }
  const header = ui.node('header','yk-header');
  header.append(ui.node('h1','',t('船与风','Boat & wind')));
  const errorBox = ui.status('',true); errorBox.hidden = true; errorBox.setAttribute('role','alert');
  const scene=ui.node('section','underway-scene'), ns='http://www.w3.org/2000/svg';
  function svgNode(tag,attrs,parent) { const el=document.createElementNS(ns,tag);Object.entries(attrs||{}).forEach(([k,v])=>el.setAttribute(k,v));if(parent)parent.append(el);return el; }
  const svg=svgNode('svg',{viewBox:'0 0 320 280',role:'img','aria-label':t('真北朝上，船头、航迹向和风向','North-up heading, course over ground and wind')});scene.append(svg);
  svgNode('circle',{cx:160,cy:140,r:108,fill:'none',stroke:'currentColor',opacity:'.25'},svg);
  for(let bearing=0;bearing<360;bearing+=30){const rad=bearing*Math.PI/180;svgNode('line',{x1:160+Math.sin(rad)*100,y1:140-Math.cos(rad)*100,x2:160+Math.sin(rad)*108,y2:140-Math.cos(rad)*108,stroke:'currentColor',opacity:'.35'},svg);}
  [['N',160,20],['E',284,145],['S',160,272],['W',35,145]].forEach(([label,x,y])=>{const text=svgNode('text',{x,y,fill:'currentColor','text-anchor':'middle','font-size':13},svg);text.textContent=label;});
  const cog=svgNode('g',{},svg);svgNode('path',{d:'M160 140V42M154 51L160 42L166 51',fill:'none',stroke:'currentColor','stroke-width':2,'stroke-dasharray':'6 5'},cog);
  const wind=svgNode('g',{},svg);svgNode('path',{d:'M160 41V78M154 70L160 78L166 70',fill:'none',stroke:'currentColor','stroke-width':4,opacity:'.6'},wind);
  const boat=svgNode('g',{},svg);svgNode('path',{d:'M160 108L173 144L168 162H152L147 144Z',fill:'currentColor'},boat);
  [boat,cog,wind].forEach(node=>node.style.visibility='hidden');
  const hint=ui.status(t('等待读数','Waiting for readings'));scene.append(hint);
  const legend=ui.node('div','underway-legend'), directionRows={};
  [['heading','船首向','Heading'],['cog','航迹向','Course'],['twd','来风方向','Wind from']].forEach(([key,zh,english])=>{const row=ui.node('p');row.append(ui.node('span','',t(zh,english)));const value=ui.node('strong','','—');row.append(value);legend.append(row);directionRows[key]=value;});scene.append(legend);
  const grid=ui.node('div','yk-grid'), position=ui.node('section','yk-section'),coordinate=ui.node('p','yk-coordinate','—'),positionAge=ui.status('');
  position.append(ui.node('h2','yk-section-title',t('船位','Position')),coordinate,positionAge);
  const actions=ui.node('div','yk-actions');actions.append(ui.button(t('在海图查看','View in Chart'),()=>Yokuli.navigation.open('chart')),ui.button(t('选择数据来源','Choose sources'),()=>Yokuli.navigation.open('data_center'),{secondary:true}));
  root.append(header,errorBox,scene,grid,position,actions);
  root.addEventListener('yokulierror',event=>{errorBox.textContent=ui.errorMessage(event.detail);errorBox.hidden=false;});
  const valid=reading=>reading&&Number.isFinite(reading.rawValue)&&['FRESH','HELD'].includes(reading.freshness||reading.quality);
  const definitions=[['sog','对地速度','Speed over ground',1],['depth','水深','Depth',1],['tws','真风速','True wind speed',1],['pressure','气压','Air pressure',1]];
  const rotations=new Map();let frame=null,paused=false;
  function animate(now){frame=null;if(paused)return;let again=false;rotations.forEach(motion=>{const progress=Math.min(1,(now-motion.start)/650),eased=1-Math.pow(1-progress,3);motion.shown=motion.from+(motion.to-motion.from)*eased;motion.node.setAttribute('transform','rotate('+motion.shown+' 160 140)');again=again||progress<1;});if(again)frame=requestAnimationFrame(animate);}
  function direction(key,node,reading,current){const has=current&&valid(reading);node.style.visibility=has?'visible':'hidden';if(!has){rotations.delete(key);return;}const angle=reading.rawValue;const last=rotations.get(key);const from=last?last.shown:angle;const delta=((angle-from)%360+540)%360-180;rotations.set(key,{node,from,to:from+delta,shown:from,start:performance.now()});if(!frame&&!paused)frame=requestAnimationFrame(animate);}
  window.addEventListener('yokuli:pause',()=>{paused=true;cancelAnimationFrame(frame);frame=null;});
  window.addEventListener('yokuli:resume',()=>{paused=false;if(!frame)frame=requestAnimationFrame(animate);});
  Yokuli.marine.watch(snapshot=>{
    errorBox.hidden=true;const vessel=snapshot.vessel||{},readings=snapshot.readings||{},current=snapshot.snapshotCurrent!==false;
    coordinate.textContent=Number.isFinite(vessel.latitude)&&Number.isFinite(vessel.longitude)?vessel.positionDisplay||t('船位已收到','Position received'):t('尚无船位','No position received');
    positionAge.textContent=[ui.quality(vessel.freshness||vessel.quality),ui.age(vessel.measuredAt,vessel.ageMillis),vessel.source&&vessel.source.name].filter(Boolean).join(' · ');
    direction('heading',boat,readings.heading,current);direction('cog',cog,readings.cog,current);direction('twd',wind,readings.twd,current);
    Object.entries(directionRows).forEach(([key,node])=>{const reading=readings[key];node.textContent=reading&&Number.isFinite(reading.value)?Math.round(reading.value)+'° · '+ui.quality(reading.freshness||reading.quality):'—';});
    hint.textContent=!current?t('显示上次读数，正在等待系统更新','Last readings shown; waiting for the system'):
      !valid(readings.heading)?t('尚无有效船首向；虚线只表示实际航迹向','No current heading; the dashed line shows course over ground only'):
      t('船头：船首向 · 虚线：航迹向 · 箭头：风吹来的方向','Bow: heading · Dashed: course · Arrow: wind from');
    const fragment=document.createDocumentFragment();definitions.forEach(([key,zh,english,digits])=>fragment.append(ui.metric(t(zh,english),readings[key],digits)));grid.replaceChildren(fragment);
  },error=>{errorBox.textContent=ui.errorMessage(error);errorBox.hidden=false;[boat,cog,wind].forEach(node=>node.style.visibility='hidden');});
})();
