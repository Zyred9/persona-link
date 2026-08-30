Page({
  data: {
    versionLabel: 'V1.0'
  },

  onLoad() {
    this.setData({
      versionLabel: getApp().globalData.consentVersion.replace(/^v/i, 'V')
    });
  },

  openPrivacyGuide() {
    wx.navigateTo({
      url: '/subpackages/account/pages/legal/index?type=privacyGuide'
    });
  },

  requestDeletion() {
    wx.showModal({
      title: '申请删除数据',
      content: '删除请求提交后将异步处理。当前演示环境不会实际删除数据。',
      confirmText: '确认申请',
      success(result) {
        if (result.confirm) {
          wx.showToast({ title: '演示环境不实际删除', icon: 'none' });
        }
      }
    });
  }
});
