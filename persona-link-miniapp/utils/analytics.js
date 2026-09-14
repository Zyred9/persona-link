const { requestData, createIdempotencyKey, TOKEN_STORAGE_KEY } = require('./request');

function trackEvent(eventType, pagePath, businessId) {
  if (!getApp().globalData.hasConsent || !wx.getStorageSync(TOKEN_STORAGE_KEY)) {
    return Promise.resolve();
  }
  let appVersion = '';
  try {
    appVersion = wx.getAccountInfoSync().miniProgram.version || '';
  } catch (error) {}
  // 埋点只使用现有会话，不能为统计自动登录或在注销后重建账号。
  return requestData({
    silentUnauthorized: true,
    sessionBound: true,
    url: '/api/miniapp/events/batch',
    method: 'POST',
    data: {
      events: [{
        eventId: createIdempotencyKey('event'),
        eventType,
        pagePath,
        businessId: businessId ? String(businessId) : null,
        sourceScene: wx.getLaunchOptionsSync().scene,
        appVersion,
        clientTime: new Date().toISOString().slice(0, 19)
      }]
    }
  }).catch(() => undefined);
}

module.exports = { trackEvent };
