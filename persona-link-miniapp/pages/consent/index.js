const { requestData, loginSession, onboardingProfile, onboardingAvatar, isProfileComplete, TOKEN_STORAGE_KEY } = require('../../utils/request');
const { resolveImageUrl } = require('../../utils/image');

Page({
  data: {
    consentVersion: 'v2.0',
    step: 'login',
    state: 'ready',
    nickname: '',
    avatarUrl: '',
    avatarImage: '',
    busy: false,
    error: ''
  },

  onShow() {
    if (this.started) {
      if (!this.data.busy && this.profileToken && this.profileToken !== wx.getStorageSync(TOKEN_STORAGE_KEY)) this.setData({ step: 'login', error: '登录状态已变化，请重新登录' });
      return;
    }
    this.started = true;
    if (wx.getStorageSync(TOKEN_STORAGE_KEY)) this.resumeProfile();
  },
  onUnload() { this.disposed = true; },

  async login() {
    if (this.data.busy) return;
    this.setData({ busy: true, error: '' });
    try {
      await loginSession();
      await this.resumeProfile();
    } catch (error) { this.showError(error); }
    finally { if (!this.disposed) this.setData({ busy: false }); }
  },

  showError(error) {
    if (this.disposed) return;
    this.setData({ error: error.message || '操作失败，请重试',
      ...(!wx.getStorageSync(TOKEN_STORAGE_KEY) || (this.profileToken && this.profileToken !== wx.getStorageSync(TOKEN_STORAGE_KEY)) ? { step: 'login' } : {}) });
  },

  async resumeProfile() {
    this.setData({ busy: true, error: '' });
    try {
      const profile = await onboardingProfile();
      if (this.disposed) return;
      this.profileToken = wx.getStorageSync(TOKEN_STORAGE_KEY);
      this.setData({ step: 'profile', nickname: profile.nickname || '', avatarUrl: profile.avatarUrl || '', avatarImage: resolveImageUrl(profile.avatarUrl) });
      if (isProfileComplete(profile)) {
        await this.loadDocuments();
        try { await getApp().verifyConsent(); this.continueToApp(); } catch (error) { /* 未同意或版本已更新，留在协议步骤。 */ }
      }
    } catch (error) { this.showError(error); }
    finally { if (!this.disposed) this.setData({ busy: false }); }
  },

  handleNicknameInput(event) { this.setData({ nickname: event.detail.value }); },

  async chooseAvatar(event) {
    if (this.data.busy || !event.detail.avatarUrl) return;
    this.setData({ busy: true, error: '' });
    try {
      const result = await onboardingAvatar(event.detail.avatarUrl, this.profileToken);
      if (!result || !result.avatarUrl) throw new Error('未获取到头像，请重试');
      if (!this.disposed) this.setData({ avatarUrl: result.avatarUrl, avatarImage: resolveImageUrl(result.avatarUrl) });
    } catch (error) { this.showError(error); }
    finally { if (!this.disposed) this.setData({ busy: false }); }
  },

  async saveProfile(event) {
    if (this.data.busy) return;
    const value = event && event.detail && event.detail.value;
    const nickname = String(typeof value === 'string' ? value : value ? value.nickname : this.data.nickname).trim();
    if (!nickname || nickname.length > 32 || !this.data.avatarUrl) {
      this.setData({ error: '请选择微信头像并填写昵称（最多32字）' }); return;
    }
    this.setData({ busy: true, error: '', nickname });
    try {
      if (this.profileToken !== wx.getStorageSync(TOKEN_STORAGE_KEY)) throw new Error('登录状态已变化，请重新登录');
      const profile = await onboardingProfile({ nickname, avatarUrl: this.data.avatarUrl }, this.profileToken);
      if (!isProfileComplete(profile)) throw new Error('头像昵称保存未完成，请重试');
      await this.loadDocuments();
    } catch (error) { this.showError(error); }
    finally { if (!this.disposed) this.setData({ busy: false }); }
  },

  async loadDocuments() {
    if (this.loading) return;
    this.loading = true;
    this.setData({ step: 'consent', state: 'loading', error: '' });
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

  async agreeAndContinue() {
    if (this.data.step !== 'consent' || this.data.state !== 'ready' || this.data.busy || this.agreeing) return;
    if (!this.profileToken || this.profileToken !== wx.getStorageSync(TOKEN_STORAGE_KEY)) {
      this.setData({ step: 'login', error: '登录状态已变化，请重新登录' }); return;
    }
    this.agreeing = true;
    const app = getApp();
    app.consentEpoch = (app.consentEpoch || 0) + 1;
    wx.setStorageSync('personaLinkConsentedDocuments', this.documents);
    wx.setStorageSync(app.globalData.consentStorageKey, this.data.consentVersion);
    wx.setStorageSync('personaLinkConsentToken', this.profileToken);
    try {
      await app.verifyConsent();
      this.continueToApp();
    } catch (error) {
      this.agreeing = false;
      this.showError(error);
      if (wx.getStorageSync(TOKEN_STORAGE_KEY)) await this.resumeProfile();
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
