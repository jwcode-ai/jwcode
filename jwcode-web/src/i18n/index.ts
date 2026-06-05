import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import zhCN from './locales/zh-CN.json';
import en from './locales/en.json';

const savedSettings = (() => {
  try {
    const raw = localStorage.getItem('jwcode-settings-storage');
    if (raw) {
      const parsed = JSON.parse(raw);
      return parsed?.state?.language || 'zh-CN';
    }
  } catch {}
  return 'zh-CN';
})();

i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    en: { translation: en },
  },
  lng: savedSettings,
  fallbackLng: 'zh-CN',
  interpolation: {
    escapeValue: false,
  },
});

export default i18n;
