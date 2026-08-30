Page({
  startPair() {
    wx.navigateTo({ url: '/subpackages/test/pages/quiz/index?mode=pair&next=invite' });
  },

  joinPair() {
    wx.navigateTo({ url: '/subpackages/pair/pages/join/index' });
  }
});
