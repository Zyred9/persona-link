const PAIR_SESSION_KEY = 'personaPairSession';

Page({
  data: {
    pairCode: '',
    errorMessage: ''
  },

  onLoad(options) {
    if (options.code) {
      this.setData({ pairCode: String(options.code).trim().toUpperCase().slice(0, 5) });
    }
  },

  updatePairCode(event) {
    const pairCode = event.detail.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 5);
    this.setData({ pairCode, errorMessage: '' });
  },

  clearPairCode() {
    this.setData({ pairCode: '', errorMessage: '' });
  },

  confirmJoin() {
    if (!/^[A-Z0-9]{5}$/.test(this.data.pairCode)) {
      this.setData({ errorMessage: '请输入 5 位字母或数字配对码' });
      return;
    }

    wx.setStorageSync(PAIR_SESSION_KEY, {
      pairCode: this.data.pairCode,
      selfCompleted: false,
      partnerCompleted: false
    });
    wx.navigateTo({ url: '/subpackages/test/pages/quiz/index?mode=pair&next=wait' });
  }
});
