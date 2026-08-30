const PAIR_SESSION_KEY = 'personaPairSession';
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function createPairCode() {
  return Array.from({ length: 5 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
}

Page({
  data: {
    pairCode: ''
  },

  onLoad() {
    const pairCode = createPairCode();
    wx.setStorageSync(PAIR_SESSION_KEY, {
      pairCode,
      selfCompleted: true,
      partnerCompleted: false
    });
    this.setData({ pairCode });
  },

  copyPairCode() {
    wx.setClipboardData({ data: this.data.pairCode });
  },

  openProgress() {
    wx.navigateTo({ url: '/subpackages/pair/pages/wait/index' });
  },

  onShareAppMessage() {
    return {
      title: '来和我一起做双人关系角色测试',
      path: `/subpackages/pair/pages/join/index?code=${this.data.pairCode}`
    };
  }
});
