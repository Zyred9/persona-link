const { authenticatedRequestData, invalidateTestRecordRequests } = require('../../../../utils/request');

Page({
  data: {
    versionLabel: '尚无有效协议记录',
    deleting: false
  },

  onLoad() {
    const documents = wx.getStorageSync('personaLinkConsentedDocuments');
    const labels = { 1: '用户协议', 2: '隐私指引', 3: '免责声明' };
    if (Array.isArray(documents) && documents.length) this.setData({
      versionLabel: documents.map(({ type, version }) => `${labels[type]} V${version}`).join(' / ')
    });
  },
  onUnload() { this.disposed = true; },

  openPrivacyGuide() {
    wx.navigateTo({
      url: '/subpackages/account/pages/legal/index?type=privacyGuide'
    });
  },

  requestDeletion() {
    if (this.data.deleting || this.confirming) return;
    this.confirming = true;
    wx.showModal({
      title: '删除全部测试记录？',
      content: '将删除你的全部答卷、答案与报告，无法自行恢复。未完成双人测试将取消；对方仍可查看已完成的双人报告及自己的答卷。这不是账号注销。',
      confirmText: '确认删除',
      confirmColor: '#ff7469',
      success: async (result) => {
        if (!result.confirm || this.disposed) return;
        this.setData({ deleting: true });
        try {
          await authenticatedRequestData({ url: '/api/miniapp/me/test-records', method: 'DELETE' });
          invalidateTestRecordRequests();
          const keys = wx.getStorageInfoSync().keys;
          keys.filter((key) => key === 'personaPairSession' || key.startsWith('pairCreateRequest:'))
            .forEach((key) => wx.removeStorageSync(key));
          getApp().globalData.pendingLaunchUrl = '';
          getApp().globalData.resetHomeDetail = true;
          // 重建页面栈，销毁答卷预载、历史列表和广告控制器；保留登录及协议记录。
          wx.reLaunch({ url: '/pages/home/index' });
          wx.showToast({ title: '测试记录已删除', icon: 'success' });
        } catch (error) {
          if (!this.disposed) wx.showToast({ title: error.message || '删除失败，请重试', icon: 'none' });
        } finally {
          if (!this.disposed) this.setData({ deleting: false });
        }
      },
      complete: () => { this.confirming = false; }
    });
  }
});
