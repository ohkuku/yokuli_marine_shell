(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const words = {
    zh: {
      skip:'查看资料包', developers:'开发者', eyebrow:'OFFLINE / 官方资料库', title:'把海域带在身边。',
      intro:'为 yokuli os 准备的标准离线资料。选好海域，在海图册里使用和管理。', collectionTitle:'选择海域',
      searchLabel:'查找', searchPlaceholder:'海域、国家或资料来源', regionLabel:'地区', allRegions:'所有地区', loading:'正在读取资料目录…',
      archives:'较早版本', archiveIntro:'保留同一标准的历史发行。日常使用请选择上方当前版本。',
      useTitle:'下载后，就在你的海图册里。', step1:'选择资料', step1Body:'每个标准包明确列出实际包含的海图、航行数据与三维内容，下载后交给海图册统一管理。',
      step2:'保存到手机', step2Body:'应用内下载会保存到 Yokuli OS Documents → Chart Packages → 地区。网页下载位置由你的浏览器决定。',
      step3:'在海图册中使用', step3Body:'在海图册打开 .yklpkg，完成导入、整理或按需准备，再选择使用。下载不会自动替换当前资料。',
      footer:'同一份目录，网页与应用同步。', catalogue:'机器可读目录', sources:'来源与制作', download:'下载资料包', uploading:'正在发布，稍后可下载',
      details:'覆盖、来源与文件信息', release:'版本', provider:'来源', license:'许可', filename:'文件', date:'资料下载于', hash:'SHA-256',
      sourceLink:'查看来源', licenseLink:'许可条款', empty:'这个地区暂时没有匹配的资料包。', error:'暂时无法读取目录。请检查网络后重试。',
      retry:'重新加载', copy:'复制摘要', copied:'已复制', copyFailed:'请选择上方摘要手动复制', more:'显示更多',
      content:{charts:'二维海图',data:'深度与设施',navigation:'离线航路',terrain:'三维基础资料',sources:'来源原件'},
      count:n=>`${n} 个海域`, countArchive:n=>`${n} 份`, bytes:v=>v, prepared:'已准备好', pending:'准备发布',
    },
    en: {
      skip:'Browse packages', developers:'Developers', eyebrow:'OFFLINE / OFFICIAL LIBRARY', title:'Take the coast with you.',
      intro:'Standard offline content, prepared for yokuli os. Choose your waters, then use and manage them in Chart Library.', collectionTitle:'Choose your waters',
      searchLabel:'Search', searchPlaceholder:'Waters, country or provider', regionLabel:'Region', allRegions:'All regions', loading:'Loading the catalogue…',
      archives:'Earlier releases', archiveIntro:'Previous releases of the same standard. Choose the current release above for everyday use.',
      useTitle:'Downloaded. Ready for your Library.', step1:'Choose your content', step1Body:'Each standard package lists its actual charts, marine data and 3D content. Download it once and manage everything in Chart Library.',
      step2:'Keep it on your phone', step2Body:'In-app downloads go to Yokuli OS Documents → Chart Packages → region. Your browser chooses where website downloads are saved.',
      step3:'Open in Library', step3Body:'Open the .yklpkg in Chart Library to import, organise and prepare its content, then select it. Downloads never replace your current data.',
      footer:'One catalogue, on the web and in the app.', catalogue:'Catalogue API', sources:'Sources & production', download:'Download package', uploading:'Publishing · available soon',
      details:'Coverage, sources & file information', release:'Version', provider:'Provider', license:'Licence', filename:'Filename', date:'Source download', hash:'SHA-256',
      sourceLink:'Source details', licenseLink:'Licence', empty:'No packages match this region and search.', error:'The catalogue is unavailable right now. Check your connection and try again.',
      retry:'Reload catalogue', copy:'Copy checksum', copied:'Copied', copyFailed:'Select and copy the checksum above', more:'Show more',
      content:{charts:'2D charts',data:'Depth & facilities',navigation:'Offline passages',terrain:'3D base scenes',sources:'Source files'},
      count:n=>`${n} ${n===1?'area':'areas'}`, countArchive:n=>`${n} ${n===1?'file':'files'}`, bytes:v=>v, prepared:'Prepared', pending:'Coming soon',
    },
  };
  let lang = navigator.language?.toLowerCase().startsWith('zh') ? 'zh' : 'en';
  try { const saved=localStorage.getItem('yokuli-language'); if (saved==='zh'||saved==='en') lang=saved; } catch (_) {}
  let catalogue=null, failed=false, shown=24, activeLoad=null;
  const t = key => words[lang][key];
  const localized = (entry,key) => lang==='en' ? entry[`${key}En`]||entry[key] : entry[key]||entry[`${key}En`];
  const element = (tag, className, content) => {const item=document.createElement(tag); if(className)item.className=className; if(content!==undefined)item.textContent=content; return item;};
  function href(value,downloads=false) {
    try {const u=new URL(value); return u.protocol==='https:'&&!u.username&&(!downloads||['github.com','media.githubusercontent.com'].includes(u.hostname))?u.href:null;} catch(_){return null;}
  }
  function link(text,url,className) {const a=element('a',className,text); a.href=url; a.rel='noopener noreferrer'; return a;}
  function byteLabel(bytes) {const power=bytes>=1073741824?3:bytes>=1048576?2:1; return `${(bytes/1024**power).toLocaleString(lang==='zh'?'zh-CN':'en',{maximumFractionDigits:power===3?2:0})} ${['B','KiB','MiB','GiB'][power]}`;}
  function appendFact(dl,label,value,className){if(!value)return; dl.append(element('dt',null,label)); const dd=element('dd',className); if(value instanceof Node)dd.append(value);else dd.textContent=value;dl.append(dd);}
  function packageCard(entry) {
    const card=element('article','package');
    const heading=element('div','package-heading');
    heading.append(element('p','location',`${localized(entry.location,'continentName')} / ${localized(entry.location,'countryName')}`),element('span','country-code',entry.location.country));
    card.append(heading,element('h3',null,localized(entry,'name')),element('p','description',localized(entry,'description')));
    const types=element('ul','content-types'); for(const type of entry.contentTypes||[]){const label=words[lang].content[type];if(label)types.append(element('li',null,label));}card.append(types);
    const meta=element('div','package-meta'); meta.append(element('span',null,entry.releaseVersion),element('span',null,`${byteLabel(entry.bytes)} · .yklpkg`));card.append(meta);
    const downloadUrl=entry.status==='published'?href(entry.downloadUrl,true):null;
    if(downloadUrl){const download=link(t('download'),downloadUrl,'download'); download.setAttribute('aria-label',`${t('download')} · ${localized(entry,'name')} · ${byteLabel(entry.bytes)}`);download.append(element('span','arrow','↓'));card.append(download);}
    else {const pending=element('div','download pending',t('uploading'));pending.setAttribute('aria-disabled','true');card.append(pending);}
    const details=element('details'); details.append(element('summary',null,t('details')));const body=element('div','details-body');
    body.append(element('p',null,localized(entry,'coverageDescription')));
    const dl=element('dl');appendFact(dl,t('release'),entry.releaseVersion);appendFact(dl,t('provider'),entry.provider);appendFact(dl,t('license'),entry.license);appendFact(dl,t('date'),entry.downloadedAt?.slice(0,10));appendFact(dl,t('filename'),entry.fileName);appendFact(dl,t('hash'),entry.sha256,'checksum');body.append(dl);
    const copy=element('button','copy-hash',t('copy'));copy.type='button';copy.addEventListener('click',async()=>{try{await navigator.clipboard.writeText(entry.sha256);copy.textContent=t('copied');}catch(_){copy.textContent=t('copyFailed');}});body.append(copy);
    const notice=localized(entry,'sourceNotice');if(notice)body.append(element('p','notice',notice));
    body.append(element('p','attribution',entry.attribution));const links=element('p','source-links');const sourceUrl=href(entry.sourceUrl), licenseUrl=href(entry.licenseUrl);if(sourceUrl)links.append(link(t('sourceLink'),sourceUrl));if(licenseUrl)links.append(link(t('licenseLink'),licenseUrl));body.append(links);details.append(body);card.append(details);return card;
  }
  function renderRegions(){const value=$('region').value;const regions=new Map();for(const item of catalogue?.collections||[])if(!['withdrawn','draft'].includes(item.status))regions.set(item.location.continent,localized(item.location,'continentName'));$('region').replaceChildren();const all=element('option',null,t('allRegions'));all.value='';$('region').append(all);for(const [key,name]of [...regions].sort((a,b)=>a[1].localeCompare(b[1]))){const option=element('option',null,name);option.value=key;$('region').append(option);}$('region').value=regions.has(value)?value:'';}
  function showStatus(message,retry=false){$('status').hidden=false;$('status').replaceChildren(element('p',null,message));if(retry){const button=element('button',null,t('retry'));button.type='button';button.addEventListener('click',load);$('status').append(button);}}
  function render(){
    $('cards').replaceChildren();$('archives').replaceChildren();$('archive-section').hidden=true;$('more').hidden=true;
    if(!catalogue){showStatus(t(failed?'error':'loading'),failed);return;}
    const search=$('search').value.trim().toLocaleLowerCase(),region=$('region').value;
    const entries=catalogue.collections.filter(entry=>!['withdrawn','draft'].includes(entry.status)&&(!region||entry.location.continent===region)&&(!search||JSON.stringify([entry.name,entry.nameEn,entry.description,entry.descriptionEn,entry.location,entry.provider]).toLocaleLowerCase().includes(search)));
    entries.sort((a,b)=>b.createdAt.localeCompare(a.createdAt));
    const latest=new Set(),main=[],archive=[];
    for(const entry of entries){if(entry.contentTypes.includes('sources')||latest.has(entry.collectionId))archive.push(entry);else{main.push(entry);latest.add(entry.collectionId);}}
    main.sort((a,b)=>Number(b.recommended)-Number(a.recommended)||localized(a,'name').localeCompare(localized(b,'name')));
    $('count').textContent=t('count')(new Set(main.map(e=>e.location.region)).size);
    $('status').hidden=entries.length>0;if(!entries.length)showStatus(t('empty'));
    for(const entry of main.slice(0,shown))$('cards').append(packageCard(entry));$('more').hidden=main.length<=shown;
    for(const entry of archive)$('archives').append(packageCard(entry));$('archive-section').hidden=archive.length===0;$('archive-count').textContent=t('countArchive')(archive.length);
  }
  function translate(){document.documentElement.lang=lang==='zh'?'zh-CN':'en';document.title=lang==='zh'?'海图下载 · yokuli os':'Chart downloads · yokuli os';for(const item of document.querySelectorAll('[data-i18n]')){const word=t(item.dataset.i18n);if(typeof word==='string')item.textContent=word;}$('language').textContent=lang==='zh'?'English':'中文';$('language').lang=lang==='zh'?'en':'zh-CN';$('language').setAttribute('aria-label',lang==='zh'?'Switch to English':'切换到中文');$('search').placeholder=t('searchPlaceholder');$('search').setAttribute('aria-label',t('searchPlaceholder'));renderRegions();render();}
  async function load(){activeLoad?.abort();const controller=new AbortController();activeLoad=controller;failed=false;showStatus(t('loading'));const timeout=setTimeout(()=>controller.abort(),15000);try{const response=await fetch('catalogue.json',{signal:controller.signal,cache:'no-cache'});if(!response.ok)throw Error('Unavailable');const data=await response.json();if(data.format!=='yokuli.chart-catalogue'||data.version!==1||!Array.isArray(data.collections))throw Error('Unsupported catalogue');if(activeLoad!==controller)return;catalogue=data;renderRegions();render();}catch(_){if(activeLoad===controller){failed=true;showStatus(t('error'),true);}}finally{clearTimeout(timeout);}}
  $('language').addEventListener('click',()=>{lang=lang==='zh'?'en':'zh';try{localStorage.setItem('yokuli-language',lang);}catch(_){}translate();});
  $('filters').addEventListener('submit',e=>e.preventDefault());$('search').addEventListener('input',()=>{shown=24;render();});$('region').addEventListener('change',()=>{shown=24;render();});$('more').addEventListener('click',()=>{shown+=24;render();});
  translate();load();
})();
