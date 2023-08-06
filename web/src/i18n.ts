// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

export const languages = [
  {code: "en", englishName: "English", nativeName: "English"},
  {code: "es", englishName: "Spanish", nativeName: "español"},
  {code: "fi", englishName: "Finnish", nativeName: "suomi"},
  {code: "id", englishName: "Indonesian", nativeName: "Indonesia"},
  {code: "it", englishName: "Italian", nativeName: "Italiano"},
  {code: "nl", englishName: "Dutch", nativeName: "Nederlands"},
  {code: "pt", englishName: "Portuguese", nativeName: "Português"},
].sort((a, b) => a.nativeName.localeCompare(b.nativeName, undefined, {sensitivity: 'base'}))

const resources = {
  en: {
    translation: {
      "TerritoryCard.title": "Territory Map Card",
      "TerritoryCard.footer": "Please keep this card in the envelope. Do not soil, mark or bend it. \n Each time the territory is covered, please inform the brother who cares for the territory files."
    }
  },
  es: {
    translation: {
      "TerritoryCard.title": "Tarjeta del Mapa del Territorio",
      "TerritoryCard.footer": "Sirvase mantener esta tarjeta en el sobre. No la manche, ni doble. Cada vez que se haya trabajado \n completamente el territorio, sirvase informarlo al hermano que atiende los archivos del territorio."
    }
  },
  fi: {
    translation: {
      "TerritoryCard.title": "Aluekortti",
      "TerritoryCard.footer": "Pidä tätä korttia kotelossa. Älä tahri äläkä taita sitä äläkä tee siihen mitään merkintöjä. \n Joka kerta kun alue on käyty läpi, ilmoita siitä veljelle, joka hoitaa aluekarttoja."
    }
  },
  id: {
    translation: {
      "TerritoryCard.title": "Kartu Peta Daerah",
      "TerritoryCard.footer": "Mohon simpan kartu ini di dalam amplop. Jangan sampai kotor, tercoret atau terlipat. \n Jika daerah selesai dikerjakan, mohon beritahu saudara yang bertanggung jawab atas arsip daerah."
    }
  },
  it: {
    translation: {
      "TerritoryCard.title": "Piantina del territorio",
      "TerritoryCard.footer": "Tenete questa cartolina nella busta. Non la sciupate, non vi fate segni e non la piegate. Ogni volta che \n il territorio è stato percorso interamente, informate il fratello che si occupa dello schedario dei territori."
    }
  },
  nl: {
    translation: {
      "TerritoryCard.title": "Gebiedskaart",
      "TerritoryCard.footer": "Gelieve deze kaart in het plastic etui te bewaren. Bevlek, beschrijf of vouw deze kaart niet. \n Gelieve iedere keer dat het gebied bewerkt is, de broeder die voor het gebiedssysteem zorgt, hierover in te lichten."
    }
  },
  pt: {
    translation: {
      "TerritoryCard.title": "Cartão de mapa de território",
      "TerritoryCard.footer": "Guarde este cartão no envelope. Tome cuidado para não o manchar, marcar ou dobrar. \n Cada vez que o território for coberto, por favor informe o irmão que cuida do arquivo dos territórios."
    }
  }
};

i18n.use(LanguageDetector)
  .use(initReactI18next)
  // https://www.i18next.com/overview/configuration-options
  .init({
    resources,
    fallbackLng: 'en',
    debug: true,

    interpolation: {
      escapeValue: false, // not needed for react as it escapes by default
    }
  });

export async function changeLanguage(language: string) {
  await i18n.changeLanguage(language);
  // TODO: set the lang for document element after the whole application has been translated
  //document.documentElement.setAttribute('lang', i18n.language);
}

export default i18n;
