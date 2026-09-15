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
  assert.equal(page.data.titleImageUrl, '', '初始未配置头图不展示图片区域');
  for (const value of [undefined, null, '', '/uploads/title.png', 'https://example.com/title.png']) {
    titleImageUrl = value;
    await page.loadHome();
    assert.equal(page.data.state, 'ready');
    assert.equal(page.data.titleImageUrl, resolveImageUrl(value));
  }
  page.handleTitleImageError();
  assert.equal(page.data.titleImageUrl, '', '远程图片失败必须隐藏图片区域');
  page.setData = () => { throw new Error('头图已隐藏不应继续重设 src'); };
  page.handleTitleImageError();
  const template = fs.readFileSync(path.join(root, 'pages/home/index.wxml'), 'utf8');
  const titles = template.match(/<image[^>]*class="home-title-image[^>]*>/g);
  assert.equal(titles.length, 2);
  titles.forEach((tag) => {
    assert.ok(tag.includes('wx:if="{{titleImageUrl}}"'));
    assert.ok(tag.includes('src="{{titleImageUrl}}"'));
    assert.ok(tag.includes('binderror="handleTitleImageError"'));
  });
  const style = fs.readFileSync(path.join(root, 'pages/home/index.wxss'), 'utf8');
  assert.match(style, /\.category-list \{[\s\S]*?scrollbar-width: none;/, '分类横滑不能显示底部滚动条');
  assert.match(style, /\.category-list::-webkit-scrollbar \{\s*display: none;/, '微信内核同样隐藏分类滚动条');
  console.log('HOME_TITLE_CHECK_OK empty-hidden/configured/relative/image-error-hide/loading-ready/category-scrollbar');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
