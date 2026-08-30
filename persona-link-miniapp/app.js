const CONSENT_STORAGE_KEY = 'personaLinkConsentVersion';
const CONSENT_VERSION = 'v1.0';
const MINIAPP_ROUTES = new Set([
  'pages/home/index',
  'pages/profile/index',
  'subpackages/account/pages/legal/index',
  'subpackages/account/pages/settings/index',
  'subpackages/account/pages/privacy/index',
  'subpackages/account/pages/history/index',
  'subpackages/account/pages/member/index',
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
    const hasConsented = wx.getStorageSync(CONSENT_STORAGE_KEY) === CONSENT_VERSION;
    const source = options || {};
    this.globalData.hasConsent = hasConsented;
    if (hasConsented || source.path === 'pages/consent/index') {
      this.globalData.consentRedirecting = false;
      return;
    }
    if (this.globalData.consentRedirecting) {
      return;
    }

    this.globalData.pendingLaunchUrl = buildLaunchUrl(source.path, source.query);
    this.globalData.consentRedirecting = true;
    wx.reLaunch({
      url: '/pages/consent/index',
      complete: () => {
        this.globalData.consentRedirecting = false;
      }
    });
  },

  globalData: {
    consentStorageKey: CONSENT_STORAGE_KEY,
    consentVersion: CONSENT_VERSION,
    hasConsent: false,
    pendingLaunchUrl: '',
    consentRedirecting: false
  }
});
