const {
  request,
  authenticatedRequestData,
  TOKEN_STORAGE_KEY
} = require('../../utils/request');
const { trackEvent } = require('../../utils/analytics');
const { resolveImageUrl } = require('../../utils/image');

const ALL_CATEGORY_ID = 'all';
const DEFAULT_TITLE_IMAGE = '/assets/images/home-title.png';

function prepareTests(tests) {
  return (Array.isArray(tests) ? tests : []).map((item) => Object.assign({}, item, {
    imageUrl: resolveImageUrl(item.coverUrl)
  }));
}

function filterTests(tests, categoryId) {
  return ALL_CATEGORY_ID === categoryId
    ? tests
    : tests.filter((item) => item.categoryId === categoryId);
}

Page({
  data: {
    state: 'loading',
    titleImageUrl: DEFAULT_TITLE_IMAGE,
    categories: [{ categoryId: ALL_CATEGORY_ID, categoryName: '全部' }],
    selectedCategoryId: ALL_CATEGORY_ID,
    focusTest: null,
    recommendedTests: [],
    allTests: [],
    tests: [],
    currentAssessment: null,
    abandoningAssessment: false,
    containerVisible: false,
    containerMode: '',
    detailVisible: false,
    quizVisible: false,
    activeTestId: '',
    activeTestType: 1,
    emptyDescription: '内容正在更新中。\n这里不会用广告替代内容。',
    errorDescription: '暂时无法连接服务，\n请稍后重试。'
  },

  async onShow() {
    const app = getApp();
    if (app.globalData.resetHomeDetail) {
      app.globalData.resetHomeDetail = false;
      this.clearTestDetail();
    }
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      const accountVisible = this.data.containerVisible && this.data.containerMode === 'account';
      this.getTabBar().setData({ selected: accountVisible ? 1 : 0 });
    }
    try {
      if (app.verifyConsent) await app.verifyConsent();
    } catch (error) { return; }
    if (app.globalData.hasConsent) {
      this.loadHome();
      trackEvent(2, '/pages/home/index');
      if (wx.getStorageSync(TOKEN_STORAGE_KEY)) this.loadCurrentAssessment();
    }
  },

  async onPullDownRefresh() {
    try {
      if (!getApp().globalData.hasConsent || this.data.containerVisible) {
        return;
      }
      await Promise.all([
        this.loadHome(),
        wx.getStorageSync(TOKEN_STORAGE_KEY) ? this.loadCurrentAssessment() : Promise.resolve()
      ]);
    } finally {
      wx.stopPullDownRefresh();
    }
  },

  blockContainerScroll() {},

  async loadCurrentAssessment() {
    if (this.data.abandoningAssessment) return;
    const version = this.currentAssessmentVersion = (this.currentAssessmentVersion || 0) + 1;
    try {
      const currentAssessment = await authenticatedRequestData({
        url: '/api/miniapp/assessments/current'
      });
      if (version !== this.currentAssessmentVersion) return;
      this.setData({ currentAssessment: currentAssessment || null });
    } catch (error) {
      if (version !== this.currentAssessmentVersion) return;
      this.setData({ currentAssessment: null });
    }
  },

  async loadHome() {
    const requestVersion = (this.requestVersion || 0) + 1;
    this.requestVersion = requestVersion;
    if (this.data.state !== 'ready') {
      this.setData({ state: 'loading' });
    }
    try {
      const response = await request({ url: '/api/miniapp/home' });
      if (this.requestVersion !== requestVersion) {
        return;
      }
      if (!response || response.code !== 0 || !response.data) {
        throw new Error(response && response.message ? response.message : '首页配置加载失败');
      }
      const home = response.data;
      const categories = [
        { categoryId: ALL_CATEGORY_ID, categoryName: '全部' },
        ...(Array.isArray(home.categories) ? home.categories : [])
      ];
      const focusTests = prepareTests(home.focusTests);
      const recommendedTests = prepareTests(home.recommendedTests);
      const allTests = prepareTests(home.allTests);
      const selectedCategoryId = categories.some(
        (item) => item.categoryId === this.data.selectedCategoryId
      ) ? this.data.selectedCategoryId : ALL_CATEGORY_ID;
      const hasContent = categories.length > 1 || focusTests.length > 0 || recommendedTests.length > 0 || allTests.length > 0;
      this.setData({
        state: hasContent ? 'ready' : 'empty',
        titleImageUrl: resolveImageUrl(home.titleImageUrl) || DEFAULT_TITLE_IMAGE,
        categories,
        selectedCategoryId,
        focusTest: focusTests[0] || null,
        recommendedTests,
        allTests,
        tests: filterTests(allTests, selectedCategoryId)
      });
    } catch (error) {
      if (this.requestVersion !== requestVersion) {
        return;
      }
      this.setData({ state: 'error' });
    }
  },

  handleTitleImageError() {
    if (this.data.titleImageUrl !== DEFAULT_TITLE_IMAGE) {
      this.setData({ titleImageUrl: DEFAULT_TITLE_IMAGE });
    }
  },

  selectCategory(event) {
    const selectedCategoryId = event.currentTarget.dataset.categoryId;
    this.setData({
      selectedCategoryId,
      tests: filterTests(this.data.allTests, selectedCategoryId)
    });
  },

  switchRootTab(index) {
    if (Number(index) === 1) {
      this.openAccount();
      return;
    }
    this.closeAccount();
  },

  openAccount() {
    this.reopenAccountAfterLeave = false;
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ selected: 1, hidden: false });
    }
    this.setData({
      containerVisible: true,
      containerMode: 'account'
    });
  },

  closeAccount() {
    this.reopenAccountAfterLeave = false;
    this.setData({ containerVisible: false });
  },

  handleAccountViewChange(event) {
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ selected: 1, hidden: !event.detail.atRoot });
    }
  },

  loadMoreAccountRecords() {
    if (!this.data.containerVisible || this.data.containerMode !== 'account') return;
    const account = this.selectComponent('#home-account-center');
    if (account) account.loadMoreRecords();
  },

  clearAccount() {
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ selected: 0, hidden: false });
    }
    this.setData({
      containerVisible: false,
      containerMode: ''
    });
  },

  openTest(event) {
    const { id, type } = event.currentTarget.dataset;
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ hidden: true });
    }
    this.setData({
      containerVisible: true,
      containerMode: 'detail',
      detailVisible: true,
      activeTestId: String(id || ''),
      activeTestType: Number(type) === 2 ? 2 : 1
    });
  },

  closeTestDetail() {
    this.setData({ containerVisible: false });
  },

  deactivateTestDetail() {
    const detail = this.selectComponent('#home-test-detail');
    if (detail) {
      detail.deactivate();
    }
  },

  clearTestDetail() {
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ hidden: false });
    }
    this.setData({
      containerVisible: false,
      containerMode: '',
      detailVisible: false,
      activeTestId: '',
      activeTestType: 1
    });
  },

  onShareAppMessage() {
    if (!this.data.detailVisible || !this.data.activeTestId) {
      return { title: '映见你我', path: '/pages/home/index' };
    }
    const detailPath = Number(this.data.activeTestType) === 2
      ? '/subpackages/pair/pages/detail/index'
      : '/subpackages/test/pages/detail/index';
    return {
      title: '映见你我',
      path: `${detailPath}?id=${encodeURIComponent(this.data.activeTestId)}`
    };
  },

  continueAssessment() {
    if (this.data.abandoningAssessment) return;
    const assessment = this.data.currentAssessment;
    if (!assessment || !assessment.answerSessionId) {
      return;
    }
    this.openQuiz(assessment);
  },

  openQuiz(assessment) {
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ hidden: true });
    }
    this.setData({
      containerVisible: true,
      containerMode: 'quiz',
      quizVisible: true,
      activeTestType: Number(assessment.answerType) || 1
    }, () => {
      this.selectComponent('#home-quiz-runner').start({
        answerSessionId: assessment.answerSessionId,
        assessment
      });
    });
  },

  closeQuiz() {
    this.setData({ containerVisible: false });
  },

  deactivateQuiz() {
    const runner = this.selectComponent('#home-quiz-runner');
    if (runner) {
      runner.deactivate();
    }
  },

  clearQuiz() {
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ hidden: false });
    }
    this.setData({
      containerVisible: false,
      containerMode: '',
      quizVisible: false
    });
    this.loadCurrentAssessment();
    const nextUrl = this.pendingQuizUrl;
    this.pendingQuizUrl = '';
    if (nextUrl) {
      wx.navigateTo({
        url: nextUrl,
        fail: () => wx.showToast({ title: '页面打开失败，请稍后重试', icon: 'none' })
      });
    }
  },

  finishQuiz(event) {
    this.pendingQuizUrl = event.detail.url;
    this.setData({ containerVisible: false });
  },

  deactivateContainer() {
    if (this.data.containerMode === 'account') {
      const account = this.selectComponent('#home-account-center');
      if (account && account.data.currentView !== 'profile') {
        this.reopenAccountAfterLeave = true;
        account.handleBack();
      }
      return;
    }
    if (this.data.containerMode === 'quiz') {
      this.deactivateQuiz();
      return;
    }
    this.deactivateTestDetail();
  },

  clearContainer() {
    if (this.data.containerMode === 'account') {
      if (this.reopenAccountAfterLeave) {
        this.reopenAccountAfterLeave = false;
        this.setData({ containerVisible: true });
        return;
      }
      this.clearAccount();
      return;
    }
    if (this.data.containerMode === 'quiz') {
      this.clearQuiz();
      return;
    }
    this.clearTestDetail();
  },

  abandonAssessment() {
    const assessment = this.data.currentAssessment;
    if (!assessment || !assessment.answerSessionId || this.data.abandoningAssessment) {
      return;
    }
    this.currentAssessmentVersion = (this.currentAssessmentVersion || 0) + 1;
    this.setData({ abandoningAssessment: true });
    wx.showModal({
      title: '作废本次测试？',
      content: Number(assessment.answerType) === 2
        ? '作废后不能继续本次答卷。如已加入双人配对，本次配对也将取消。不会创建新答卷。'
        : '作废后不能继续本次答卷，不会创建新答卷，也不会影响已完成的测试记录。',
      confirmText: '作废',
      confirmColor: '#ff6f65',
      success: async (result) => {
        try {
          if (!result.confirm) return;
          await authenticatedRequestData({
            url: `/api/miniapp/assessments/${encodeURIComponent(assessment.answerSessionId)}/abandon`,
            method: 'POST',
            sessionBound: true
          });
          this.setData({ currentAssessment: null });
          wx.showToast({ title: '本次测试已作废', icon: 'none' });
        } catch (error) {
          wx.showToast({ title: error.message || '作废失败，请重试', icon: 'none' });
        } finally {
          this.setData({ abandoningAssessment: false });
        }
      },
      fail: () => this.setData({ abandoningAssessment: false })
    });
  }
});
