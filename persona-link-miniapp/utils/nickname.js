// 微信昵称按“最多16个汉字或32个字符”计量：ASCII 字符算 1 个长度单位，其余字符算 2 个。
const WECHAT_NICKNAME_MAX_UNITS = 32;

function wechatNicknameUnits(value) {
  let units = 0;
  for (const char of String(value || '')) {
    units += char.codePointAt(0) <= 0x7f ? 1 : 2;
  }
  return units;
}

module.exports = { WECHAT_NICKNAME_MAX_UNITS, wechatNicknameUnits };
