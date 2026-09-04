const {
  authenticatedRequestData,
  ensureSession,
  TOKEN_STORAGE_KEY
} = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');

const VIEW_ALIASES = {
  ad: 'ad-failure',
  permission: 'permission-denied',
  login: 'login-gate'
};
const AVAILABLE_VIEWS = ['result', 'ad-failure', 'permission-denied', 'login-gate'];
const MEMBER_PAGE_URL = '/subpackages/account/pages/member/index';

function textOf(content) {
  if (content && typeof content === 'object') return content.text || '';
  return typeof content === 'string' ? content : '';
}

function prepareResult(report) {
  const snapshot = report && report.resultSnapshot ? report.resultSnapshot : {};
  return {
    name: snapshot.resultName || report.resultCode || '未命名类型',
    description: textOf(snapshot.basicResult),
    deepDescription: textOf(snapshot.deepResult),
    shareText: textOf(snapshot.shareCopy),
    dimensions: (Array.isArray(snapshot.dimensions) ? snapshot.dimensions : []).map((dimension, index) => ({
      dimensionId: String(dimension.dimensionId),
      dimensionName: dimension.dimensionName,
      normalizedScore: Math.max(0, Math.min(100, Math.round(Number(dimension.normalizedScore) || 0))),
      barTone: index % 2 === 0 ? 'coral' : 'mint'
    }))
  };
}

Page({
  data: {
    state: 'loading',
    view: 'result',
    result: null,
    authorizing: false,
    errorDescription: '暂时无法加载报告，请稍后重试。'
  },

  onLoad(options) {
    this.reportId = options.reportId || '';
    const requestedView = VIEW_ALIASES[options.state] || options.state;
    const view = AVAILABLE_VIEWS.includes(requestedView) ? requestedView : 'result';
    this.setData({ view });
    if (view !== 'result') {
      this.setData({ state: 'ready' });
      return;
    }
    this.loadReport();
  },

  async loadReport() {
    if (!this.reportId) {
      this.setData({ state: 'error', errorDescription: '报告参数缺失，请从测试记录重新进入。' });
      return;
    }
    this.setData({ state: 'loading' });
    try {
      const report = await authenticatedRequestData({
        url: `/api/miniapp/reports/${encodeURIComponent(this.reportId)}`
      });
      this.setData({ state: 'ready', result: prepareResult(report) });
    } catch (error) {
      this.setData({ state: 'error', errorDescription: error.message || '报告加载失败' });
    }
  },

  onShareAppMessage() {
    const result = this.data.result || {};
    trackEvent(5, '/subpackages/test/pages/result/index', this.reportId);
    return {
      title: result.shareText || (result.name ? `我的测试结果是：${result.name}` : '来测测你的个性类型'),
      path: '/pages/home/index'
    };
  },

  retry() {
    this.loadReport();
  },

  watchVideo() {
    this.setData({ view: 'ad-failure' });
  },

  retryVideo() {
    this.returnResult();
    wx.showToast({ title: '视频入口已重新加载', icon: 'none' });
  },

  openMember() {
    if (wx.getStorageSync(TOKEN_STORAGE_KEY)) {
      wx.navigateTo({ url: MEMBER_PAGE_URL });
      return;
    }
    this.setData({ view: 'login-gate' });
  },

  async authorizeLogin() {
    if (this.data.authorizing) return;
    this.setData({ authorizing: true });
    try {
      await ensureSession();
      this.setData({ authorizing: false, view: 'result' });
      wx.navigateTo({ url: MEMBER_PAGE_URL });
    } catch (error) {
      this.setData({ authorizing: false });
      wx.showToast({ title: error.message || '暂时无法登录，请稍后重试', icon: 'none' });
    }
  },

  returnResult() {
    if (this.data.result) {
      this.setData({ state: 'ready', view: 'result' });
      return;
    }
    if (this.reportId) {
      this.setData({ view: 'result' });
      this.loadReport();
      return;
    }
    getApp().returnToHome();
  }
});
