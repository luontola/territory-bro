import type {StorybookConfig} from "@storybook/react-vite";

const config: StorybookConfig = {
  stories: [
    "../web/src/**/*.mdx",
    "../web/src/**/*.stories.@(js|jsx|mjs|ts|tsx)",
  ],
  staticDirs: [
    "../web/public"
  ],
  addons: [
    "@storybook/addon-links",
    "@storybook/addon-essentials",
    "@storybook/addon-onboarding",
    "@storybook/addon-interactions",
  ],
  framework: {
    name: "@storybook/react-vite",
    options: {}
  },
  features: {
    // XXX: We can't avoid using the deprecated storiesOf, because TerritoryMap.stories.tsx generates stories dynamically
    //      for every map raster. Need to wait for a replacement, presumably in Storybook 8. https://github.com/storybookjs/storybook/issues/9828
    storyStoreV7: false
  },
  docs: {
    autodocs: "tag"
  }
};
export default config;
