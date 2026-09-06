Component({
  data: {
    selected: 0,
    hidden: false,
    tabs: [
      { pagePath: '/pages/home/index', text: '首页', icon: 'home' },
      { pagePath: '/pages/profile/index', text: '我的', icon: 'profile' }
    ]
  },

  methods: {
    switchTab(event) {
      const index = Number(event.currentTarget.dataset.index);
      const tab = this.data.tabs[index];
      if (!tab || index === this.data.selected) {
        return;
      }
      const pages = getCurrentPages();
      const currentPage = pages[pages.length - 1];
      if (currentPage
          && currentPage.route === 'pages/home/index'
          && typeof currentPage.switchRootTab === 'function') {
        currentPage.switchRootTab(index);
        return;
      }
      wx.switchTab({ url: tab.pagePath });
    }
  }
});
