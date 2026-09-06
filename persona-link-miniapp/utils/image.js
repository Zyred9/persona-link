const { getApiBaseUrl } = require('../config/env');

const OSS_IMAGE_PROCESS = 'x-oss-process=image/resize,w_1200/format,webp';

function resolveImageUrl(imageUrl) {
  if (!imageUrl) {
    return '';
  }
  const resolvedUrl = /^https?:\/\//.test(imageUrl)
    ? imageUrl
    : `${getApiBaseUrl()}${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
  if (!/^https?:\/\/pgcloud\.aitici\.com\//.test(resolvedUrl)
      || /[?&]x-oss-process=/.test(resolvedUrl)) {
    return resolvedUrl;
  }
  return `${resolvedUrl}${resolvedUrl.includes('?') ? '&' : '?'}${OSS_IMAGE_PROCESS}`;
}

module.exports = { resolveImageUrl };
