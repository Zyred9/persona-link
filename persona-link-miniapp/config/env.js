const API_BASE_URLS = {
  develop: 'http://127.0.0.1:18080',
  trial: '',
  release: ''
};

function getEnvVersion() {
  const accountInfo = wx.getAccountInfoSync();
  return accountInfo.miniProgram.envVersion || 'develop';
}

function getApiBaseUrl() {
  const storedBaseUrl = wx.getStorageSync('personaLinkApiBaseUrl');
  if (storedBaseUrl) {
    return storedBaseUrl.replace(/\/$/, '');
  }

  const baseUrl = API_BASE_URLS[getEnvVersion()];
  if (!baseUrl) {
    throw new Error('当前环境尚未配置 API 地址');
  }
  return baseUrl;
}

module.exports = {
  getApiBaseUrl
};
