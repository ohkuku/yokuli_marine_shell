(async function () {
  'use strict';
  const root = document.getElementById('app'), ui = Yokuli.ui;
  let en = false;
  const t = (zh, english) => en ? english : zh;
  try { en = (await Yokuli.system.info()).language !== 'zh-CN'; }
  catch(error) { root.append(ui.status(ui.errorMessage(error),true)); return; }
  const header = ui.node('header','yk-header');
  header.append(ui.node('p','yk-eyebrow',t('随航一览','Underway')),ui.node('h1','',t('此刻，船与风','Boat, wind & heading')));
  const errorBox = ui.status('',true); errorBox.hidden = true; errorBox.setAttribute('role','alert');
  const position = ui.node('section','yk-section'), coordinate = ui.node('p','yk-coordinate','—'), positionAge = ui.status('');
  position.append(ui.node('h2','yk-section-title',t('船位','Position')),coordinate,positionAge);
  const grid = ui.node('div','yk-grid'), note = ui.status(t('方向依据船首向。航迹向单独呈现，不替代船首向。','Heading shows the bow direction. Course over ground is shown separately.'));
  const actions = ui.node('div','yk-actions');
  actions.append(ui.button(t('海图','Chart'),() => Yokuli.navigation.open('chart')),ui.button(t('数据来源','Data sources'),() => Yokuli.navigation.open('data_center'),{secondary:true}));
  root.append(header,errorBox,position,grid,note,actions);
  root.addEventListener('yokulierror', event => {errorBox.textContent=ui.errorMessage(event.detail);errorBox.hidden=false;});
  const definitions = [
    [['sog'],'对地速度','Speed over ground',1],
    [['heading'],'船首向','Heading',0],
    [['cog'],'航迹向','Course over ground',0],
    [['tws'],'真风速','True wind speed',1],
    [['twd'],'真风向','True wind direction',0],
    [['pressure'],'气压','Air pressure',1]
  ];
  Yokuli.marine.watch(snapshot => {
    errorBox.hidden = true;
    const vessel=snapshot.vessel || {}, readings=snapshot.readings || {};
    coordinate.textContent=Number.isFinite(vessel.latitude)&&Number.isFinite(vessel.longitude) ? (vessel.positionDisplay || snapshot.positionDisplay || t('已有船位，在海图查看','Position received · view in Chart')) : t('尚无船位','No position received');
    positionAge.textContent=[ui.quality(vessel.freshness || vessel.quality),ui.age(vessel.measuredAt,vessel.ageMillis),vessel.source&&vessel.source.name].filter(Boolean).join(' · ');
    const fragment=document.createDocumentFragment();
    definitions.forEach(([keys,zh,english,digits])=>fragment.append(ui.metric(t(zh,english),readings[keys[0]],digits)));
    grid.replaceChildren(fragment);
  },error=>{errorBox.textContent=ui.errorMessage(error);errorBox.hidden=false;});
})();
