const { authenticatedRequestData, createIdempotencyKey } = require('../../utils/request');

const HISTORY_SECTIONS = {
  single: { prefix: 'history', records: 'records', total: 'total', id: 'reportId', url: 'reports' },
  pair: { prefix: 'pairHistory', records: 'pairRecords', total: 'pairTotal', id: 'pairSessionId', url: 'pairs' }
};
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

function formatHistoryRecord(record, kind) {
  if (kind === 'single') return Object.assign({}, record, {
    reportId: String(record.reportId),
    resultName: record.resultName || record.resultCode || '测试结果',
    generatedAt: formatGeneratedAt(record.generatedAt),
    versionLabel: record.versionNo ? `V${record.versionNo}` : ''
  });
  const status = Number(record.pairStatus);
  const labels = { 1: '等待对方完成', 2: '对方作答中', 3: '报告生成中', 4: '双人报告已生成', 5: '本次配对已失效', 6: '本次配对已失效' };
  return Object.assign({}, record, {
    pairSessionId: String(record.pairSessionId),
    resultText: labels[status] || '查看配对进度',
    meta: `${formatGeneratedAt(record.createdAt)}${record.versionNo ? ` · V${record.versionNo}` : ''}`,
    reportReady: status === 4,
    waiting: status < 4,
    invalid: status === 5 || status === 6
  });
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
    historyLoadingMore: false,
    historyHasMore: false,
    historyMoreError: '',
    pairHistoryState: 'loading',
    pairHistoryLoadingMore: false,
    pairHistoryHasMore: false,
    pairHistoryMoreError: '',
    pairHistoryErrorDescription: '',
    records: [],
    pairRecords: [],
    total: 0,
    pairTotal: 0,
    historyErrorDescription: '暂时无法加载测试记录，请稍后重试。',
    feedbackContent: '',
    feedbackSubmitting: false,
    versionLabel: ''
  },

  lifetimes: {
    attached() {
      this.componentAttached = true;
      this.setCurrentView(this.data.initialView);
    },
    detached() {
      this.componentAttached = false;
      this.feedbackEpoch = (this.feedbackEpoch || 0) + 1;
      this.historyRequestVersion = (this.historyRequestVersion || 0) + 1;
      this.clearFeedbackRedirect();
    }
  },

  pageLifetimes: {
    hide() {
      this.pageHidden = true;
      this.feedbackEpoch = (this.feedbackEpoch || 0) + 1;
      this.clearFeedbackRedirect();
    },
    show() {
      this.pageHidden = false;
      this.setData({ feedbackSubmitting: false });
    }
  },

  methods: {
    setCurrentView(view) {
      const currentView = normalizeView(view);
      if (this.data.currentView === 'history' && 'history' !== currentView) {
        this.historyRequestVersion = (this.historyRequestVersion || 0) + 1;
      }
      if (this.data.currentView === 'feedback' && 'feedback' !== currentView) {
        this.feedbackEpoch = (this.feedbackEpoch || 0) + 1;
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
        this.setData({ feedbackSubmitting: false });
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

    loadRecords() {
      this.historyRequestVersion = (this.historyRequestVersion || 0) + 1;
      return Promise.all([this.loadHistoryPage(true, 'single'), this.loadHistoryPage(true, 'pair')]);
    },

    loadMoreRecords() {
      return Promise.all(['single', 'pair'].map((kind) => {
        const section = HISTORY_SECTIONS[kind];
        if (this.data[section.prefix + 'State'] !== 'ready' || !this.data[section.prefix + 'HasMore']) return;
        return this.loadHistoryPage(false, kind);
      }));
    },

    retryPairHistory() {
      return this.loadHistoryPage(this.data.pairHistoryState === 'error', 'pair');
    },

    retrySingleHistory() {
      return this.loadHistoryPage(this.data.historyState === 'error', 'single');
    },

    async loadHistoryPage(reset, kind = 'single') {
      if (!this.componentAttached || this.data.currentView !== 'history') return;
      const section = HISTORY_SECTIONS[kind];
      const prefix = section.prefix;
      if (!reset && this[prefix + 'PageLoading']) return;
      const epoch = this.historyRequestVersion;
      const token = {};
      this[prefix + 'Request'] = token;
      const active = () => this.componentAttached && this.data.currentView === 'history'
        && epoch === this.historyRequestVersion && token === this[prefix + 'Request'];
      const pageNumber = reset ? 1 : this[prefix + 'Page'] + 1;
      this[prefix + 'PageLoading'] = true;
      this.setData(reset
        ? { [prefix + 'State']: 'loading', [prefix + 'LoadingMore']: false, [prefix + 'HasMore']: false, [prefix + 'MoreError']: '' }
        : { [prefix + 'LoadingMore']: true, [prefix + 'MoreError']: '' });
      try {
        const page = await authenticatedRequestData({ url: `/api/miniapp/${section.url}?page=${pageNumber}&size=20` });
        if (!active()) return;
        if (!page || !Array.isArray(page.records)) throw new Error('测试记录数据异常，请重试');
        const seen = new Set(reset ? [] : this.data[section.records].map((record) => record[section.id]));
        const additions = page.records.filter((record) => {
          const id = String(record[section.id]);
          if (seen.has(id)) return false;
          seen.add(id);
          return true;
        }).map((record) => formatHistoryRecord(record, kind));
        // 两个分区都保持服务端时间、ID 倒序，不按展示日期重排。
        const records = reset ? additions : this.data[section.records].concat(additions);
        this[prefix + 'Page'] = pageNumber;
        this.setData({
          [prefix + 'State']: records.length > 0 ? 'ready' : 'empty',
          [section.records]: records,
          [section.total]: Number(page.total) || 0,
          [prefix + 'HasMore']: page.records.length > 0 && pageNumber * 20 < Number(page.total)
        });
      } catch (error) {
        if (!active()) return;
        this.setData(reset ? {
          [prefix + 'State']: 'error',
          [prefix + 'ErrorDescription']: error.message || '测试记录加载失败'
        } : { [prefix + 'MoreError']: error.message || '加载更多失败，请重试' });
      } finally {
        // 刷新、删除或切换页面后，旧请求不能解除新请求的加载锁。
        if (active()) {
          this[prefix + 'PageLoading'] = false;
          this.setData({ [prefix + 'LoadingMore']: false });
        }
      }
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

    handleFeedbackInput(event) {
      if (this.data.feedbackSubmitting) return;
      this.setData({ feedbackContent: event.detail.value });
    },

    async submitFeedback() {
      if (this.data.feedbackSubmitting) return;
      const content = this.data.feedbackContent.trim();
      if (!content || content.length > 500) {
        wx.showToast({ title: content ? '反馈不能超过500字' : '请填写反馈内容', icon: 'none' });
        return;
      }
      if (!this.feedbackRequest || this.feedbackRequest.content !== content) {
        this.feedbackRequest = { requestId: createIdempotencyKey('feedback'), content };
      }
      const epoch = this.feedbackEpoch || 0;
      const active = () => this.componentAttached && !this.pageHidden && this.data.currentView === 'feedback'
        && epoch === (this.feedbackEpoch || 0);
      this.setData({ feedbackSubmitting: true });
      try {
        await authenticatedRequestData({ url: '/api/miniapp/feedbacks', method: 'POST', data: this.feedbackRequest });
      } catch (error) {
        if (active()) wx.showToast({ title: error.message || '提交失败，请重试', icon: 'none' });
        return;
      } finally {
        if (active()) this.setData({ feedbackSubmitting: false });
      }
      if (!active()) return;
      this.feedbackRequest = null;
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

    joinPair() {
      wx.navigateTo({ url: '/subpackages/pair/pages/join/index' });
    },

    openLegal(event) {
      const type = event.currentTarget.dataset.type;
      wx.navigateTo({
        url: `/subpackages/account/pages/legal/index?type=${type}`
      });
    }
  }
});
