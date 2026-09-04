Page({
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
  },

  goHistory() {
    wx.navigateTo({ url: '/subpackages/account/pages/history/index' });
  },

  goMember() {
    wx.navigateTo({ url: '/subpackages/account/pages/member/index' });
  },

  goFeedback() {
    wx.navigateTo({ url: '/subpackages/account/pages/feedback/index' });
  },

  goSettings() {
    wx.navigateTo({ url: '/subpackages/account/pages/settings/index' });
  },

  goHome() {
    wx.switchTab({ url: '/pages/home/index' });
  }
});
