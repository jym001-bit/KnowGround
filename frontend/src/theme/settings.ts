/** Default theme settings */
export const themeSettings: App.Theme.ThemeSetting = {
  themeScheme: 'light',
  grayscale: false,
  colourWeakness: false,
  recommendColor: true,
  themeColor: '#8C806E',
  otherColor: { info: '#2080f0', success: '#52c41a', warning: '#faad14', error: '#f5222d' },
  isInfoFollowPrimary: true,
  resetCacheStrategy: 'close',
  layout: { mode: 'vertical', scrollMode: 'content', reverseHorizontalMix: false },
  page: { animate: true, animateMode: 'fade-slide' },
  header: { height: 56, breadcrumb: { visible: false, showIcon: true }, multilingual: { visible: false } },
  tab: { visible: false, cache: true, height: 44, mode: 'chrome' },
  fixedHeaderAndTab: true,
  sider: {
    inverted: false,
    width: 180,
    collapsedWidth: 64,
    mixWidth: 90,
    mixCollapsedWidth: 64,
    mixChildMenuWidth: 200
  },
  footer: { visible: false, fixed: false, height: 48, right: true },
  watermark: { visible: false, text: '知据 KnowGround' },
  tokens: {
    light: {
      colors: {
        container: 'rgb(248, 245, 238)',
        layout: 'rgb(234, 229, 218)',
        inverted: 'rgb(87, 81, 71)',
        'base-text': 'rgb(69, 65, 58)'
      },
      boxShadow: {
        header: '0 1px 2px rgb(82, 72, 54, 0.08)',
        sider: '2px 0 8px 0 rgb(82, 72, 54, 0.05)',
        tab: '0 1px 2px rgb(82, 72, 54, 0.08)'
      }
    },
    dark: { colors: { container: 'rgb(28, 28, 28)', layout: 'rgb(18, 18, 18)', 'base-text': 'rgb(224, 224, 224)' } }
  }
};

/**
 * Override theme settings
 *
 * If publish new version, use `overrideThemeSettings` to override certain theme settings
 */
export const overrideThemeSettings: Partial<App.Theme.ThemeSetting> = {
  themeColor: '#8C806E',
  watermark: { visible: false, text: '知据 KnowGround' }
};
