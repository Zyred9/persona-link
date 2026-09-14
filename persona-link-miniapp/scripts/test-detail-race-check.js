const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const detailRequests = [];
const assessmentRequests = [];
const navigations = [];
const previews = [];
const toasts = [];
let definition;

const imageModule = { exports: {} };
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/image.js'), 'utf8'), {
  module: imageModule,
  require: () => ({ getApiBaseUrl: () => 'https://api.example.com' })
});
assert.strictEqual(imageModule.exports.resolveImageUrl('/uploads/cover.png'), 'https://api.example.com/uploads/cover.png');
assert.strictEqual(imageModule.exports.resolveImageUrl(''), '');

const source = fs.readFileSync(path.join(__dirname, '../components/test-detail/index.js'), 'utf8');
vm.runInNewContext(source, {
  Component(value) {
    definition = value;
  },
  require(moduleName) {
    if ('../../utils/request' === moduleName) {
      return {
        requestData: () => new Promise((resolve, reject) => detailRequests.push({ resolve, reject })),
        authenticatedRequestData: () => new Promise((resolve, reject) => assessmentRequests.push({ resolve, reject })),
        createIdempotencyKey: () => 'assessment-key'
      };
    }
    if ('../../utils/analytics' === moduleName) {
      return { trackEvent() {} };
    }
    if ('../../utils/image' === moduleName) {
      return imageModule.exports;
    }
    throw new Error(`Unexpected module: ${moduleName}`);
  },
  wx: {
    navigateTo(options) {
      navigations.push(options.url);
      if (options.success) options.success();
      if (options.complete) options.complete();
    },
    previewImage(options) { previews.push(options); },
    showToast(options) { toasts.push(options); }
  }
});

function createInstance(data) {
  const instance = {
    data: Object.assign({}, definition.data, data),
    events: [],
    setData(patch) {
      Object.assign(this.data, patch);
    },
    triggerEvent(name) {
      this.events.push(name);
    }
  };
  Object.entries(definition.methods).forEach(([name, method]) => {
    instance[name] = method.bind(instance);
  });
  return instance;
}

async function main() {
  const detail = createInstance({ testId: '12', testType: 1 });
  const oldLoad = detail.loadTest('12');
  detail.data.testId = '';
  await detail.loadTest('');
  detail.data.testId = '12';
  const newLoad = detail.loadTest('12');

  detailRequests[1].resolve({ testType: 1, title: 'new detail', coverUrl: 'https://example.com/cover.png', detailImageUrl: '/uploads/detail.png' });
  await newLoad;
  detailRequests[0].reject(new Error('old failure'));
  await oldLoad;
  assert.strictEqual(detail.data.test.title, 'new detail', '旧请求覆盖了新详情');
  assert.strictEqual(detail.data.test.imageUrl, 'https://api.example.com/uploads/detail.png', '详情必须使用独立详情图，并解析相对地址');

  const start = detail.startAssessment();
  detail.deactivate();
  assessmentRequests[0].resolve({ answerSessionId: 'stale-session' });
  await start;
  assert.strictEqual(navigations.length, 0, '关闭详情后仍跳转到了旧答卷');

  for (const testType of [1, 2]) {
    const instance = createInstance({ testId: '12', testType });
    instance.previewDetailImage();
    const previewCount = previews.length;
    const customLoad = instance.loadTest('12');
    const originalUrl = 'https://pgcloud.aitici.com/common/detail.png';
    detailRequests.at(-1).resolve({ testType, detailImageUrl: originalUrl });
    await customLoad;
    assert.strictEqual(instance.data.test.imageUrl, imageModule.exports.resolveImageUrl(originalUrl));
    instance.previewDetailImage();
    assert.strictEqual(previews.length, previewCount + 1);
    assert.strictEqual(previews.at(-1).current, originalUrl, '预览应使用原图');
    assert.strictEqual(previews.at(-1).urls[0], originalUrl);
    assert.strictEqual(previews.at(-1).showmenu, true, '预览应允许长按保存');
    previews.at(-1).fail();
    assert.ok(toasts.at(-1).title.includes('失败'));
    for (const detailImageUrl of [undefined, null, '']) {
      const legacyLoad = instance.loadTest('12');
      detailRequests.at(-1).resolve({ testType, coverUrl: 'https://example.com/cover.png', detailImageUrl });
      await legacyLoad;
      assert.strictEqual(instance.data.test.imageUrl, '',
        '未配置详情图时不使用默认整图，也不回退到首页封面');
      instance.previewDetailImage();
      assert.strictEqual(previews.length, previewCount + 1, '无图不得打开预览');
    }
  }
  const swipe = createInstance({ testId: '12', testType: 1 });
  swipe.handleSwipeStart({ touches: [{ clientX: 120, clientY: 200 }] });
  swipe.handleSwipeEnd({ changedTouches: [{ clientX: 260, clientY: 210 }] });
  assert.deepStrictEqual(swipe.events, ['back'], '左向右滑动必须触发返回首页');
  swipe.handleSwipeStart({ touches: [{ clientX: 260, clientY: 200 }] });
  swipe.handleSwipeEnd({ changedTouches: [{ clientX: 120, clientY: 210 }] });
  assert.deepStrictEqual(swipe.events, ['back'], '从右向左滑动不得返回');
  swipe.handleSwipeStart({ touches: [{ clientX: 120, clientY: 200 }] });
  swipe.handleSwipeEnd({ changedTouches: [{ clientX: 140, clientY: 320 }] });
  assert.deepStrictEqual(swipe.events, ['back'], '纵向滑动不得触发返回');

  const template = fs.readFileSync(path.join(__dirname, '../components/test-detail/index.wxml'), 'utf8');
  assert.strictEqual((template.match(/bindtap="previewDetailImage"/g) || []).length, 2);
  assert.strictEqual((template.match(/src="{{test.imageUrl}}" mode="widthFix"/g) || []).length, 2,
    '单人和双人详情图均应完整等比例展示');
  assert.match(template, /bindtouchstart="handleSwipeStart"/);
  assert.match(template, /bindtouchend="handleSwipeEnd"/);
  assert.match(fs.readFileSync(path.join(__dirname, '../pages/home/index.wxml'), 'utf8'),
    /bind:back="closeTestDetail"/, '首页详情必须把滑动返回接到关闭详情');
  for (const file of ['subpackages/test/pages/detail/index', 'subpackages/pair/pages/detail/index']) {
    assert.match(fs.readFileSync(path.join(__dirname, `../${file}.wxml`), 'utf8'),
      /bind:back="backToHome"/, `${file} 必须把滑动返回接到首页`);
    assert.match(fs.readFileSync(path.join(__dirname, `../${file}.js`), 'utf8'), /backToHome\s*\(/);
  }

  console.log('TEST_DETAIL_RACE_CHECK_OK');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
