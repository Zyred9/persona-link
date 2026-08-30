Page({
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
