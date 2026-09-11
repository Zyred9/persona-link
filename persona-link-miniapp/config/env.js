const API_BASE_URLS = {
  develop: 'http://192.168.8.128:8080',
  trial: 'https://persona.sanmao.online',
  release: 'https://persona.sanmao.online',
};

function getEnvVersion() {
  const accountInfo = wx.getAccountInfoSync();
  return accountInfo.miniProgram.envVersion || 'develop';
}

function getApiBaseUrl() {
  const envVersion = getEnvVersion();
  // 调试地址仅用于开发版，避免本地缓存覆盖体验版和正式版服务地址。
  const storedBaseUrl = envVersion === 'develop' && wx.getStorageSync('personaLinkApiBaseUrl');
  if (storedBaseUrl) {
    return storedBaseUrl.replace(/\/$/, '');
  }

  const baseUrl = API_BASE_URLS[envVersion];
  if (!baseUrl) {
    throw new Error('当前环境尚未配置 API 地址');
  }
  return baseUrl;
}

module.exports = {
  getApiBaseUrl
};
