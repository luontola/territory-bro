import i18n from "i18next";
import resources from "virtual:i18next-loader";

export {resources};

export const languages = Object.entries(resources)
  .map(([code, resource]: any) => ({
    code: code,
    englishName: resource.translation.englishName as string,
    nativeName: resource.translation.nativeName as string,
  }))
  .sort((a, b) => a.nativeName.localeCompare(b.nativeName, undefined, {sensitivity: 'base'}))

// https://www.i18next.com/overview/configuration-options
i18n.init({
  resources,
  // document is not defined when running server-export on Node.js
  lng: typeof document !== 'undefined' ? document.documentElement.lang : undefined,
  fallbackLng: 'en',
  // debug: import.meta.env.DEV,
  interpolation: {
    escapeValue: false // the templates already escape by default
  },
  react: {
    transKeepBasicHtmlNodesFor: ['p', 'br', 'strong', 'em', 'i', 'u', 'b', 'kbd', 'wbr']
  }
});

export default i18n;
