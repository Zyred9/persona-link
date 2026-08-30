Page({
  data: {
    consentVersion: 'v1.0'
  },

  onLoad() {
    const { consentStorageKey, consentVersion } = getApp().globalData;
    this.setData({ consentVersion });
    if (wx.getStorageSync(consentStorageKey) === consentVersion) {
      wx.switchTab({ url: '/pages/home/index' });
    }
  },

  openLegal(event) {
    const type = event.currentTarget.dataset.type;
    wx.navigateTo({
      url: `/subpackages/account/pages/legal/index?type=${type}`
    });
  },

  agreeAndContinue() {
    const app = getApp();
    wx.setStorageSync(app.globalData.consentStorageKey, this.data.consentVersion);
    app.globalData.hasConsent = true;
    const pendingLaunchUrl = app.globalData.pendingLaunchUrl;
    app.globalData.pendingLaunchUrl = '';
    if (pendingLaunchUrl) {
      wx.reLaunch({ url: pendingLaunchUrl });
      return;
    }
    wx.switchTab({ url: '/pages/home/index' });
  },

  exitMiniProgram() {
    wx.exitMiniProgram({
      fail() {
        wx.showToast({ title: '请关闭小程序退出', icon: 'none' });
      }
    });
  }
});
