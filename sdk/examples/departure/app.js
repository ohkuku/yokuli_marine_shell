(async function () {
  'use strict';
  const root = document.getElementById('app'), ui = Yokuli.ui;
  let en = false, state = {checks:{}, updatedAt:null}, busy = false;
  const items = [
    ['crew','船员与救生装备','Crew & safety equipment','人数、救生衣和通信设备已确认','Count everyone; check lifejackets and communications'],
    ['boat','船体与动力','Boat & propulsion','舱底、燃油、电量和发动机已检查','Check bilge, fuel, batteries and propulsion'],
    ['route','本次航线','Today’s passage','航线、海图覆盖和避险去处已确认','Review the route, chart coverage and places of shelter'],
    ['conditions','当地情况','Local conditions','已自行了解天气、潮流与出航限制','Check weather, currents and local restrictions yourself'],
    ['lines','离泊准备','Ready to leave','缆绳、护舷与松缆分工已确认','Agree who handles lines and fenders']
  ];
  const t = (zh, english) => en ? english : zh;
  const errorBox = ui.status('',true); errorBox.setAttribute('role','alert'); errorBox.hidden = true;
  function error(value) { errorBox.textContent = ui.errorMessage(value); errorBox.hidden = false; }
  root.addEventListener('yokulierror', event => error(event.detail));
  try {
    const info = await Yokuli.system.info(); en = info.language !== 'zh-CN';
    const saved = await Yokuli.storage.get();
    if (saved.value && saved.value.checks && typeof saved.value.checks === 'object') state = saved.value;
  } catch (value) {
    error(value); root.append(errorBox,ui.button(t('重新打开','Reopen'),() => window.location.reload())); return;
  }
  const header = ui.node('header','yk-header');
  header.append(ui.node('p','yk-eyebrow',t('出航准备','Before departure')),ui.node('h1','',t('一项一项，准备好','Ready to sail')),ui.node('p','yk-subtitle',t('清单只记录你的确认，不会替你判断是否适合出航。','Your own checks, saved here. This checklist does not decide whether it is safe to sail.')));
  root.append(header,errorBox);
  const position = ui.node('section','yk-section');
  position.append(ui.node('h2','yk-section-title',t('船位','Position')));
  const location = ui.node('p','yk-coordinate','—'), age = ui.status(t('等待读数','Waiting for a reading'));
  position.append(location,age,ui.button(t('管理数据来源','Manage sources'),() => Yokuli.navigation.open('data_center'),{secondary:true})); root.append(position);
  const progressLabel = ui.node('p','yk-status'), progress = ui.node('div','yk-progress'), fill = ui.node('div','yk-progress-fill');
  progress.setAttribute('role','progressbar'); progress.setAttribute('aria-valuemin','0'); progress.setAttribute('aria-valuemax',String(items.length)); progress.append(fill);
  const list = ui.node('div','yk-checklist'), savedAt = ui.status(''); root.append(progressLabel,progress,list,savedAt);
  const inputs = [];
  function refresh() {
    let done = 0; inputs.forEach(({key,input}) => {input.checked = state.checks[key] === true; input.disabled = busy; if(input.checked) done++;});
    progressLabel.textContent = t('已确认 '+done+' / '+items.length,done+' of '+items.length+' checked');
    progress.setAttribute('aria-valuenow',String(done)); fill.style.width = (done/items.length*100)+'%';
    savedAt.textContent = state.updatedAt ? t('保存于 ','Saved ')+ui.age(state.updatedAt) : t('勾选后自动保存','Checks save automatically');
  }
  async function save(next) {
    if(busy) return false; busy = true; refresh(); errorBox.hidden = true;
    try { await Yokuli.storage.set(next); state = next; return true; }
    catch(value) { error(value); return false; }
    finally { busy = false; refresh(); }
  }
  items.forEach(([key,zh,english,detailZh,detailEn]) => {
    const label = ui.node('label','yk-check'), input = document.createElement('input'); input.type = 'checkbox';
    const text = ui.node('span','',t(zh,english)); text.append(ui.node('small','',t(detailZh,detailEn)));
    label.append(input,text); list.append(label); inputs.push({key,input});
    input.addEventListener('change',() => save({checks:Object.assign({},state.checks,{[key]:input.checked}),updatedAt:Date.now()}));
  });
  const actions = ui.node('div','yk-actions'), confirmation = ui.node('section','yk-section yk-hidden');
  confirmation.append(ui.status(t('开始新一轮准备会清除当前勾选。','Starting again clears the current checks.')),
    ui.button(t('清除并重新开始','Clear and start again'),async () => { if(await save({checks:{},updatedAt:Date.now()})) confirmation.classList.add('yk-hidden'); }),
    ui.button(t('保留','Keep'),() => confirmation.classList.add('yk-hidden'),{secondary:true}));
  actions.append(ui.button(t('查看海图','Open chart'),() => Yokuli.navigation.open('chart')),ui.button(t('下一次出航','Start a new checklist'),() => confirmation.classList.remove('yk-hidden'),{secondary:true}));
  root.append(actions,confirmation); refresh();
  Yokuli.marine.watch(snapshot => {
    const vessel = snapshot.vessel || {};
    const lat = vessel.latitude, lon = vessel.longitude;
    location.textContent = Number.isFinite(lat) && Number.isFinite(lon) ? (vessel.positionDisplay || snapshot.positionDisplay || t('已有船位，在海图查看','Position received · view in Chart')) : t('尚无船位','No position received');
    age.textContent = [ui.quality(vessel.freshness || vessel.quality),ui.age(vessel.measuredAt,vessel.ageMillis),vessel.source && vessel.source.name].filter(Boolean).join(' · ');
  },error);
})();
