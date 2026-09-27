/* Yokuli extension SDK 2 (SDK 1 compatible). Capability-routed system calls; no own sensors or background runtime. */
(function (global) {
  'use strict';
  const pending = new Map();
  const subscriptions = new Set();
  let nextId = 0;
  let disposed = false;
  let systemInfo = null;
  let hostPaused = false;
  let resumeRefresh = Promise.resolve();
  global.addEventListener('yokuli:pause', () => { hostPaused = true; });
  global.addEventListener('yokuli:resume', () => {
    hostPaused = false;
    resumeRefresh = systemInfo ? call('system.info').then(applySystemInfo).catch(() => {}) : Promise.resolve();
  });
  function applySystemInfo(info) {
    systemInfo = info;
    document.documentElement.lang = systemInfo.language || 'en';
    document.documentElement.dataset.theme = systemInfo.theme === 'light' ? 'light' : 'dark';
    return systemInfo;
  }
  function failure(code, message) { const error = new Error(message); error.code = code; return error; }
  function call(method, params) {
    if (disposed) return Promise.reject(failure('CLOSED', 'Application is closed'));
    if (!global.YokuliHost || typeof global.YokuliHost.postMessage !== 'function') {
      return Promise.reject(failure('HOST_UNAVAILABLE', 'Open this package in Yokuli OS'));
    }
    if (pending.size >= 32) return Promise.reject(failure('BUSY', 'Too many pending requests'));
    const id = String(++nextId);
    let payload;
    try {
      payload = JSON.stringify({id, method, params: params || {}});
      const bytes = typeof TextEncoder === 'function' ? new TextEncoder().encode(payload).byteLength : new Blob([payload]).size;
      if (bytes > 96 * 1024) throw failure('PAYLOAD_TOO_LARGE', 'Request exceeds 96 KiB');
    } catch (error) { return Promise.reject(error.code ? error : failure('INVALID_ARGUMENT', 'Parameters must be JSON serializable')); }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { pending.delete(id); reject(failure('TIMEOUT', 'Yokuli OS did not respond')); }, 10000);
      pending.set(id, {resolve, reject, timer});
      try { global.YokuliHost.postMessage(payload); }
      catch (error) { clearTimeout(timer); pending.delete(id); reject(failure('TRANSPORT', error.message)); }
    });
  }
  if (global.YokuliHost) global.YokuliHost.onmessage = function (event) {
    let reply;
    try { reply = JSON.parse(event.data); } catch (_) { return; }
    const request = pending.get(String(reply.id));
    if (!request) return;
    clearTimeout(request.timer); pending.delete(String(reply.id));
    if (reply.error) { const error = failure(reply.error.code || 'HOST_ERROR', reply.error.message || 'Operation failed'); error.retryAfterMillis = reply.error.retryAfterMillis; request.reject(error); }
    else request.resolve(reply.result);
  };
  function watchSnapshot(method, callback, onError) {
    let stopped = false, timer = null, running = false;
    async function poll() {
      if (stopped || disposed || document.hidden || hostPaused || running) return;
      running = true;
      try {
        await resumeRefresh;
        if (stopped || disposed || document.hidden || hostPaused) return;
        const value = await call(method);
        await resumeRefresh;
        if (!stopped && !document.hidden && !hostPaused) callback(value);
      }
      catch (error) { if (!stopped && !document.hidden && !hostPaused && onError) onError(error); }
      finally {
        running = false;
        if (!stopped && !disposed && !document.hidden && !hostPaused) timer = setTimeout(poll, 1100);
      }
    }
    function visibility() { clearTimeout(timer); if (!document.hidden && !hostPaused) poll(); }
    function stop() { stopped = true; clearTimeout(timer); document.removeEventListener('visibilitychange', visibility); global.removeEventListener('yokuli:pause', visibility); global.removeEventListener('yokuli:resume', visibility); subscriptions.delete(stop); }
    subscriptions.add(stop); document.addEventListener('visibilitychange', visibility); global.addEventListener('yokuli:pause', visibility); global.addEventListener('yokuli:resume', visibility); poll();
    return stop;
  }
  const zh = () => !systemInfo || systemInfo.language === 'zh-CN';
  function age(timestamp, ageMillis) {
    if (!Number.isFinite(timestamp) && !Number.isFinite(ageMillis)) return zh() ? '尚无测量' : 'Not measured';
    const elapsed = Number.isFinite(ageMillis) ? ageMillis : Date.now() - timestamp;
    const seconds = Math.max(0, Math.floor(elapsed / 1000));
    if (seconds < 5) return zh() ? '刚刚' : 'just now';
    if (seconds < 60) return zh() ? seconds + ' 秒前' : seconds + 's ago';
    if (seconds < 3600) return zh() ? Math.floor(seconds / 60) + ' 分钟前' : Math.floor(seconds / 60) + 'm ago';
    if (seconds < 86400) return zh() ? Math.floor(seconds / 3600) + ' 小时前' : Math.floor(seconds / 3600) + 'h ago';
    if (Number.isFinite(timestamp)) return new Date(timestamp).toLocaleString(zh() ? 'zh-CN' : 'en');
    return zh() ? Math.floor(seconds / 86400) + ' 天前' : Math.floor(seconds / 86400) + 'd ago';
  }
  function quality(value) {
    const labels = {FRESH:['实时','Live'],HELD:['上次读数','Last reading'],STALE:['较早读数','Older reading'],OFF:['已关闭','Off'],MISSING:['尚无数据','No data'],UNAVAILABLE:['尚无数据','No data'],INVALID:['无有效读数','No valid reading']};
    const pair = labels[String(value || '').toUpperCase()];
    return pair ? pair[zh() ? 0 : 1] : (value || (zh() ? '状态未知' : 'Unknown quality'));
  }
  function errorMessage(error) {
    const messages = {
      SDK_VERSION_REQUIRED:['需要更新此应用使用的 SDK 版本。','This app needs a newer SDK declaration.'],
      VOYAGE_ALREADY_ACTIVE:['已有航行记录，已为你保留，请查看当前记录。','A voyage is already active. Review the current recording.'],
      SESSION_CHANGED:['当前航行已改变，请查看后重新选择操作。','The active voyage changed. Review it before acting.'],
      SOURCE_LOCKED:['守锚正在使用船位，暂停值守后再更改。','Anchor Watch is using position. Pause it before changing sources.'],
      SOURCE_CHANGE_PENDING:['正在确认上一次来源选择，请稍等。','The previous source change is still being confirmed.'],
      SHARING_NOT_CONFIGURED:['请先在数据共享中保存端口和分享内容。','Save a port and publication policy in Data Sharing first.'],
      CORE_UNAVAILABLE:['系统正在恢复连接，请稍后重试。','The system is reconnecting. Please try again shortly.'],
      SOURCE_SNAPSHOT_STALE:['来源信息正在更新，请稍后重新选择。','Sources are refreshing. Please choose again shortly.'],
      SYSTEM_PERMISSION_REQUIRED:['需要先在数据中心允许手机定位。','Allow phone location through Data Center first.'],
      LOCATION_DISABLED:['手机系统定位已关闭，请在数据中心查看。','Phone location is switched off. Open Data Center.'],
      SERVICE_UNAVAILABLE:['系统服务暂未就绪，请稍后重试。','This system service is not ready yet. Please try again.'],
      POSITION_REQUIRED:['请先在数据中心选择有效船位来源。','Choose a valid position source in Data Center first.'],
      INVALID_STATE:['状态已改变，请查看当前状态后再操作。','The state has changed. Review it before continuing.'],
      METHOD_NOT_FOUND:['当前系统尚不支持这项能力。','This capability is not supported by this system.'],
      PERMISSION_DENIED:['此应用没有所需权限，请在应用中心查看。','This app does not have the required permission. Check App Center.'],
      RATE_LIMITED:['操作太快，请稍后重试。','Please wait a moment and try again.'],
      SESSION_EXPIRED:['应用或权限已改变，请重新打开。','The app or its permissions changed. Please reopen it.'],
      STORAGE_FAILURE:['没有保存成功，请重试。','Your changes were not saved. Please try again.'],
      TIMEOUT:['系统暂未响应，请稍后重试。','Yokuli OS has not responded. Please try again.'],
      HOST_UNAVAILABLE:['请在 Yokuli OS 中打开此应用。','Open this app inside Yokuli OS.'],
      NOT_FOREGROUND:['先回到此应用，再进行操作。','Return to this app before continuing.'],
      CLOSED:['应用已关闭。','The app has closed.'],
      PAYLOAD_TOO_LARGE:['内容过大，请减少后重试。','This content is too large. Reduce it and try again.']
    };
    const pair = messages[error && error.code];
    return pair ? pair[zh() ? 0 : 1] : (error && error.message || (zh() ? '操作未完成，请重试。' : 'The operation failed. Please try again.'));
  }
  function node(tag, className, text) {
    const result = document.createElement(tag);
    if (className) result.className = className;
    if (text !== undefined) result.textContent = text;
    return result;
  }
  function button(text, action, options) {
    const result = node('button', options && options.secondary ? 'yk-button yk-button-secondary' : 'yk-button', text);
    result.type = 'button';
    result.addEventListener('click', async () => {
      if (result.disabled) return;
      result.disabled = true;
      try { await action(); } catch (error) {
        result.dispatchEvent(new CustomEvent('yokulierror', {bubbles:true, detail:error}));
      } finally { result.disabled = false; }
    });
    return result;
  }
  function metric(label, reading, digits) {
    const card = node('article', 'yk-metric');
    card.append(node('h2', 'yk-label', label));
    const valid = reading && typeof reading.value === 'number' && Number.isFinite(reading.value);
    const line = node('div', 'yk-value-line');
    line.append(node('strong', 'yk-value', valid ? reading.value.toLocaleString(zh() ? 'zh-CN' : 'en', {maximumFractionDigits: digits === undefined ? 1 : digits}) : '—'));
    line.append(node('span', 'yk-unit', reading ? (reading.unit || '') : ''));
    card.append(line);
    const detail = node('div', 'yk-meta');
    detail.append(node('span', '', quality(reading && (reading.freshness || reading.quality))));
    detail.append(node('span', '', age(reading && reading.measuredAt, reading && reading.ageMillis)));
    if (reading && reading.source) detail.append(node('span', 'yk-source', reading.source.name || reading.source.id || '')); 
    card.append(detail); return card;
  }
  function status(message, isError) { return node('p', isError ? 'yk-status yk-error' : 'yk-status', message); }
  function dispose() {
    if (disposed) return; disposed = true;
    subscriptions.forEach(stop => stop());
    pending.forEach(request => { clearTimeout(request.timer); request.reject(failure('CLOSED', 'Application closed')); }); pending.clear();
  }
  global.addEventListener('pagehide', dispose);
  const sdk = {
    version:2,
    system:{info:async () => {
      return applySystemInfo(await call('system.info'));
    }, services:() => call('system.services')},
    marine:{snapshot:() => call('marine.snapshot'), watch:(onData,onError) => watchSnapshot('marine.snapshot',onData,onError)},
    devices:{snapshot:() => call('devices.snapshot'), watch:(onData,onError) => watchSnapshot('devices.snapshot',onData,onError)},
    sources:{
      snapshot:() => call('sources.snapshot'),
      watch:(onData,onError) => watchSnapshot('sources.snapshot',onData,onError),
      select:(metric,sourceId) => call('sources.select',{metric,sourceId})
    },
    nmea:{
      connections:() => call('nmea.connections'),
      watch:(onData,onError) => watchSnapshot('nmea.connections',onData,onError),
      setConnectionEnabled:(id,enabled) => call('nmea.setConnectionEnabled',{id,enabled})
    },
    sharing:{
      snapshot:() => call('sharing.snapshot'),
      watch:(onData,onError) => watchSnapshot('sharing.snapshot',onData,onError),
      setEnabled:enabled => call('sharing.setEnabled',{enabled})
    },
    voyage:{
      snapshot:() => call('voyage.snapshot'),
      watch:(onData,onError) => watchSnapshot('voyage.snapshot',onData,onError),
      receipt:(requestId,recheck) => call('voyage.receipt',{requestId,recheck:recheck === true}),
      command:command => {
        if (!command || typeof command !== 'object' || typeof command.action !== 'string' || typeof command.requestId !== 'string' || !command.requestId.trim()) {
          return Promise.reject(failure('INVALID_ARGUMENT','Voyage commands require an action and a stable requestId'));
        }
        return call('voyage.command',command);
      }
    },
    storage:{get:() => call('storage.get'), set:value => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return Promise.reject(failure('INVALID_ARGUMENT', 'Storage value must be an object'));
      return call('storage.set', {value});
    }},
    navigation:{open:destination => call('navigation.open', {target:destination})},
    ui:{node, button, metric, status, age, quality, errorMessage},
    dispose
  };
  Object.keys(sdk).forEach(key => { if (sdk[key] && typeof sdk[key] === 'object') Object.freeze(sdk[key]); });
  global.Yokuli = Object.freeze(sdk);
})(window);
