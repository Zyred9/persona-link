const { getApiBaseUrl } = require('../config/env');

const OSS_IMAGE_PROCESS = 'x-oss-process=image/resize,w_1200/format,webp';

function toAbsoluteUrl(imageUrl) {
  if (!imageUrl) {
    return '';
  }
  return /^https?:\/\//.test(imageUrl)
    ? imageUrl
    : `${getApiBaseUrl()}${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
}

function resolveImageUrl(imageUrl) {
  const resolvedUrl = toAbsoluteUrl(imageUrl);
  if (!resolvedUrl
      || !/^https?:\/\/pgcloud\.aitici\.com\//.test(resolvedUrl)
      || /[?&]x-oss-process=/.test(resolvedUrl)) {
    return resolvedUrl;
  }
  return `${resolvedUrl}${resolvedUrl.includes('?') ? '&' : '?'}${OSS_IMAGE_PROCESS}`;
}

// 预览用原始图地址：只补全相对路径，不追加 OSS 缩放/转格式参数，保证原图清晰且兼容预览。
function resolvePreviewUrl(imageUrl) {
  return toAbsoluteUrl(imageUrl);
}

module.exports = { resolveImageUrl, resolvePreviewUrl };
