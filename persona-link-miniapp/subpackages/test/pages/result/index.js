const { authenticatedRequestData } = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');
const { createReportAccess } = require('../../../../utils/report-access');

const VIEW_ALIASES = {
  permission: 'permission-denied'
};
const AVAILABLE_VIEWS = ['result', 'permission-denied'];

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

  async loadReport(watch = false) {
    if (!this.reportId) {
      this.setData({ state: 'error', errorDescription: '报告参数缺失，请从测试记录重新进入。' });
      return;
    }
    if (!this.reportAccess) this.reportAccess = createReportAccess(this, `/api/miniapp/reports/${encodeURIComponent(this.reportId)}`);
    return this.reportAccess.run(async (active) => {
      const report = await authenticatedRequestData({
        url: `/api/miniapp/reports/${encodeURIComponent(this.reportId)}`
      });
      if (active()) this.setData({ state: 'ready', result: prepareResult(report) });
    }, watch);
  },

  watchAd() { return this.loadReport(true); },

  onUnload() { if (this.reportAccess) this.reportAccess.dispose(); },
  onHide() { if (this.reportAccess) this.reportAccess.setHidden(true); },
  onShow() { if (this.reportAccess) this.reportAccess.setHidden(false); },

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
