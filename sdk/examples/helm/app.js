(async function () {
  'use strict';
  const root=document.getElementById('app'), ui=Yokuli.ui;
  let en=false, tab='devices', catalog=null, busy=false, pending=null, storage={}, receipt=null, confirmation=false;
  let live={}, errors={}, stops=[], lastCatalog=0, refreshing=false, refreshingReceipt=false, interaction=false;
  const t=(zh,english)=>en?english:zh;
  const methods=()=>catalog&&Array.isArray(catalog.methods)?catalog.methods:[];
  const method=name=>methods().find(item=>item.name===name);
  const allowed=name=>{const item=method(name);return item&&item.declared&&item.granted;};
  const can=name=>{const item=method(name);return !busy&&item&&item.available;};
  const message=error=>ui.errorMessage(error);
  const statusNames={READY:['就绪','Ready'],LIVE:['实时','Live'],HELD:['保留上次数据','Last reading'],WAITING:['等待读数','Waiting for data'],PERMISSION_REQUIRED:['需要授权','Permission required'],DISABLED:['系统已关闭','Disabled in system'],RECONNECTING:['重新连接中','Reconnecting'],CONNECTED:['已连接','Connected'],CONNECTING:['连接中','Connecting'],DISCONNECTED:['已断开','Disconnected'],STOPPED:['已关闭','Stopped'],RUNNING:['运行中','Running'],STARTING:['启动中','Starting'],STOPPING:['正在关闭','Stopping'],ERROR:['需要处理','Needs attention'],FAILED:['未完成','Failed'],AVAILABLE:['可使用','Available'],UNAVAILABLE:['尚不可用','Unavailable'],MISSING:['未发现','Not detected'],HEALTHY:['正常','Healthy'],DEGRADED:['数据较早','Older data'],STALE:['数据较早','Older data'],OFF:['已关闭','Off'],IDLE:['尚未记录','Not recording'],RECORDING:['正在记录','Recording'],PAUSED:['已暂停','Paused'],SAVING:['正在保存','Saving'],UNKNOWN:['等待确认','Awaiting confirmation'],QUEUED:['已提交','Queued'],EXECUTING:['执行中','In progress'],CONFIRMED:['已完成','Completed'],REJECTED:['未执行','Not applied']};
  const nameOf=value=>{const pair=statusNames[String(value||'').toUpperCase()];return pair?t(pair[0],pair[1]):value||t('状态未知','Unknown state');};
  function explanation(name) {const item=method(name);return !item?t('当前系统没有此能力。','This system does not offer this capability.'):!item.granted?t('尚未获得权限，请在应用中心查看。','Permission is not granted. Check App Center.'):t('系统服务正在准备，请稍后操作。','The system service is getting ready. Please wait.');}
  function fail(key,error){errors[key]=message(error);if(key==='sources'&&live.sources)live.sources.current=false;render();}
  async function refreshCatalog(force) {
    if(refreshing||(!force&&performance.now()-lastCatalog<3000))return;
    refreshing=true;
    try{catalog=await Yokuli.system.services();lastCatalog=performance.now();}
    catch(error){errors.system=message(error);}
    finally{refreshing=false;render();}
  }
  const header=ui.node('header','yk-header'), tabs=ui.node('nav','helm-tabs'), notice=ui.status('',true), panel=ui.node('div');
  notice.setAttribute('role','alert');notice.hidden=true;
  try{
    en=(await Yokuli.system.info()).language!=='zh-CN';
    catalog=await Yokuli.system.services();
    storage=(await Yokuli.storage.get()).value||{};
    if(storage.pendingVoyage&&typeof storage.pendingVoyage.requestId==='string')pending=storage.pendingVoyage;
  }catch(error){root.append(ui.status(message(error),true),ui.button(t('重新打开','Reopen'),()=>window.location.reload()));return;}
  header.append(ui.node('p','yk-eyebrow',t('船上控制台','Boat controls')),ui.node('h1','',t('船上的事，在这里','Your boat, connected')));
  root.append(header,tabs,notice,panel);tabs.setAttribute('role','tablist');
  [['devices','设备与来源','Devices'],['connections','连接与分享','Connections'],['voyage','航行记录','Recording']].forEach(([id,zh,english])=>{
    const button=ui.node('button','',t(zh,english));button.type='button';button.setAttribute('role','tab');button.dataset.tab=id;
    button.addEventListener('click',()=>{if(!busy){tab=id;confirmation=false;subscribe();render();}});tabs.append(button);
  });
  root.addEventListener('yokulierror',event=>fail('action',event.detail));
  root.addEventListener('pointerdown',()=>{interaction=true;});
  window.addEventListener('pointerup',()=>{interaction=false;});window.addEventListener('pointercancel',()=>{interaction=false;});
  root.addEventListener('focusout',()=>setTimeout(render,0));
  let swipe=null;
  panel.addEventListener('pointerdown',event=>{
    if(busy||event.target.closest('button,select,input,label,a'))return;
    swipe={id:event.pointerId,x:event.clientX,y:event.clientY,delta:0,active:false};
  });
  panel.addEventListener('pointermove',event=>{
    if(!swipe||swipe.id!==event.pointerId)return;
    const dx=event.clientX-swipe.x,dy=event.clientY-swipe.y;
    if(!swipe.active&&Math.abs(dy)>12&&Math.abs(dy)>Math.abs(dx)){swipe=null;return;}
    if(!swipe.active&&Math.abs(dx)>12&&Math.abs(dx)>Math.abs(dy)*1.4){swipe.active=true;panel.setPointerCapture(event.pointerId);}
    if(swipe.active){swipe.delta=dx;panel.style.transform='translateX('+Math.max(-72,Math.min(72,dx*.35))+'px)';}
  });
  function finishSwipe(event){
    if(!swipe||swipe.id!==event.pointerId)return;
    const ended=swipe;swipe=null;panel.style.transform='';
    if(event.type!=='pointercancel'&&ended.active&&Math.abs(ended.delta)>65){
      const order=['devices','connections','voyage'],index=order.indexOf(tab),next=Math.max(0,Math.min(order.length-1,index+(ended.delta<0?1:-1)));
      if(next!==index){tab=order[next];confirmation=false;subscribe();render(true);}
    }
  }
  panel.classList.add('helm-panel');panel.addEventListener('pointerup',finishSwipe);panel.addEventListener('pointercancel',finishSwipe);
  function section(title){const block=ui.node('section','yk-section');block.append(ui.node('h2','yk-section-title',title));return block;}
  function control(label,name,action){const button=ui.button(label,action,{secondary:true});button.disabled=!can(name);return button;}
  function row(title,subtitle){const block=ui.node('article','helm-row');block.append(ui.node('h3','',title));if(subtitle)block.append(ui.status(subtitle));return block;}
  async function operate(key,action){
    if(busy)return;busy=true;delete errors.action;render(true);
    try{await action();}
    catch(error){errors.action=message(error);}
    finally{busy=false;await refreshCatalog(true);render(true);}
  }
  function permissionNote(block,name){if(!method(name)||!method(name).available)block.append(ui.status(explanation(name)));}
  function renderDevices(){
    const sources=section(t('船位来源','Position source')), state=live.sources;
    if(state){
      const pos=state.position,select=ui.node('select','helm-source');select.setAttribute('aria-label',t('船位来源','Position source'));
      const current=pos.mode==='phone'?'phone':pos.mode==='nmea'?'connection:'+pos.selectedConnectionId:'';
      pos.options.forEach(option=>{const element=ui.node('option','',option.name);element.value=option.sourceId||'';element.selected=element.value===current;element.disabled=!option.available&&element.value!==current;select.append(element);});
      if(pos.mode!=='demo'&&!Array.from(select.options).some(option=>option.value===current)){const unavailable=ui.node('option','',t('当前来源暂不可用','Current source unavailable'));unavailable.value=current;unavailable.selected=true;unavailable.disabled=true;select.append(unavailable);}
      if(pos.mode==='demo'){const demo=ui.node('option','',t('演示来源（由系统管理）','Demo source (managed by system)'));demo.value='demo';demo.selected=true;demo.disabled=true;select.append(demo);}
      select.disabled=!can('sources.select')||state.current===false||pos.locked||pos.selectionPending;
      select.addEventListener('change',()=>operate('sources',async()=>{const result=await Yokuli.sources.select('POSITION',select.value||null);live.sources=result.state;}));
      sources.append(select);
      if(pos.locked)sources.append(ui.status(t('活动任务正在使用船位，请先处理当前任务。','An active task is using this position source. Review that task first.')));
      if(pos.selectionPending)sources.append(ui.status(t('正在应用来源选择…','Applying the source selection…')));
      permissionNote(sources,'sources.select');
    }else sources.append(ui.status(errors.sources||explanation('sources.snapshot')));
    panel.append(sources);
    const devices=section(t('真实设备','Real devices')), snapshot=live.devices;
    if(snapshot&&snapshot.ready){
      if(!snapshot.devices.length)devices.append(ui.status(t('系统尚未报告设备。','The system has not reported any devices.')));
      snapshot.devices.forEach(device=>{
        const deviceNames={GNSS:['手机定位','Phone GPS'],IMU:['手机姿态','Phone motion'],PRESSURE:['手机气压计','Phone barometer']};
        const named=deviceNames[device.kind];
        const block=row(named?t(named[0],named[1]):device.name,[nameOf(device.health),device.active?t('正在使用','In use'):nameOf(device.availability),ui.age(null,device.measurementAgeMillis)].join(' · '));
        if(device.capabilities.length)block.append(ui.node('p','helm-quiet',device.capabilities.map(capability=>metricName(capability)).join(' · ')));
        if(device.reason)block.append(ui.status(reasonName(device.reason)));devices.append(block);
      });
    }else devices.append(ui.status(errors.devices||(snapshot&&snapshot.error?reasonName(snapshot.error):!allowed('devices.snapshot')?explanation('devices.snapshot'):t('设备目录尚未就绪，将自动继续读取。','The device inventory is not ready. Updates will resume automatically.'))));
    panel.append(devices);
    if(state){
      const metrics=section(t('各项数据用谁的','Choose each data source'));
      state.metrics.filter(item=>item.metric!=='POSITION'&&item.candidates.length>0).forEach(metric=>{
        const block=row(metricName(metric.metric)),select=ui.node('select','helm-source');select.setAttribute('aria-label',metricName(metric.metric));
        const auto=ui.node('option','',t('由数据中心自动选择','Automatic selection'));auto.value='';select.append(auto);
        metric.candidates.forEach(candidate=>{const option=ui.node('option','',candidate.name+' · '+ui.age(candidate.measuredAt,candidate.ageMillis));option.value=candidate.sourceId;select.append(option);});
        if(metric.pinnedSourceId&&!metric.candidates.some(candidate=>candidate.sourceId===metric.pinnedSourceId)){const unavailable=ui.node('option','',t('已选择的来源暂不可用','Selected source unavailable'));unavailable.value=metric.pinnedSourceId;unavailable.disabled=true;select.append(unavailable);}
        select.value=metric.pinnedSourceId||'';select.disabled=!can('sources.select')||state.current===false||!metric.selectable;
        select.addEventListener('change',()=>operate('sources',async()=>{live.sources=(await Yokuli.sources.select(metric.metric,select.value||null)).state;}));
        block.append(select);if(metric.conflict)block.append(ui.status(t('来源之间存在差异，请在数据中心确认。','Sources disagree. Review them in Data Center.')));metrics.append(block);
      });
      if(metrics.children.length>1)panel.append(metrics);
    }
    panel.append(control(t('在数据中心查看','Open Data Center'),'navigation.open',()=>Yokuli.navigation.open('data_center')));
  }
  function metricName(key){const labels={'position':['船位','Position'],'speed.overGround':['对地速度','Speed over ground'],'course.overGround':['航迹向','Course over ground'],'altitude':['海拔','Altitude'],'orientation.device':['设备方向','Device orientation'],'angularVelocity':['转动速度','Rotation rate'],'acceleration.linear':['线性加速度','Linear acceleration'],'magneticField':['磁场','Magnetic field'],'pressure.air':['气压','Air pressure'],'nmea.receive':['接收数据','Receive data'],'nmea.send':['发送数据','Send data'],'nmea0183.tcp':['NMEA · TCP','NMEA · TCP'],'nmea0183.udp':['NMEA · UDP','NMEA · UDP'],POSITION:['船位','Position'],SOG:['对地速度','Speed over ground'],SPEED_OVER_GROUND:['对地速度','Speed over ground'],COG:['航迹向','Course over ground'],COURSE_OVER_GROUND:['航迹向','Course over ground'],HEADING_TRUE:['船首向','Heading'],HEADING:['船首向','Heading'],PRESSURE:['气压','Pressure'],BAROMETRIC_PRESSURE:['气压','Pressure'],DEPTH:['水深','Depth'],WIND:['风','Wind'],IMU:['姿态','Attitude'],HEEL:['横倾','Heel'],PITCH:['纵倾','Pitch'],AIR_TEMPERATURE:['气温','Air temperature'],WATER_TEMPERATURE:['水温','Water temperature']};const pair=labels[key];return pair?t(pair[0],pair[1]):key;}
  function reasonName(code){
    const names={HARDWARE_MISSING:['手机没有此传感器。','This sensor is not present on this phone.'],PRECISE_LOCATION_PERMISSION_REQUIRED:['请在系统权限中允许精确定位。','Allow precise location in Android permissions.'],ANDROID_LOCATION_DISABLED:['手机系统的定位开关已关闭。','Location is switched off in Android settings.'],LOCATION_DRIVER_ERROR:['定位服务遇到问题，请在数据中心查看。','The location service needs attention. Open Data Center.'],POSITION_SELECTION_PENDING:['正在等待所选船位来源。','Waiting for the selected position source.'],SENSOR_START_FAILED:['传感器未能启动，请在数据中心查看。','The sensor could not start. Open Data Center.'],SENSOR_ACCURACY_UNRELIABLE:['传感器精度较低，请检查手机固定位置及附近磁场。','Sensor accuracy is low. Check the phone mount and nearby magnetic interference.'],NMEA_TRANSPORT_ERROR:['连接未建立，请检查设备、网络与连接配置。','Connection failed. Check the device, network and connection settings.'],POSITION_REQUIRED:['开始记录前，请选择有效船位来源。','Choose a valid position source before recording.'],COMMAND_PENDING:['上一项操作尚未完成，请等待确认。','The previous action is awaiting confirmation.'],CORE_RESTARTED_CONFIRMING:['系统已重连，正在核对上一次操作。','The system reconnected and is reconciling the last action.'],CONFIRMATION_DELAYED:['操作确认较慢，请稍后重新确认。','Confirmation is taking longer. Check again shortly.']};
    const pair=names[code];return pair?t(pair[0],pair[1]):/^[A-Z_]+$/.test(String(code))?t('系统暂未就绪，请稍后重试。','The system is not ready yet. Please try again.'):String(code);
  }
  function renderConnections(){
    const sectionConnections=section(t('船上的连接','Boat connections')), network=live.nmea;
    if(network){
      if(!network.connections.length)sectionConnections.append(ui.status(t('尚无连接。先在船联网里添加设备。','No connections yet. Add a device in Connections first.')));
      network.connections.forEach(connection=>{
        const block=row(connection.name,[connection.transport,(connection.current===false?t('上次状态：','Last known: '):'')+nameOf(connection.state)].join(' · '));
        block.append(ui.status(t('收 ','Received ')+connection.received+t(' · 发 ',' · Sent ')+connection.sent+' · '+ui.age(null,connection.lastReceivedAgeMillis)));
        const desired=!connection.requested;
        block.append(control(desired?t('连接','Connect'):t('断开','Disconnect'),'nmea.setConnectionEnabled',()=>operate('nmea',async()=>{await Yokuli.nmea.setConnectionEnabled(connection.id,desired);live.nmea=await Yokuli.nmea.connections();})));
        sectionConnections.append(block);
      });
    }else sectionConnections.append(ui.status(errors.nmea||explanation('nmea.connections')));
    permissionNote(sectionConnections,'nmea.setConnectionEnabled');
    sectionConnections.append(control(t('管理连接','Manage connections'),'navigation.open',()=>Yokuli.navigation.open('nmea')));panel.append(sectionConnections);
    const sharing=section(t('本机分享','Share from this phone')), state=live.sharing;
    if(state){
      sharing.append(ui.status([nameOf(state.state),t('接入设备 ','Clients ')+state.clientCount].join(' · ')));
      if(state.configured)sharing.append(ui.status(t('端口 ','Port ')+state.port+t(' · 已发送 ',' · Sent ')+state.sentSentences));
      if(state.message)sharing.append(ui.status(state.message));
      if(state.configured){const desired=!state.requested;sharing.append(control(desired?t('开启分享','Start sharing'):t('关闭分享','Stop sharing'),'sharing.setEnabled',()=>operate('sharing',async()=>{live.sharing=(await Yokuli.sharing.setEnabled(desired)).state;})));}
      else sharing.append(ui.status(t('先在「数据共享」里选择要分享的数据与端口。','Choose data and a port in Data Sharing before enabling it.')),control(t('设置数据分享','Set up sharing'),'navigation.open',()=>Yokuli.navigation.open('local_nmea')));
      permissionNote(sharing,'sharing.setEnabled');
    }else sharing.append(ui.status(errors.sharing||explanation('sharing.snapshot')));panel.append(sharing);
  }
  function requestId(){const bytes=new Uint8Array(16);crypto.getRandomValues(bytes);return 'helm_'+Array.from(bytes,value=>value.toString(16).padStart(2,'0')).join('');}
  async function savePending(value){const next=Object.assign({},storage,{pendingVoyage:value});await Yokuli.storage.set(next);storage=next;pending=value;}
  async function applyReceipt(result){
    if(result.state)live.voyage=result.state;receipt=result.receipt;
    if(receipt&&receipt.terminal){
      if(receipt.status!=='CONFIRMED')errors.action=receipt.reason?reasonName(receipt.reason):nameOf(receipt.status);
      await savePending(null);
    }
  }
  async function issue(action){
    if(busy)return;
    await operate('voyage',async()=>{
      const current=live.voyage;
      if(!pending){const command={action,requestId:requestId()};if(action!=='start')command.sessionId=current.sessionId;await savePending(command);}
      try { await applyReceipt(await Yokuli.voyage.command(pending)); confirmation=false; }
      catch (error) {
        // 这些明确拒绝发生在 Core 投递之前；清除未发送的草稿，允许用户操作当前会话。
        // 超时、断线、撤权后结果不明仍保留原 ID，绝不自动发起另一条记录。
        if (['VOYAGE_ALREADY_ACTIVE','SESSION_CHANGED','INVALID_ARGUMENT','PERMISSION_DENIED','NOT_FOREGROUND'].includes(error.code)) {
          await savePending(null); receipt=null;
        }
        throw error;
      }
    });
  }
  async function checkReceipt(recheck){
    if(!pending||refreshingReceipt)return;refreshingReceipt=true;
    try{await applyReceipt(await Yokuli.voyage.receipt(pending.requestId,recheck));}
    catch(error){errors.voyage=message(error);}
    finally{refreshingReceipt=false;render();}
  }
  function duration(milliseconds){const minutes=Math.max(0,Math.floor((milliseconds||0)/60000));return Math.floor(minutes/60)+t(' 小时 ','h ')+(minutes%60)+t(' 分钟','m');}
  function renderVoyage(){
    const state=live.voyage,sectionVoyage=section(t('航行记录','Voyage recording'));
    if(!state){sectionVoyage.append(ui.status(errors.voyage||explanation('voyage.snapshot')));panel.append(sectionVoyage);return;}
    sectionVoyage.append(ui.node('h3','',nameOf(state.phase)));
    if(state.name)sectionVoyage.append(ui.status(state.name));
    if(state.sessionId){const grid=ui.node('div','helm-grid');[['记录时长','Recorded time',duration(state.elapsedMillis)],['日志条目','Moments',String(state.momentCount)]].forEach(([zh,english,value])=>{const block=ui.node('div');block.append(ui.node('p','helm-quiet',t(zh,english)),ui.node('p','helm-number',value));grid.append(block);});sectionVoyage.append(grid);}
    if(pending){
      sectionVoyage.append(ui.status(t('正在确认上一次操作。重复查询不会新建记录。','Confirming your last action. Checking again does not create another recording.')));
      if(receipt)sectionVoyage.append(ui.status(nameOf(receipt.status)));
      const actions=ui.node('div','yk-actions');actions.append(control(t('重新确认','Check again'),'voyage.receipt',()=>checkReceipt(true)),control(t('重试同一操作','Retry this action'),'voyage.command',()=>issue(pending.action)));sectionVoyage.append(actions);
    }else if(!state.commandPending){
      const actions=ui.node('div','yk-actions');
      if(state.phase==='IDLE')actions.append(control(t('开始记录','Start recording'),'voyage.command',()=>issue('start')));
      if(state.phase==='RECORDING')actions.append(control(t('暂停','Pause'),'voyage.command',()=>issue('pause')));
      if(state.phase==='PAUSED')actions.append(control(t('继续记录','Resume'),'voyage.command',()=>issue('resume')));
      if(['RECORDING','PAUSED'].includes(state.phase))actions.append(control(t('结束记录','Finish recording'),'voyage.command',()=>{confirmation=true;render(true);}));
      sectionVoyage.append(actions);
    }else sectionVoyage.append(ui.status(t('系统正在处理记录操作…','The system is applying a recording command…')));
    if(confirmation&&!pending){const confirm=ui.node('div','helm-confirm');confirm.append(ui.node('p','',t('结束本次记录并保存到航行日志？','Finish this recording and save it to the log?')),control(t('结束并保存','Finish & save'),'voyage.command',()=>issue('end')),ui.button(t('继续记录','Keep recording'),()=>{confirmation=false;render(true);},{secondary:true}));sectionVoyage.append(confirm);}
    permissionNote(sectionVoyage,'voyage.command');panel.append(sectionVoyage);
    panel.append(control(t('查看航行日志','Open the log'),'navigation.open',()=>Yokuli.navigation.open('voyages')));
  }
  function render(force){
    if(!force&&(interaction||['SELECT','INPUT'].includes(document.activeElement&&document.activeElement.tagName)))return;
    tabs.querySelectorAll('button').forEach(button=>{button.setAttribute('aria-selected',String(button.dataset.tab===tab));button.disabled=busy;});
    const visibleErrors=tab==='devices'?['devices','sources']:tab==='connections'?['nmea','sharing']:['voyage'];
    const error=errors.action||errors.system||visibleErrors.map(key=>errors[key]).find(Boolean);notice.textContent=error||'';notice.hidden=!error;panel.replaceChildren();
    if(busy){const progress=ui.node('div','helm-indicator');progress.append(ui.node('span'));progress.setAttribute('aria-label',t('处理中','Working'));panel.append(progress);}
    if(tab==='devices')renderDevices();else if(tab==='connections')renderConnections();else renderVoyage();
    const relevant=tab==='devices'?['devices.snapshot','sources.snapshot','sources.select']:tab==='connections'?['nmea.connections','nmea.setConnectionEnabled','sharing.snapshot','sharing.setEnabled']:['voyage.snapshot','voyage.command'];
    if(relevant.some(name=>method(name)&&!method(name).granted)){
      const permission=section(t('应用权限','App permissions'));
      permission.append(ui.status(t('查看设备与控制系统分开授权。你可以只开放需要的操作。','Reading devices and controlling the system are separate permissions. Enable only the actions you need.')),control(t('在应用中心管理权限','Manage permissions in App Center'),'navigation.open',()=>Yokuli.navigation.open('app_center')));panel.append(permission);
    }
  }
  function subscribe(){
    stops.forEach(stop=>stop());stops=[];
    const needed=tab==='devices'?['devices','sources']:tab==='connections'?['nmea','sharing']:['voyage'];
    needed.forEach(key=>{const name=key==='nmea'?'nmea.connections':key+'.snapshot';if(!allowed(name))return;
      stops.push(Yokuli[key].watch(value=>{live[key]=value;delete errors[key];refreshCatalog(false);if(key==='voyage'&&pending)checkReceipt(false);render();},error=>fail(key,error)));
    });
  }
  window.addEventListener('yokuli:resume',()=>refreshCatalog(true));
  window.addEventListener('pagehide',()=>stops.forEach(stop=>stop()));
  subscribe();render();
})();
