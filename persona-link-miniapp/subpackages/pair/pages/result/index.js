const { authenticatedRequestData } = require('../../../../utils/request');
const { trackEvent } = require('../../../../utils/analytics');
const { createReportAccess } = require('../../../../utils/report-access');

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
    if (!this.pairSessionId) {
      this.setData({ state: 'error', errorDescription: '配对参数缺失，请从配对进度页重新进入。' });
      return;
    }
    if (!this.reportAccess) this.reportAccess = createReportAccess(this, `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}/report`);
    return this.reportAccess.run(async (active) => {
      const [report, pair] = await Promise.all([
        authenticatedRequestData({ url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}/report` }),
        authenticatedRequestData({ url: `/api/miniapp/pairs/${encodeURIComponent(this.pairSessionId)}` })
      ]);
      if (!active()) return;
      const snapshot = report.resultSnapshot || {};
      const mine = pair.myRole === 'INITIATOR' ? snapshot.initiatorResult : snapshot.partnerResult;
      const partner = pair.myRole === 'INITIATOR' ? snapshot.partnerResult : snapshot.initiatorResult;
      const roleName = snapshot.roleName
        || (snapshot.summary ? (snapshot.summary.includes('较为接近') ? '默契搭子' : '互补搭子') : '');
      const fallbackActions = roleName === '互补搭子'
        ? ['先说清彼此不同的期待', '把差异变成可以商量的选择', '给对方留出适合自己的节奏']
        : ['有分歧也愿意说清楚', '会把彼此的感受放进计划', '能在日常里给对方稳定回应'];
      this.setData({
        state: 'ready',
        result: {
          title: roleName || `${mine?.resultName || '我'} × ${partner?.resultName || 'TA'}`,
          summary: snapshot.summary || '',
          actions: Array.isArray(snapshot.actions) && snapshot.actions.length > 0
            ? snapshot.actions
            : fallbackActions,
          initiatorName: mine?.resultName || '暂无结果',
          partnerName: partner?.resultName || '暂无结果'
        }
      });
    }, watch);
  },

  watchAd() { return this.loadReport(true); },

  onUnload() { if (this.reportAccess) this.reportAccess.dispose(); },
  onHide() { if (this.reportAccess) this.reportAccess.setHidden(true); },
  onShow() { if (this.reportAccess) this.reportAccess.setHidden(false); },

  onShareAppMessage() {
    trackEvent(5, '/subpackages/pair/pages/result/index', this.pairSessionId);
    return {
      title: this.data.result ? `我们的双人测试结果：${this.data.result.title}` : '一起来做双人测试',
      path: '/pages/home/index'
    };
  },

  retry() {
    this.loadReport();
  }
});
