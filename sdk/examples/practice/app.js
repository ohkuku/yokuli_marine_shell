(async function () {
  'use strict';
  const ui = Yokuli.ui, root = document.getElementById('app');
  root.className = 'yk-page';
  let en = false, busy = false, current = null, pending = null, interacting = false, controlGranted = false;
  const t = (zh, english) => en ? english : zh;
  const status = ui.status('', true), summary = ui.node('section', 'yk-section'), controls = ui.node('section', 'yk-section'), devices = ui.node('section', 'yk-section');
  status.hidden = true;
  function showError(error) { status.hidden = false; status.textContent = ui.errorMessage(error); }
  root.addEventListener('yokulierror', event => showError(event.detail));
  root.addEventListener('pointerdown', () => { interacting = true; });
  function release() { interacting = false; setTimeout(() => render(), 0); }
  window.addEventListener('pointerup', release); window.addEventListener('pointercancel', release);
  async function send(command) {
    if (busy) return; busy = true; render(true);
    try {
      pending = command;
      await Yokuli.storage.set({pending});
      const result = await Yokuli.hardware.control(command);
      if (!result.accepted) {
        // 明确拒绝未执行；只有断线/超时保留原请求供核对。
        await Yokuli.storage.set({pending:null}); pending = null;
        throw Object.assign(new Error(result.message || result.code), {code:result.message || result.code});
      }
      pending = null; await Yokuli.storage.set({pending:null}); status.hidden = false;
      status.textContent = result.code === 'RESTARTING' ? t('正在切换环境，请稍候…','Switching environments. Please wait…') : result.message || t('已应用', 'Applied');
    } catch (error) { showError(error); }
    finally { busy = false; render(true); }
  }
  const command = (action, extra) => send(Object.assign({action, requestId:crypto.randomUUID().replaceAll('-','')}, extra || {}));
  function render(force) {
    // 轮询不销毁指尖下的按钮/正在编辑的控件；数值可以等下一帧更新。
    if (!force && (interacting || ['INPUT','SELECT','TEXTAREA'].includes(document.activeElement && document.activeElement.tagName))) return;
    controls.replaceChildren(); devices.replaceChildren();
    if (!current) return;
    summary.replaceChildren(ui.node('h2','',current.mode === 'REAL' ? t('真实设备','Real devices') : current.mode === 'REPLAY' ? t('录像回放','Replay') : t('模拟环境','Simulation')),
      ui.node('p','',`${current.paused ? t('已暂停','Paused') : current.rate + '×'} · ${new Date(current.clock.utcMillis).toLocaleString(en ? 'en' : 'zh-CN')}`));
    function button(label, action, needsControl = true) { const result = ui.button(label, action); result.disabled = busy || (needsControl && !controlGranted); controls.append(result); }
    if (!controlGranted) controls.append(ui.status(t('此应用可查看环境。需要操作时，在应用信息中允许演练控制。','This app can view the environment. Allow practice control in App info to make changes.')));
    if (pending) {
      controls.append(ui.status(t('上次操作尚未确认。先核对当前状态，必要时使用原请求重试。','The previous operation is unconfirmed. Review current state; retry only with the original request.')));
      button(t('核对原请求','Reconcile request'), () => send(pending));
      button(t('我已核对，清除待办','Reviewed — clear pending'), async () => { await Yokuli.storage.set({pending:null}); pending = null; render(true); });
    }
    if (current.mode === 'REAL') button(t('进入模拟（结束航行任务后）','Enter simulation after finishing sailing tasks'), () => command('ENTER_SIMULATION'));
    else {
      button(current.paused ? t('继续','Resume') : t('暂停','Pause'), () => command(current.paused ? 'RESUME' : 'PAUSE'));
      [1,2,10,60].forEach(rate => button(`${rate}×`, () => command('SET_RATE',{rate})));
      if (current.paused) button(t('前进 1 秒','Step one second'), () => command('STEP',{stepMillis:1000}));
      button(t('回到真实设备','Return to real devices'), () => command('RETURN_REAL'));
    }
    button(current.recordingId ? t('完成录像','Finish recording') : t('开始录像','Start recording'), () => command(current.recordingId ? 'STOP_RECORDING' : 'START_RECORDING',{name:t('演练控制台','Practice controls')}));
    button(t('在演练室编辑场景','Edit scenario in Hardware Lab'), () => Yokuli.navigation.open('hardware_lab'), false);
    devices.append(ui.node('h2','',t('设备','Devices')));
    current.devices.forEach(device => {
      const item = ui.node('div','yk-section'); item.append(ui.node('p','',`${device.name} · ${device.attached ? t('已接入','Attached') : t('已断开','Detached')} · ${device.frames}`));
      if (current.mode === 'SIMULATION') { item.append(ui.toggle(t('接入设备','Attach device'),device.attached, checked => command(checked ? 'ATTACH' : 'DETACH',{deviceId:device.id}),{disabled:busy || !controlGranted})); }
      devices.append(item);
    });
  }
  try {
    const info = await Yokuli.system.info(); en = info.language !== 'zh-CN';
    root.append(ui.node('h1','',t('演练控制台','Practice controls')),ui.status(t('使用 Core 的同一套设备与虚拟时钟。不会创建网页模拟器。','Uses the shared Core devices and virtual clock. No browser-side simulator.')),status,summary,controls,devices);
    const services = await Yokuli.system.services();
    controlGranted = services.methods.some(method => method.name === 'hardware.control' && method.granted);
    const stored = await Yokuli.storage.get(); pending = stored.value.pending || null;
    Yokuli.hardware.watch(value => { current = value; render(); }, showError);
  } catch (error) { root.append(status); showError(error); }
})();
