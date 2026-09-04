Page({
  data: { versionLabel: '' },

  onLoad() {
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
});
