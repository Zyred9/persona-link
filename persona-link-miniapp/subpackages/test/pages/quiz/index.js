Page({
  data: {
    pageTitle: '单人测试'
  },

  onLoad(options) {
    this.quizOptions = {
      answerSessionId: options.answerSessionId || '',
      flow: options.flow || 'single',
      pairSessionId: options.pairSessionId || ''
    };
    this.setData({
      pageTitle: this.quizOptions.flow.startsWith('pair') ? '双人测试' : '单人测试'
    });
  },

  onReady() {
    this.selectComponent('#quiz-runner').start(this.quizOptions);
  },

  handleBack() {
    wx.navigateBack();
  },

  handleContext(event) {
    this.setData({ pageTitle: event.detail.answerType === 2 ? '双人测试' : '单人测试' });
  },

  handleFinish(event) {
    wx.redirectTo({
      url: event.detail.url,
      fail: () => this.selectComponent('#quiz-runner').navigationFailed()
    });
  }
});
