Page({
  data: {
    testId: ''
  },

  onLoad(options) {
    this.setData({ testId: options.id || '' });
  }
});
