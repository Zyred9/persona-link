const TESTS = {
  horse: {
    heroFirst: '认真小牛',
    heroSecond: '的职场卡片',
    category: '职场趣测',
    questionCount: 22,
    duration: '约3分钟',
    title: '你是哪种牛马性格测试？',
    description: '适合想用轻松视角看看自己协作方式的人'
  },
  preference: {
    heroFirst: '偏好雷达',
    heroSecond: '的日常卡片',
    category: '日常偏好',
    questionCount: 20,
    duration: '约3分钟',
    title: '你的 X 偏好？',
    description: '从日常选择里，看看自己更自然的偏好方向'
  }
};

Page({
  data: {
    isError: false,
    testId: 'horse',
    test: TESTS.horse
  },

  onLoad(options) {
    const testId = TESTS[options.id] ? options.id : 'horse';
    this.setData({ isError: options.error === '1', testId, test: TESTS[testId] });
  },

  startTest() {
    wx.navigateTo({
      url: `/subpackages/test/pages/quiz/index?id=${this.data.testId}&count=${this.data.test.questionCount}`
    });
  },

  retry() {
    this.setData({ isError: false });
  }
});
