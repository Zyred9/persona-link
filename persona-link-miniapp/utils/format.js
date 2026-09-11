function pad(value) {
  return String(value).padStart(2, '0');
}

// 后端 LocalDateTime 序列化为 ISO 字符串（2026-09-10T13:58:02），统一转为「年-月-日 时:分:秒」展示。
function formatDateTime(value) {
  if (!value) return '';
  const date = new Date(String(value).replace(' ', 'T'));
  if (Number.isNaN(date.getTime())) return String(value);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

module.exports = { formatDateTime };
