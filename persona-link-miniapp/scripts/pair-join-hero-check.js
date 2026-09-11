const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { resolveImageUrl } = require('../utils/image');
global.wx = {
  getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } }),
  getStorageSync: () => 'https://example.com'
};

const root = path.join(__dirname, '..');
let page;
let config = {};
let failConfig = false;
vm.runInNewContext(fs.readFileSync(path.join(root, 'subpackages/pair/pages/join/index.js'), 'utf8'), {
  Page: (value) => { page = value; },
  require: (moduleName) => {
    if (moduleName.endsWith('/image')) return { resolveImageUrl };
    if (moduleName.endsWith('/request')) {
      return {
        requestData: async () => {
          if (failConfig) throw new Error('network');
          return config;
        },
        authenticatedRequestData: async () => ({}),
        createIdempotencyKey: () => 'pair-join-hero-check'
      };
    }
    return {};
  }
});
page.setData = function(value) { Object.assign(this.data, value); };

async function run() {
  assert.equal(page.data.joinHeroImageUrl, '', '初始加载必须隐藏头图');
  for (const value of [undefined, null, '', 'https://example.com/pair.png', '/uploads/pair-hero.png']) {
    config = { joinHeroImageUrl: value };
    await page.loadJoinHeroImage();
    assert.equal(page.data.joinHeroImageUrl, resolveImageUrl(value));
  }
  failConfig = true;
  await page.loadJoinHeroImage();
  assert.equal(page.data.joinHeroImageUrl, '', '配置读取失败必须隐藏头图');
  failConfig = false;
  page.handleJoinHeroImageError();
  assert.equal(page.data.joinHeroImageUrl, '', '图片加载失败必须隐藏头图');
  const template = fs.readFileSync(path.join(root, 'subpackages/pair/pages/join/index.wxml'), 'utf8');
  const hero = template.match(/<image[\s\S]*?join-hero[\s\S]*?>/);
  assert.ok(hero, '加入页头图元素缺失');
  assert.ok(hero[0].includes('wx:if="{{joinHeroImageUrl}}"'), '头图未按配置值控制展示');
  assert.ok(hero[0].includes('src="{{joinHeroImageUrl}}"'), '头图未绑定配置图片地址');
  assert.ok(hero[0].includes('binderror="handleJoinHeroImageError"'), '头图缺少加载失败隐藏处理');
  console.log('PAIR_JOIN_HERO_CHECK_OK default/configured/relative/cleared/request-error/image-error');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
