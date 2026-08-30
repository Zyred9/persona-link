const { request } = require('../../utils/request');

const TESTS = [
  {
    id: 'horse',
    title: '你是哪种\n牛马性格测试？',
    meta: '22题 · 单人',
    category: '职场协作',
    tone: 'yellow',
    icon: '马'
  },
  {
    id: 'preference',
    title: '你的\nX 偏好？',
    meta: '20题 · 单人',
    category: '日常偏好',
    tone: 'mint',
    icon: '✓'
  }
];

Page({
  data: {
    state: 'loading',
    categories: ['全部', '职场协作', '相处方式', '日常偏好'],
    selectedCategory: '全部',
    tests: TESTS,
    emptyDescription: '内容正在更新中。\n这里不会用广告替代内容。',
    errorDescription: '暂时无法连接服务，\n请稍后重试。'
  },

  onLoad(options) {
    if (!getApp().globalData.hasConsent) {
      return;
    }
    if (options.preview === 'empty') {
      this.setData({ state: 'empty' });
      return;
    }
    this.loadHome();
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
  },

  async loadHome() {
    this.setData({ state: 'loading' });
    try {
      await request({ url: '/api/health' });
      // ponytail: 服务端首页配置接口就绪后，用接口响应替换演示内容。
      this.setData({ state: 'ready', tests: TESTS });
    } catch (error) {
      this.setData({ state: 'error' });
    }
  },

  selectCategory(event) {
    const selectedCategory = event.currentTarget.dataset.category;
    const tests = '全部' === selectedCategory
      ? TESTS
      : TESTS.filter((item) => item.category === selectedCategory);
    this.setData({ selectedCategory, tests });
  },

  openSingleTest(event) {
    wx.navigateTo({
      url: `/subpackages/test/pages/detail/index?id=${event.currentTarget.dataset.id}`
    });
  },

  openPairTest() {
    wx.navigateTo({ url: '/subpackages/pair/pages/detail/index' });
  }
});
