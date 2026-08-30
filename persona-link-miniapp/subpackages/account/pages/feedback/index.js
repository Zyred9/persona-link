Page({
  data: {
    content: ''
  },

  handleInput(event) {
    this.setData({ content: event.detail.value });
  },

  submitFeedback() {
    const content = this.data.content.trim();
    if (!content) {
      wx.showToast({ title: '请填写反馈内容', icon: 'none' });
      return;
    }

    this.setData({ content: '' });
    wx.showToast({ title: '提交成功', icon: 'success' });
    setTimeout(() => {
      wx.redirectTo({ url: '/subpackages/account/pages/settings/index' });
    }, 600);
  }
});
