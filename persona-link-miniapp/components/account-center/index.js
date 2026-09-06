const { authenticatedRequestData } = require('../../utils/request');

const PAIR_SESSION_KEY = 'personaPairSession';
const PROFILE_VIEW = 'profile';
const VIEW_TITLES = {
  profile: '我的',
  history: '测试记录',
  feedback: '反馈与举报',
  settings: '设置'
};
const VIEW_ROUTES = {
  history: '/subpackages/account/pages/history/index',
  feedback: '/subpackages/account/pages/feedback/index',
  settings: '/subpackages/account/pages/settings/index'
};

function normalizeView(view) {
  return Object.prototype.hasOwnProperty.call(VIEW_TITLES, view) ? view : PROFILE_VIEW;
}

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

Component({
  options: {
    styleIsolation: 'apply-shared'
  },

  properties: {
    embedded: {
      type: Boolean,
      value: false
    },
    initialView: {
      type: String,
      value: PROFILE_VIEW,
      observer(initialView) {
        if (this.componentAttached) {
          this.setCurrentView(initialView);
        }
      }
    }
  },

  data: {
    currentView: PROFILE_VIEW,
    viewTitle: VIEW_TITLES[PROFILE_VIEW],
    historyState: 'loading',
    records: [],
    pairRecords: [],
    total: 0,
    pairTotal: 0,
    singleSectionTitle: '测试报告',
    pairSectionTitle: '双人配对',
    historyErrorDescription: '暂时无法加载测试记录，请稍后重试。',
    feedbackContent: '',
    versionLabel: ''
  },

  lifetimes: {
    attached() {
      this.componentAttached = true;
      this.setCurrentView(this.data.initialView);
    },
    detached() {
      this.componentAttached = false;
      this.historyRequestVersion = (this.historyRequestVersion || 0) + 1;
      this.clearFeedbackRedirect();
    }
  },

  methods: {
    setCurrentView(view) {
      const currentView = normalizeView(view);
      if (this.data.currentView === 'history' && 'history' !== currentView) {
        this.historyRequestVersion = (this.historyRequestVersion || 0) + 1;
      }
      if (this.data.currentView === 'feedback' && 'feedback' !== currentView) {
        this.clearFeedbackRedirect();
      }
      this.setData({
        currentView,
        viewTitle: VIEW_TITLES[currentView]
      }, () => {
        if (currentView !== this.data.currentView) return;
        this.prepareView(currentView);
        this.triggerEvent('viewchange', {
          view: currentView,
          atRoot: PROFILE_VIEW === currentView
        });
      });
    },

    prepareView(view) {
      if ('history' === view) {
        this.loadRecords();
        return;
      }
      if ('feedback' === view) {
        this.setData({ feedbackContent: '' });
        return;
      }
      if ('settings' === view) {
        this.loadVersionLabel();
      }
    },

    openAccountView(event) {
      const view = normalizeView(event.currentTarget.dataset.view);
      if (!this.data.embedded) {
        wx.navigateTo({ url: VIEW_ROUTES[view] });
        return;
      }
      this.setCurrentView(view);
    },

    handleBack() {
      if (PROFILE_VIEW === this.data.currentView) {
        this.triggerEvent('close');
        return;
      }
      this.setCurrentView(PROFILE_VIEW);
    },

    async loadRecords() {
      const requestVersion = (this.historyRequestVersion || 0) + 1;
      this.historyRequestVersion = requestVersion;
      this.setData({ historyState: 'loading' });
      try {
        const storedPair = wx.getStorageSync(PAIR_SESSION_KEY) || {};
        const [page, pairRecord] = await Promise.all([
          authenticatedRequestData({ url: '/api/miniapp/reports?page=1&size=20' }),
          this.loadPairRecord(storedPair).catch(() => null)
        ]);
        if (!this.componentAttached
          || 'history' !== this.data.currentView
          || requestVersion !== this.historyRequestVersion) {
          return;
        }
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
          historyState: records.length > 0 || pairRecords.length > 0 ? 'ready' : 'empty',
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
        if (!this.componentAttached
          || 'history' !== this.data.currentView
          || requestVersion !== this.historyRequestVersion) {
          return;
        }
        this.setData({
          historyState: 'error',
          historyErrorDescription: error.message || '测试记录加载失败'
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
      if (2 === pairStatus) resultText = '对方作答中';
      if (3 === pairStatus) resultText = '报告生成中';
      if (4 === pairStatus) resultText = '双人报告已生成';
      if (5 === pairStatus || 6 === pairStatus) resultText = '本次配对已失效';
      let generatedAt = '';
      if (4 === pairStatus) {
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
        reportReady: 4 === pairStatus,
        waiting: pairStatus < 4,
        invalid: 5 === pairStatus || 6 === pairStatus
      };
    },

    handleRecordTap(event) {
      const reportId = String(event.currentTarget.dataset.reportId || '');
      if (!reportId) return;
      wx.navigateTo({
        url: `/subpackages/test/pages/result/index?reportId=${encodeURIComponent(reportId)}`
      });
    },

    handlePairTap(event) {
      const pairSessionId = String(event.currentTarget.dataset.pairSessionId || '');
      if (!pairSessionId) return;
      const reportReady = true === event.currentTarget.dataset.reportReady
        || 'true' === event.currentTarget.dataset.reportReady;
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
            if (this.componentAttached && 'history' === this.data.currentView) {
              this.loadRecords();
            }
          } catch (error) {
            wx.showToast({ title: error.message || '删除失败', icon: 'none' });
          }
        }
      });
    },

    retryHistory() {
      this.loadRecords();
    },

    handleFeedbackInput(event) {
      this.setData({ feedbackContent: event.detail.value });
    },

    submitFeedback() {
      const content = this.data.feedbackContent.trim();
      if (!content) {
        wx.showToast({ title: '请填写反馈内容', icon: 'none' });
        return;
      }
      this.setData({ feedbackContent: '' });
      wx.showToast({ title: '提交成功', icon: 'success' });
      this.clearFeedbackRedirect();
      this.feedbackRedirectTimer = setTimeout(() => {
        this.feedbackRedirectTimer = null;
        if (!this.componentAttached) return;
        if (this.data.embedded) {
          this.setCurrentView('settings');
          return;
        }
        wx.redirectTo({ url: VIEW_ROUTES.settings });
      }, 600);
    },

    clearFeedbackRedirect() {
      if (this.feedbackRedirectTimer) {
        clearTimeout(this.feedbackRedirectTimer);
        this.feedbackRedirectTimer = null;
      }
    },

    loadVersionLabel() {
      try {
        const info = wx.getAccountInfoSync().miniProgram;
        this.setData({ versionLabel: info.version ? `V${info.version}` : 'V1.0 原型' });
      } catch (error) {
        this.setData({ versionLabel: 'V1.0 原型' });
      }
    },

    openPrivacy() {
      wx.navigateTo({ url: '/subpackages/account/pages/privacy/index' });
    },

    openLegal(event) {
      const type = event.currentTarget.dataset.type;
      wx.navigateTo({
        url: `/subpackages/account/pages/legal/index?type=${type}`
      });
    }
  }
});
