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
let titleImageUrl;
vm.runInNewContext(fs.readFileSync(path.join(root, 'pages/home/index.js'), 'utf8'), {
  Page: (value) => { page = value; },
  require: () => ({ resolveImageUrl, request: async () => ({ code: 0, data: {
    titleImageUrl, categories: [{ categoryId: '1', categoryName: '性格' }]
  } }) })
});
page.setData = function(value) { Object.assign(this.data, value); };

async function run() {
  const fallback = '/assets/images/home-title.png';
  assert.equal(page.data.titleImageUrl, fallback, '初始加载必须有本地标题图');
  for (const value of [undefined, null, '', '/uploads/title.png', 'https://example.com/title.png']) {
    titleImageUrl = value;
    await page.loadHome();
    assert.equal(page.data.state, 'ready');
    assert.equal(page.data.titleImageUrl, resolveImageUrl(value) || fallback);
  }
  page.handleTitleImageError();
  assert.equal(page.data.titleImageUrl, fallback, '远程图片失败必须回退本地');
  page.setData = () => { throw new Error('本地图片失败不应继续重设 src'); };
  page.handleTitleImageError();
  const template = fs.readFileSync(path.join(root, 'pages/home/index.wxml'), 'utf8');
  const titles = template.match(/<image class="home-title-image[^>]+>/g);
  assert.equal(titles.length, 2);
  titles.forEach((tag) => {
    assert.ok(tag.includes('src="{{titleImageUrl}}"'));
    assert.ok(tag.includes('binderror="handleTitleImageError"'));
  });
  console.log('HOME_TITLE_CHECK_OK default/configured/relative/cleared/image-error/loading-ready');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
