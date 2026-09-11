Page({
  data: {
    testId: ''
  },

  onLoad(options) {
    this.setData({ testId: options.id || '' });
  },

  backToHome() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
      return;
    }
    wx.switchTab({ url: '/pages/home/index' });
  }
});
