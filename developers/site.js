(function () {
  'use strict';
  const button = document.getElementById('language');
  if (window.YokuliHost) {
    document.querySelectorAll('a[href="yokuli-sdk-2.zip"]').forEach(link => {
      const note = document.createElement('span');
      note.dataset.lang = link.dataset.lang;
      note.textContent = link.dataset.lang === 'zh' ? '使用上方「保存 SDK」按钮' : 'Use Save SDK above';
      link.replaceWith(note);
    });
    document.querySelectorAll('a[href^="https://"]').forEach(link => {
      const label = document.createElement('span');
      label.textContent = link.textContent;
      link.replaceWith(label);
    });
  }
  function setLanguage(value) {
    document.documentElement.lang = value;
    button.textContent = value === 'zh-CN' ? 'English' : '中文';
    button.setAttribute('aria-label', value === 'zh-CN' ? 'Switch to English' : '切换到中文');
  }
  setLanguage(navigator.language && navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en');
  button.addEventListener('click', function () { setLanguage(document.documentElement.lang === 'zh-CN' ? 'en' : 'zh-CN'); });
})();
