const { authenticatedRequestData } = require('../../../../utils/request');

const PAIR_SESSION_KEY = 'personaPairSession';

function formatGeneratedAt(value) {
  if (!value) return '';
  const generatedAt = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(generatedAt.getTime())) return String(value);
  const now = new Date();
  const isToday = generatedAt.getFullYear() === now.getFullYear()
    && generatedAt.getMonth() === now.getMonth()
    && generatedAt.getDate() === now.getDate();
  const pad = (number) => String(number).padStart(2, '0');
  const time = `${pad(generatedAt.getHours())}:${pad(generatedAt.getMinutes())}`;
  return isToday
    ? `今天 ${time}`
    : `${generatedAt.getFullYear()}.${pad(generatedAt.getMonth() + 1)}.${pad(generatedAt.getDate())}`;
}

Page({
  data: {
    state: 'loading',
    records: [],
    pairRecords: [],
    total: 0,
    pairTotal: 0,
    singleSectionTitle: '测试报告',
    pairSectionTitle: '双人配对',
    errorDescription: '暂时无法加载测试记录，请稍后重试。'
  },

  onLoad() {
    this.loadRecords();
  },

  async loadRecords() {
    this.setData({ state: 'loading' });
    try {
      const storedPair = wx.getStorageSync(PAIR_SESSION_KEY) || {};
      const [page, pairRecord] = await Promise.all([
        authenticatedRequestData({ url: '/api/miniapp/reports?page=1&size=20' }),
        this.loadPairRecord(storedPair).catch(() => null)
      ]);
      const records = page && Array.isArray(page.records)
        ? page.records.map((record) => Object.assign({}, record, {
          reportId: String(record.reportId),
          resultName: record.resultName || record.resultCode || '测试结果',
          generatedAt: formatGeneratedAt(record.generatedAt),
          versionLabel: record.versionNo ? `V${record.versionNo}` : ''
        }))
        : [];
      const pairRecords = pairRecord ? [pairRecord] : [];
      this.setData({
        state: records.length > 0 || pairRecords.length > 0 ? 'ready' : 'empty',
        records,
        pairRecords,
        total: page ? page.total : 0,
        pairTotal: pairRecords.length,
        singleSectionTitle: records[0] && records[0].generatedAt.startsWith('今天') ? '今天' : '测试报告',
        pairSectionTitle: pairRecords.some((record) => record.waiting)
          ? '进行中'
          : (pairRecords.some((record) => record.invalid) ? '已失效' : '已完成')
      });
    } catch (error) {
      this.setData({
        state: 'error',
        errorDescription: error.message || '测试记录加载失败'
      });
    }
  },

  async loadPairRecord(storedPair) {
    if (!storedPair.pairSessionId) return null;
    const pairSessionId = String(storedPair.pairSessionId);
    const pair = await authenticatedRequestData({
      url: `/api/miniapp/pairs/${encodeURIComponent(pairSessionId)}`
    });
    const pairStatus = Number(pair.pairStatus);
    let resultText = '等待对方完成';
    if (pairStatus === 2) resultText = '对方作答中';
    if (pairStatus === 3) resultText = '报告生成中';
    if (pairStatus === 4) resultText = '双人报告已生成';
    if (pairStatus === 5 || pairStatus === 6) resultText = '本次配对已失效';
    let generatedAt = '';
    if (pairStatus === 4) {
      const report = await authenticatedRequestData({
        url: `/api/miniapp/pairs/${encodeURIComponent(pairSessionId)}/report`
      });
      generatedAt = formatGeneratedAt(report.generatedAt);
      const snapshot = report.resultSnapshot || {};
      const roleName = snapshot.roleName
        || (snapshot.summary ? (snapshot.summary.includes('较为接近') ? '默契搭子' : '互补搭子') : '');
      resultText = roleName ? `关系角色：${roleName}` : resultText;
    }
    return {
      pairSessionId,
      pairCode: storedPair.pairCode || '',
      title: '双人关系角色测试',
      resultText,
      meta: generatedAt || (storedPair.pairCode ? `配对码 ${storedPair.pairCode}` : '查看配对进度'),
      reportReady: pairStatus === 4,
      waiting: pairStatus < 4,
      invalid: pairStatus === 5 || pairStatus === 6
    };
  },

  handleRecordTap(event) {
    const reportId = String(event.currentTarget.dataset.reportId || '');
    if (!reportId) {
      return;
    }
    wx.navigateTo({
      url: `/subpackages/test/pages/result/index?reportId=${encodeURIComponent(reportId)}`
    });
  },

  handlePairTap(event) {
    const pairSessionId = String(event.currentTarget.dataset.pairSessionId || '');
    if (!pairSessionId) return;
    const reportReady = event.currentTarget.dataset.reportReady === true
      || event.currentTarget.dataset.reportReady === 'true';
    const path = reportReady ? 'result' : 'wait';
    wx.navigateTo({
      url: `/subpackages/pair/pages/${path}/index?pairSessionId=${encodeURIComponent(pairSessionId)}`
    });
  },

  deleteRecord(event) {
    const reportId = String(event.currentTarget.dataset.reportId || '');
    if (!reportId) return;
    wx.showModal({
      title: '删除这份报告？',
      content: '删除后无法恢复。',
      confirmColor: '#ff7469',
      success: async (result) => {
        if (!result.confirm) return;
        try {
          await authenticatedRequestData({
            url: `/api/miniapp/reports/${encodeURIComponent(reportId)}`,
            method: 'DELETE'
          });
          this.loadRecords();
        } catch (error) {
          wx.showToast({ title: error.message || '删除失败', icon: 'none' });
        }
      }
    });
  },

  retry() {
    this.loadRecords();
  }
});
