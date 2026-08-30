Page({
  data: {
    sections: [
      {
        id: 'today',
        title: '今天',
        items: [
          {
            kind: 'single',
            title: '你是哪种牛马性格测试？',
            result: '认真小狗型',
            meta: '今天 10:24 · V1.0'
          }
        ]
      },
      {
        id: 'waiting',
        title: '进行中',
        items: [
          {
            kind: 'waiting',
            title: '双人关系角色测试',
            result: '等待对方完成',
            meta: '配对码 8F29D · 复制'
          }
        ]
      },
      {
        id: 'completed',
        title: '',
        items: [
          {
            kind: 'pair',
            title: '双人关系角色测试',
            result: '关系角色：默契搭子',
            meta: '2026.08.21 · 查看报告'
          }
        ]
      }
    ]
  },

  handleRecordTap(event) {
    const kind = event.currentTarget.dataset.kind;
    const routes = {
      single: '/subpackages/test/pages/result/index',
      waiting: '/subpackages/pair/pages/wait/index',
      pair: '/subpackages/pair/pages/result/index'
    };
    wx.navigateTo({ url: routes[kind] });
  }
});
