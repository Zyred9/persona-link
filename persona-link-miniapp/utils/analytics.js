const { authenticatedRequestData, createIdempotencyKey } = require('./request');

function trackEvent(eventType, pagePath, businessId) {
  if (!getApp().globalData.hasConsent) {
    return Promise.resolve();
  }
  let appVersion = '';
  try {
    appVersion = wx.getAccountInfoSync().miniProgram.version || '';
  } catch (error) {}
  return authenticatedRequestData({
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
