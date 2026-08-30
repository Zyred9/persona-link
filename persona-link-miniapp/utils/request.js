const { getApiBaseUrl } = require('../config/env');

const TOKEN_STORAGE_KEY = 'personaLinkBusinessToken';

function createRequestError(message, statusCode, responseData, responseRequestId) {
  const error = new Error(message);
  error.statusCode = statusCode;
  if (responseData && typeof responseData === 'object') {
    error.code = responseData.code;
    error.data = responseData;
  }
  error.requestId = responseRequestId
    || (responseData && (responseData.requestId || responseData.request_id));
  return error;
}

function clearSession() {
  wx.removeStorageSync(TOKEN_STORAGE_KEY);
}

function handleUnauthorized() {
  clearSession();
  wx.showToast({
    title: '登录状态已失效',
    icon: 'none'
  });

  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1];
  if (currentPage && currentPage.route !== 'pages/home/index') {
    wx.switchTab({ url: '/pages/home/index' });
  }
}

function request(options) {
  const token = wx.getStorageSync(TOKEN_STORAGE_KEY);
  const headers = Object.assign({
    'content-type': 'application/json'
  }, options.header || {});

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let apiBaseUrl;
  try {
    apiBaseUrl = getApiBaseUrl();
  } catch (error) {
    return Promise.reject(error);
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${apiBaseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      header: headers,
      timeout: options.timeout || 10000,
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data);
          return;
        }
        if (response.statusCode === 401) {
          handleUnauthorized();
        }
        const responseData = response.data;
        const message = responseData && responseData.message
          ? responseData.message
          : `请求失败（${response.statusCode}）`;
        const requestIdHeader = Object.keys(response.header || {})
          .find((name) => name.toLowerCase() === 'x-request-id');
        const responseRequestId = requestIdHeader ? response.header[requestIdHeader] : undefined;
        reject(createRequestError(message, response.statusCode, responseData, responseRequestId));
      },
      fail(error) {
        reject(createRequestError(error.errMsg || '网络连接失败'));
      }
    });
  });
}

module.exports = {
  request,
  TOKEN_STORAGE_KEY
};
