Component({
  properties: {
    title: {
      type: String,
      value: ''
    },
    brand: {
      type: Boolean,
      value: false
    },
    showBack: {
      type: Boolean,
      value: false
    }
  },

  data: {
    statusBarHeight: 20,
    contentHeight: 44,
    totalHeight: 64
  },

  lifetimes: {
    attached() {
      const windowInfo = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const menuRect = wx.getMenuButtonBoundingClientRect();
      const statusBarHeight = windowInfo.statusBarHeight || 20;
      const contentHeight = (menuRect.top - statusBarHeight) * 2 + menuRect.height;
      this.setData({
        statusBarHeight,
        contentHeight,
        totalHeight: statusBarHeight + contentHeight
      });
    }
  },

  methods: {
    goBack() {
      if (getCurrentPages().length > 1) {
        wx.navigateBack();
        return;
      }
      wx.switchTab({ url: '/pages/home/index' });
    }
  }
});
