const DOCUMENTS = {
  userAgreement: {
    title: '用户协议',
    kicker: '原型展示协议阅读入口与返回逻辑。',
    paragraphs: [
      '我们仅处理完成服务所需的匿名标识、答题选项、题型版本、测试时间、权益状态和必要诊断信息。不会申请通讯录、定位、相机或麦克风。',
      '双人测试只呈现双方的聚合报告；不会向对方展示你的逐题答案、昵称、头像或设备资料。'
    ]
  },
  privacyGuide: {
    title: '隐私保护指引',
    kicker: '正式内容尚未接入。',
    paragraphs: [
      '当前页面仅用于验证隐私指引入口、版本记录与返回流程。正式隐私保护指引必须结合最终数据清单完成法务与合规确认，当前原型文案不可用于正式提审。'
    ]
  },
  disclaimer: {
    title: '娱乐测试免责声明',
    kicker: '正式内容尚未接入。',
    paragraphs: [
      '当前页面仅用于验证免责声明入口、版本记录与返回流程。正式娱乐测试免责声明必须在上线前完成法务与合规确认后替换，当前原型文案不可用于正式提审。'
    ]
  }
};

Page({
  data: {
    document: DOCUMENTS.userAgreement
  },

  onLoad(options) {
    this.setData({
      document: DOCUMENTS[options.type] || DOCUMENTS.userAgreement
    });
  }
});
