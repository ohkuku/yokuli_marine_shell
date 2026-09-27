(async function () {
  'use strict';
  const root = document.getElementById('app');
  try {
    const info = await Yokuli.system.info();
    const en = info.language !== 'zh-CN';
    root.append(Yokuli.ui.node('h1', '', en ? 'My boat' : '我的船况'));
    const data = Yokuli.ui.node('section', 'yk-section');
    const errors = Yokuli.ui.status('', true); errors.hidden = true;
    root.append(data, errors);
    Yokuli.marine.watch(snapshot => {
      errors.hidden = true;
      const speed = snapshot.readings.sog;
      data.replaceChildren(Yokuli.ui.metric(en ? 'Speed over ground' : '对地速度', speed));
    }, error => { errors.hidden = false; errors.textContent = Yokuli.ui.errorMessage(error); });
  } catch (error) { root.append(Yokuli.ui.status(Yokuli.ui.errorMessage(error), true)); }
})();
