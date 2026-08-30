const VIEW_ALIASES = {
  ad: 'ad-failure',
  permission: 'permission-denied',
  login: 'login-gate'
};
const DEMO_LOGIN_KEY = 'persona_link_demo_logged_in';
const RESULTS = {
  horse: {
    name: '认真小狗型',
    description: '靠谱又细心，\n是团队里的安心担当！'
  },
  preference: {
    name: '灵感探索型',
    description: '愿意尝试新鲜选择，\n也保留自己的舒适节奏！'
  }
};

Page({
  data: {
    view: 'result',
    result: RESULTS.horse,
    testId: 'horse'
  },

  onLoad(options) {
    const requestedView = VIEW_ALIASES[options.state] || options.state;
    const availableViews = ['result', 'ad-failure', 'permission-denied', 'login-gate'];
    const testId = RESULTS[options.id] ? options.id : 'horse';
    this.setData({
      view: availableViews.includes(requestedView) ? requestedView : 'result',
      result: RESULTS[testId],
      testId
    });
  },

  watchVideo() {
    this.setData({ view: 'ad-failure' });
  },

  retryVideo() {
    this.setData({ view: 'result' });
    wx.showToast({ title: '视频入口已重新加载', icon: 'none' });
  },

  openMember() {
    if (wx.getStorageSync(DEMO_LOGIN_KEY)) {
      wx.navigateTo({ url: '/subpackages/account/pages/member/index' });
      return;
    }
    this.setData({ view: 'login-gate' });
  },

  authorizeLogin() {
    wx.login({
      success: () => {
        wx.setStorageSync(DEMO_LOGIN_KEY, true);
        this.setData({ view: 'result' });
        wx.navigateTo({ url: '/subpackages/account/pages/member/index' });
      },
      fail: () => {
        wx.showToast({ title: '暂时无法登录，请稍后重试', icon: 'none' });
      }
    });
  },

  onShareAppMessage() {
    return {
      title: `我的测试结果是：${this.data.result.name}`,
      path: `/subpackages/test/pages/result/index?id=${this.data.testId}`
    };
  },

  returnResult() {
    this.setData({ view: 'result' });
  }
});
