Page({
  data: {
    accountVisible: true
  },

  onShow() {
    if (!this.data.accountVisible) {
      this.setData({ accountVisible: true });
    }
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
  },

  handleAccountViewChange(event) {
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ selected: 1, hidden: !event.detail.atRoot });
    }
  },

  closeAccount() {
    this.setData({ accountVisible: false });
  },

  deactivateAccount() {
    const account = this.selectComponent('#profile-account-center');
    if (account && account.data.currentView !== 'profile') {
      this.reopenAccountAfterLeave = true;
      account.handleBack();
    }
  },

  clearAccount() {
    if (this.reopenAccountAfterLeave) {
      this.reopenAccountAfterLeave = false;
      this.setData({ accountVisible: true });
      return;
    }
    const tabBar = typeof this.getTabBar === 'function' ? this.getTabBar() : null;
    if (tabBar) {
      tabBar.setData({ selected: 0, hidden: false });
    }
    wx.switchTab({ url: '/pages/home/index' });
  }
});
