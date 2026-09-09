const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const pagePath = path.join(__dirname, '../subpackages/test/pages/result');
const source = fs.readFileSync(path.join(pagePath, 'index.js'), 'utf8');
const template = fs.readFileSync(path.join(pagePath, 'index.wxml'), 'utf8');

async function check(deepResult, fail = false) {
  let definition;
  const requests = [];
  vm.runInNewContext(source, {
    Page(value) { definition = value; },
    require(name) {
      if (name.endsWith('/analytics')) return { trackEvent() {} };
      if (name.endsWith('/report-access')) return { createReportAccess(page) { return {
        async run(load) {
          try { await load(() => true); }
          catch (error) { page.setData({ state: 'error' }); }
        }
      }; } };
      if (name.endsWith('/request')) return {
        async authenticatedRequestData({ url }) {
          requests.push(url);
          if (fail) throw new Error('加载失败');
          return { resultSnapshot: {
            resultName: '测试结果', basicResult: { text: '基础报告' }, deepResult,
            dimensions: [{ dimensionId: 1, dimensionName: '维度', normalizedScore: 60 }]
          } };
        }
      };
      throw new Error(`Unexpected module: ${name}`);
    }
  });
  const page = { ...definition, data: { ...definition.data }, reportId: 'report-1',
    setData(patch) { Object.assign(this.data, patch); } };
  await page.loadReport();
  assert.deepEqual(requests, ['/api/miniapp/reports/report-1']);
  assert.equal(page.data.state, fail ? 'error' : 'ready');
  if (!fail) {
    assert.equal(page.data.result.description, '基础报告');
    assert.equal(page.data.result.deepDescription, deepResult ? deepResult.text : '');
    assert.equal(page.data.result.dimensions[0].normalizedScore, 60);
  }
}

async function main() {
  assert.match(template, /wx:if="\{\{result\.deepDescription\}\}" class="deep-result-card/);
  assert.match(template, /<text class="deep-result-copy">\{\{result\.deepDescription\}\}<\/text>/);
  assert.doesNotMatch(source + template, /会员|开通|watchVideo|ensureSession/);
  await check({ text: '完整深度报告' });
  await check(null);
  await check(null, true);
  console.log('Free report checks passed.');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
