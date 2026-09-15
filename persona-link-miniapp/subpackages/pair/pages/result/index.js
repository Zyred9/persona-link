const { authenticatedRequestData } = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');

function textOf(content) {
  if (content && typeof content === 'object') return content.text || '';
  return typeof content === 'string' ? content : '';
}

Page({
  data: {
    state: 'loading',
    result: null,
    errorDescription: '暂时无法加载双人报告，请稍后重试。'
  },

  onLoad(options) {
    this.pairSessionId = options.pairSessionId || '';
    this.loadReport();
  },

  async loadReport(watch = false) {
    if (this.disposed || this.loadingAccess) return;
    if (!this.pairSessionId) {
      this.setData({ state: 'error', errorDescription: '配对参数缺失，请从配对进度页重新进入。' });
      return;
    }
    if (!this.reportAccess) {
      this.loadingAccess = true;
      this.setData({ state: 'loading' });
      try {
        // 报告权限逻辑只保留一份，跨分包按需加载，避免占用首页主包。
        const { createReportAccess } = await require.async('../../../test/utils/report-access.js');
        if (this.disposed) return;
        this.reportAccess = createReportAccess(this, `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}/report`);
        this.reportAccess.setHidden(Boolean(this.hidden));
      } catch (error) {
        if (!this.disposed) this.setData({ state: 'error', errorDescription: '报告组件加载失败，请重新加载。' });
        return;
      } finally {
        this.loadingAccess = false;
      }
    }
    if (this.hidden) return;
    return this.reportAccess.run(async (active) => {
      const [report, pair] = await Promise.all([
        authenticatedRequestData({ url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}/report` }),
        authenticatedRequestData({ url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}` })
      ]);
      if (!active()) return;
      const snapshot = report.resultSnapshot || {};
      const mine = pair.myRole === 'INITIATOR' ? snapshot.initiatorResult : snapshot.partnerResult;
      const partner = pair.myRole === 'INITIATOR' ? snapshot.partnerResult : snapshot.initiatorResult;
      const roleName = snapshot.resultName || snapshot.roleName || '';
      this.setData({
        state: 'ready',
        result: {
          title: roleName || `${mine?.resultName || '我'} × ${partner?.resultName || 'TA'}`,
          summary: textOf(snapshot.basicResult) || snapshot.summary || '',
          deepDescription: textOf(snapshot.deepResult),
          actions: Array.isArray(snapshot.actions) ? snapshot.actions : [],
          shareText: textOf(snapshot.shareCopy),
          initiatorName: mine?.resultName || '暂无结果',
          partnerName: partner?.resultName || '暂无结果'
        }
      });
    }, watch);
  },

  watchAd() { return this.loadReport(true); },

  onUnload() { this.disposed = true; if (this.reportAccess) this.reportAccess.dispose(); },
  onHide() { this.hidden = true; if (this.reportAccess) this.reportAccess.setHidden(true); },
  onShow() {
    this.hidden = false;
    if (this.reportAccess) {
      this.reportAccess.setHidden(false);
      if (this.data.state === 'loading') this.loadReport();
    }
  },

  onShareAppMessage() {
    const result = this.data.result || {};
    trackEvent(5, '/subpackages/pair/pages/result/index', this.pairSessionId);
    return {
      title: result.shareText || (result.title ? `我们的测试结果是：${result.title}` : '一起来做双人测试'),
      path: '/pages/home/index'
    };
  },

  retry() {
    this.loadReport();
  }
});
