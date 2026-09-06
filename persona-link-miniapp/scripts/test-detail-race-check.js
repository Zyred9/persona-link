const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const detailRequests = [];
const assessmentRequests = [];
const navigations = [];
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
    showToast() {}
  }
});

function createInstance(data) {
  const instance = {
    data: Object.assign({}, definition.data, data),
    setData(patch) {
      Object.assign(this.data, patch);
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
    const customLoad = instance.loadTest('12');
    detailRequests.at(-1).resolve({ testType, detailImageUrl: 'https://example.com/detail.png' });
    await customLoad;
    assert.strictEqual(instance.data.test.imageUrl, 'https://example.com/detail.png');
    for (const detailImageUrl of [undefined, null, '']) {
      const legacyLoad = instance.loadTest('12');
      detailRequests.at(-1).resolve({ testType, coverUrl: 'https://example.com/cover.png', detailImageUrl });
      await legacyLoad;
      assert.strictEqual(instance.data.test.imageUrl,
        `/assets/images/${testType === 2 ? 'pair' : 'single'}-detail-hero.png`,
        '未配置详情图应使用默认整图，不能回退到首页封面');
    }
  }
  const template = fs.readFileSync(path.join(__dirname, '../components/test-detail/index.wxml'), 'utf8');
  assert.strictEqual((template.match(/src="{{test.imageUrl}}" mode="widthFix"/g) || []).length, 2,
    '单人和双人详情图均应完整等比例展示');

  console.log('TEST_DETAIL_RACE_CHECK_OK');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
