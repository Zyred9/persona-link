const { getApiBaseUrl } = require('../config/env');

const TOKEN_STORAGE_KEY = 'personaLinkBusinessToken';
const PROFILE_CACHE_KEY = 'personaLinkProfileCache';
let pendingSession = null;
let sessionGeneration = 0;
let testRecordsGeneration = 0;

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
        if (options.sessionBound && token !== wx.getStorageSync(TOKEN_STORAGE_KEY)) {
          reject(createRequestError('登录状态已变化，请重新加载资料'));
          return;
        }
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
  const generation = sessionGeneration;
  const code = await login();
  if (generation !== sessionGeneration) throw createRequestError('登录已取消');
  const session = await requestData({
    url: '/api/miniapp/auth/wechat',
    method: 'POST',
    data: { code },
    silentUnauthorized: true
  });
  if (!session || !session.token) {
    throw createRequestError('登录服务未返回业务会话');
  }
  if (generation !== sessionGeneration) throw createRequestError('登录已取消');
  wx.setStorageSync(TOKEN_STORAGE_KEY, session.token);
  return session.token;
}

function createSession() {
  // 重复点击共用登录请求，避免重复创建会话。
  if (!pendingSession) {
    const current = requestSession().finally(() => { if (pendingSession === current) pendingSession = null; });
    pendingSession = current;
  }
  return pendingSession;
}

function clearAccountSession() {
  // 拒绝协议或注销后，旧登录回调不得恢复身份。
  sessionGeneration += 1;
  pendingSession = null;
  wx.removeStorageSync(TOKEN_STORAGE_KEY);
  wx.removeStorageSync(PROFILE_CACHE_KEY);
}

// 游客可自由浏览；核心功能首次使用时用静默 wx.login 建立会话，不弹授权。
function ensureSessionOrCreate() {
  const token = wx.getStorageSync(TOKEN_STORAGE_KEY);
  return token ? Promise.resolve(token) : createSession();
}

// 账户中心首次进入时先用缓存的完整资料渲染，避免重复拉取导致的等待。
function readProfileCache(token) {
  if (!token) return null;
  let cache;
  try {
    cache = wx.getStorageSync(PROFILE_CACHE_KEY);
  } catch (error) {
    return null;
  }
  if (!cache || cache.token !== token || !cache.nickname || !cache.avatarUrl) return null;
  return cache;
}

function writeProfileCache(token, nickname, avatarUrl, reviewing) {
  if (!token || !nickname || !avatarUrl) return;
  try {
    wx.setStorageSync(PROFILE_CACHE_KEY, { token, nickname, avatarUrl, reviewing: reviewing === true });
  } catch (error) {
    // 缓存失败不影响主流程。
  }
}

async function fetchProfile() {
  const token = wx.getStorageSync(TOKEN_STORAGE_KEY);
  if (!token) return null;
  const app = typeof getApp === 'function' ? getApp() : null;
  if (app && app.verifyConsent) await app.verifyConsent();
  if (token !== wx.getStorageSync(TOKEN_STORAGE_KEY)) throw createRequestError('登录状态已变化，请重新加载资料');
  return requestData({ url: '/api/miniapp/profile', sessionBound: true, silentUnauthorized: true });
}

async function authenticatedRequestData(options) {
  const app = typeof getApp === 'function' ? getApp() : null;
  if (app && app.verifyConsent) await app.verifyConsent();
  const generation = testRecordsGeneration;
  const checkResult = (result) => {
    if (generation !== testRecordsGeneration && /^\/api\/miniapp\/(assessments|reports|pairs)(\/|\?|$)/.test(options.url)) {
      throw createRequestError('测试记录已删除，请重新进入页面');
    }
    return result;
  };
  await ensureSessionOrCreate();
  if (options.expectedToken && options.expectedToken !== wx.getStorageSync(TOKEN_STORAGE_KEY)) {
    throw createRequestError('登录状态已变化，请重新加载资料');
  }
  checkResult(null);
  try {
    return checkResult(await requestData(Object.assign({}, options, { silentUnauthorized: true })));
  } catch (error) {
    if (error.statusCode !== 401) {
      throw error;
    }
    if (app && app.ensureConsent) app.ensureConsent();
    throw error;
  }
}

async function authenticatedUploadData(options) {
  const app = typeof getApp === 'function' ? getApp() : null;
  if (app && app.verifyConsent) await app.verifyConsent();
  return uploadData(options);
}

async function uploadData(options) {
  await ensureSessionOrCreate();
  const upload = () => {
    const token = wx.getStorageSync(TOKEN_STORAGE_KEY);
    if (options.expectedToken && options.expectedToken !== token) {
      return Promise.reject(createRequestError('登录状态已变化，请重新加载资料'));
    }
    return new Promise((resolve, reject) => {
      wx.uploadFile({
        url: `${getApiBaseUrl()}${options.url}`,
        filePath: options.filePath,
        name: 'file',
        header: { Authorization: `Bearer ${token}` },
        timeout: 30000,
        success(response) {
          if (token !== wx.getStorageSync(TOKEN_STORAGE_KEY)) {
            reject(createRequestError('登录状态已变化，请重新加载资料'));
            return;
          }
          if (response.statusCode === 401) clearSession(token);
          let body;
          try { body = JSON.parse(response.data); } catch (error) {
            reject(createRequestError('上传服务响应异常', response.statusCode));
            return;
          }
          if (response.statusCode < 200 || response.statusCode >= 300 || !body || body.code !== 0) {
            reject(createRequestError(body && body.message || '头像上传失败', response.statusCode, body));
            return;
          }
          resolve(body.data);
        },
        fail(error) { reject(createRequestError(error.errMsg || '头像上传失败，请重试')); }
      });
    });
  };
  try { return await upload(); } catch (error) {
    if (error.statusCode !== 401) throw error;
    await ensureSessionOrCreate();
    return upload();
  }
}

function createIdempotencyKey(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`.slice(0, 64);
}

module.exports = {
  clearAccountSession,
  loginSession: createSession,
  readProfileCache,
  writeProfileCache,
  fetchProfile,
  invalidateTestRecordRequests() { testRecordsGeneration += 1; },
  request,
  requestData,
  authenticatedRequestData,
  authenticatedUploadData,
  createIdempotencyKey,
  TOKEN_STORAGE_KEY
};
