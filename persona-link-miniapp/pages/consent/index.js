const { requestData } = require('../../utils/request');

Page({
  data: {
    consentVersion: 'v2.0',
    state: 'loading',
    busy: false,
    error: ''
  },

  onLoad() {
    const { consentVersion } = getApp().globalData;
    this.setData({ consentVersion });
  },

  onShow() {
    if (this.started) return;
    this.started = true;
    this.init();
  },

  onUnload() { this.disposed = true; },

  async init() {
    // 已同意且协议未更新时直接进入，不重复展示协议页。
    try {
      await getApp().verifyConsent();
    } catch (error) {}
    if (this.disposed) return;
    if (getApp().globalData.hasConsent) {
      this.continueToApp();
      return;
    }
    await this.loadDocuments();
  },

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

  openLegal(event) {
    const type = event.currentTarget.dataset.type;
    wx.navigateTo({
      url: `/subpackages/account/pages/legal/index?type=${type}`
    });
  },

  async agreeAndContinue() {
    if (this.data.state !== 'ready' || this.data.busy || this.agreeing) return;
    this.agreeing = true;
    this.setData({ busy: true, error: '' });
    const app = getApp();
    app.consentEpoch = (app.consentEpoch || 0) + 1;
    wx.setStorageSync('personaLinkConsentedDocuments', this.documents);
    wx.setStorageSync(app.globalData.consentStorageKey, this.data.consentVersion);
    try {
      await app.verifyConsent();
      this.continueToApp();
    } catch (error) {
      this.agreeing = false;
      if (!this.disposed) this.setData({ error: error.message || '操作失败，请重试' });
    } finally {
      if (!this.disposed) this.setData({ busy: false });
    }
  },

  continueToApp() {
    if (this.disposed) return;
    const app = getApp();
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
