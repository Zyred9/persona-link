const { getApiBaseUrl } = require('../config/env');

const TOKEN_STORAGE_KEY = 'personaLinkBusinessToken';
let pendingSession = null;

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

function clearSession(expectedToken) {
  // 并发旧请求的 401 不能删除其他请求刚刷新的会话。
  if (wx.getStorageSync(TOKEN_STORAGE_KEY) !== expectedToken) return false;
  wx.removeStorageSync(TOKEN_STORAGE_KEY);
  return true;
}

function handleUnauthorized(expectedToken) {
  if (!clearSession(expectedToken)) return;
  wx.showToast({
    title: '登录状态已失效',
    icon: 'none'
  });

  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1];
  if (currentPage && currentPage.route !== 'pages/home/index') {
    getApp().returnToHome();
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
          if (options.silentUnauthorized) {
            clearSession(token);
          } else {
            handleUnauthorized(token);
          }
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

function requestData(options) {
  return request(options).then((response) => {
    if (!response || response.code !== 0) {
      throw createRequestError(
        response && response.message ? response.message : '服务响应异常',
        undefined,
        response
      );
    }
    return response.data;
  });
}

function login() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(result) {
        if (!result.code) {
          reject(createRequestError('微信登录凭证获取失败'));
          return;
        }
        resolve(result.code);
      },
      fail(error) {
        reject(createRequestError(error.errMsg || '微信登录失败'));
      }
    });
  });
}

async function requestSession() {
  const code = await login();
  const session = await requestData({
    url: '/api/miniapp/auth/wechat',
    method: 'POST',
    data: { code },
    silentUnauthorized: true
  });
  if (!session || !session.token) {
    throw createRequestError('登录服务未返回业务会话');
  }
  wx.setStorageSync(TOKEN_STORAGE_KEY, session.token);
  return session.token;
}

function createSession() {
  // 首页统计与开始测试可能同时登录，共用请求避免重复创建会话。
  if (!pendingSession) {
    pendingSession = requestSession().finally(() => { pendingSession = null; });
  }
  return pendingSession;
}

function ensureSession() {
  const token = wx.getStorageSync(TOKEN_STORAGE_KEY);
  return token ? Promise.resolve(token) : createSession();
}

async function authenticatedRequestData(options) {
  await ensureSession();
  try {
    return await requestData(Object.assign({}, options, { silentUnauthorized: true }));
  } catch (error) {
    if (error.statusCode !== 401) {
      throw error;
    }
    await ensureSession();
    return requestData(Object.assign({}, options, { silentUnauthorized: true }));
  }
}

function createIdempotencyKey(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`.slice(0, 64);
}

module.exports = {
  request,
  requestData,
  authenticatedRequestData,
  ensureSession,
  createIdempotencyKey,
  TOKEN_STORAGE_KEY
};
