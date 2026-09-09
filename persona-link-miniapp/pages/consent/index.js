const { requestData } = require('../../utils/request');

Page({
  data: {
    consentVersion: 'v2.0',
    state: 'loading',
    error: ''
  },

  onShow() { this.loadDocuments(); },
  onUnload() { this.disposed = true; },

  async loadDocuments() {
    if (this.loading) return;
    this.loading = true;
    this.setData({ state: 'loading', error: '' });
    try {
      const documents = await Promise.all([1, 2, 3].map((type) =>
        requestData({ url: `/api/miniapp/legal-documents/${type}` })));
      if (documents.some((document) => !document.content || !document.version)) {
        throw new Error('协议尚未配置完成，请稍后重试');
      }
      if (this.disposed) return;
      this.documents = documents.map(({ type, version }) => ({ type, version }));
      this.setData({ state: 'ready' });
    } catch (error) {
      if (!this.disposed) this.setData({ state: 'error', error: error.message || '协议加载失败' });
    } finally { this.loading = false; }
  },

  onLoad() {
    const { consentVersion } = getApp().globalData;
    this.setData({ consentVersion });
  },

  openLegal(event) {
    const type = event.currentTarget.dataset.type;
    wx.navigateTo({
      url: `/subpackages/account/pages/legal/index?type=${type}`
    });
  },

  agreeAndContinue() {
    if (this.data.state !== 'ready' || this.agreeing) return;
    this.agreeing = true;
    const app = getApp();
    app.consentEpoch = (app.consentEpoch || 0) + 1;
    wx.setStorageSync('personaLinkConsentedDocuments', this.documents);
    wx.setStorageSync(app.globalData.consentStorageKey, this.data.consentVersion);
    app.globalData.hasConsent = true;
    const pendingLaunchUrl = app.globalData.pendingLaunchUrl;
    app.globalData.pendingLaunchUrl = '';
    if (pendingLaunchUrl) {
      wx.reLaunch({ url: pendingLaunchUrl, fail: () => { this.agreeing = false; } });
      return;
    }
    wx.switchTab({ url: '/pages/home/index', fail: () => { this.agreeing = false; } });
  },

  exitMiniProgram() {
    wx.exitMiniProgram({
      fail() {
        wx.showToast({ title: '请关闭小程序退出', icon: 'none' });
      }
    });
  }
});
