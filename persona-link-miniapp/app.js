const CONSENT_STORAGE_KEY = 'personaLinkConsentVersion';
const CONSENT_VERSION = 'v2.0';
const MINIAPP_ROUTES = new Set([
  'pages/home/index',
  'pages/profile/index',
  'subpackages/account/pages/legal/index',
  'subpackages/account/pages/settings/index',
  'subpackages/account/pages/privacy/index',
  'subpackages/account/pages/history/index',
  'subpackages/account/pages/feedback/index',
  'subpackages/test/pages/detail/index',
  'subpackages/test/pages/quiz/index',
  'subpackages/test/pages/result/index',
  'subpackages/pair/pages/detail/index',
  'subpackages/pair/pages/invite/index',
  'subpackages/pair/pages/join/index',
  'subpackages/pair/pages/wait/index',
  'subpackages/pair/pages/result/index'
]);

function buildLaunchUrl(path, query) {
  if (!MINIAPP_ROUTES.has(path)) {
    return '';
  }
  const queryString = Object.keys(query || {})
    .filter((key) => query[key] !== undefined && query[key] !== null)
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(query[key])}`)
    .join('&');
  return `/${path}${queryString ? `?${queryString}` : ''}`;
}

App({
  onLaunch(options) {
    this.skipInitialConsentShow = true;
    this.ensureConsent(options);
  },

  onShow(options) {
    if (this.skipInitialConsentShow) {
      this.skipInitialConsentShow = false;
      return;
    }
    this.ensureConsent(options);
  },

  ensureConsent(options) {
    const source = options || {};
    const pages = getCurrentPages();
    const current = pages[pages.length - 1];
    if ([1007, 1008, 1044, 1096, 1158].includes(Number(source.scene))) {
      const sharedUrl = buildLaunchUrl(source.path, source.query);
      if (sharedUrl) this.globalData.pendingLaunchUrl = sharedUrl;
    }
    // 头像选择和阅读协议返回时，保留正在进行的引导步骤。
    if (current && ['pages/consent/index', 'subpackages/account/pages/legal/index'].includes(current.route)) return;
    if (source.path === 'pages/consent/index' || source.path === 'subpackages/account/pages/legal/index') return;
    this.verifyConsent(buildLaunchUrl(source.path, source.query)).catch(() => {});
  },

  verifyConsent(launchUrl) {
    if (launchUrl) this.consentLaunchUrl = launchUrl;
    if (this.consentCheck) return this.consentCheck;
    this.globalData.hasConsent = false;
    const epoch = this.consentEpoch || 0;
    this.consentCheck = (async () => {
      try {
        const { onboardingProfile, isProfileComplete, TOKEN_STORAGE_KEY, writeProfileCache } = require('./utils/request');
        const token = wx.getStorageSync(TOKEN_STORAGE_KEY);
        if (!token) throw new Error('请先点击微信登录');
        const profile = await onboardingProfile();
        if (!isProfileComplete(profile)) throw new Error('请先设置微信头像和昵称');
        writeProfileCache(token, profile.nickname, profile.avatarUrl);
        const accepted = wx.getStorageSync('personaLinkConsentedDocuments');
        const versions = await require('./utils/request').requestData({
          url: '/api/miniapp/legal-documents/versions', silentUnauthorized: true
        });
        if (epoch !== (this.consentEpoch || 0)) throw new Error('协议状态已变化，请重试');
        if (token !== wx.getStorageSync(TOKEN_STORAGE_KEY)) throw new Error('登录状态已变化，请重新登录');
        const current = wx.getStorageSync(CONSENT_STORAGE_KEY) === CONSENT_VERSION
          && wx.getStorageSync('personaLinkConsentToken') === token
          && Array.isArray(accepted) && Array.isArray(versions)
          && [1, 2, 3].every((type) => {
            const latest = versions.find((document) => Number(document.type) === type);
            return latest && Number(latest.version) > 0 && accepted.some((document) =>
              Number(document.type) === type && Number(document.version) === Number(latest.version));
          });
        if (!current) throw new Error('协议已更新，请重新阅读并同意');
        this.globalData.hasConsent = true;
      } catch (error) {
        if (epoch === (this.consentEpoch || 0)) {
          this.globalData.hasConsent = false;
          const pages = getCurrentPages();
          const current = pages[pages.length - 1];
          if (!this.globalData.consentRedirecting && (!current || !['pages/consent/index', 'subpackages/account/pages/legal/index'].includes(current.route))) {
            this.globalData.pendingLaunchUrl = this.consentLaunchUrl
              || (current ? buildLaunchUrl(current.route, current.options) : '');
            this.globalData.consentRedirecting = true;
            wx.reLaunch({ url: '/pages/consent/index', complete: () => { this.globalData.consentRedirecting = false; } });
          }
        }
        throw error;
      }
    })().finally(() => { this.consentCheck = null; this.consentLaunchUrl = ''; });
    return this.consentCheck;
  },

  returnToHome() {
    this.globalData.resetHomeDetail = true;
    wx.switchTab({ url: '/pages/home/index' });
  },

  globalData: {
    consentStorageKey: CONSENT_STORAGE_KEY,
    consentVersion: CONSENT_VERSION,
    hasConsent: false,
    pendingLaunchUrl: '',
    consentRedirecting: false,
    resetHomeDetail: false
  }
});
