const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = (file) => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
const event = (runner, optionId) => ({ currentTarget: { dataset: { questionToken: runner.data.questionToken, optionId } } });

function setup(answerType = 1, pairSessionId = null, required = true, answerStatus = 1) {
  let definition;
  const calls = [], events = [], toasts = [];
  let committed = answerStatus === 3;
  let loseResponse = true;
  let submitReply;
  vm.runInNewContext(source('components/quiz-runner/index.js'), {
    Component(d) { definition = d; },
    wx: { showToast(o) { toasts.push(o.title); } },
    require: (name) => name.endsWith('/analytics') ? { trackEvent() {} } : {
      createIdempotencyKey: () => 'same-submit',
      async authenticatedRequestData(request) {
        calls.push(request);
        if (request.method === 'PUT' && committed) throw new Error('答卷已结束');
        if (request.method === 'POST') {
          if (submitReply) return submitReply();
          committed = true;
          if (loseResponse) { loseResponse = false; throw new Error('响应丢失'); }
          return { reportId: 'report' };
        }
      }
    }
  });
  const runner = { ...definition.methods, data: { ...definition.data },
    setData(patch, callback) { Object.assign(this.data, patch); if (callback) callback(); }, triggerEvent(name, detail) { events.push({ name, detail }); } };
  runner.start({ answerSessionId: 'answer', flow: 'wrong-url-flow', assessment: {
    answerSessionId: 'answer', answerType, pairSessionId, answerStatus,
    questions: [{ questionId: 'q', questionType: 1, required, minSelectCount: 1, maxSelectCount: 1,
      selectedOptionIds: required ? ['a'] : [], options: [{ optionId: 'a' }] }]
  } });
  return { runner, calls, events, toasts, setSubmitReply(reply) { submitReply = reply; } };
}

async function main() {
  for (const [type, pair, expected] of [[1, null, '/test/pages/result/'], [2, null, '/pair/pages/invite/'], [2, 'pair', '/pair/pages/wait/']]) {
    const test = setup(type, pair);
    await test.runner.continueTest(event(test.runner));
    assert.equal(test.runner.data.submissionPending, true);
    test.runner.chooseOption(event(test.runner, 'b'));
    assert.equal(test.runner.data.selectedOptionIds[0], 'a', '提交结果不明时禁止改答案');
    await test.runner.continueTest(event(test.runner));
    assert.deepEqual(test.calls.map(c => c.method), ['PUT', 'POST', 'POST']);
    assert.equal(test.calls[1].data.submitRequestId, test.calls[2].data.submitRequestId);
    assert.ok(test.events.at(-1).detail.url.includes(expected));
    test.runner.navigationFailed();
    await test.runner.continueTest(event(test.runner));
    assert.equal(test.calls.at(-1).method, 'POST', '导航失败后仍可恢复');
    const reopened = setup(type, pair, true, 3);
    await reopened.runner.continueTest(event(reopened.runner));
    await reopened.runner.continueTest(event(reopened.runner));
    assert.equal(reopened.calls[0].method, 'POST', '重入已完成答卷不再保存答案');
    assert.ok(reopened.events.at(-1).detail.url.includes(expected));
  }
  const optional = setup(1, null, false);
  optional.runner.chooseOption(event(optional.runner, 'a'));
  optional.runner.chooseOption(event(optional.runner, 'a'));
  assert.equal(optional.runner.data.selectedOptionIds.length, 0, '可选单选可清空');
  await optional.runner.continueTest(event(optional.runner));
  assert.equal(optional.calls[0].data.optionIds.length, 0, '跳过题目提交空选项');
  const required = setup();
  required.runner.data.selectedOptionIds = [];
  await required.runner.continueTest(event(required.runner));
  assert.equal(required.calls.length, 0, '必答题不得跳过');
  const stale = setup();
  let rejectSubmit;
  stale.setSubmitReply(() => new Promise((resolve, reject) => { rejectSubmit = reject; }));
  const previousSubmit = stale.runner.continueTest(event(stale.runner));
  await new Promise(setImmediate);
  stale.runner.start({ answerSessionId: 'new', assessment: {
    ...stale.runner.assessment, answerSessionId: 'new', answerStatus: 3
  } });
  rejectSubmit(Object.assign(new Error('old validation'), { statusCode: 400 }));
  await previousSubmit;
  assert.equal(stale.runner.submissionPending, true, '旧提交错误不能覆盖新答卷提交状态');
  let home;
  let confirms = 0;
  vm.runInNewContext(source('pages/home/index.js'), {
    Page(d) { home = d; }, require: () => ({}), wx: { showModal() { confirms++; } }
  });
  home.data.currentAssessment = { answerSessionId: 'partner', canRestart: false };
  home.setData = (patch) => Object.assign(home.data, patch);
  home.abandonAssessment();
  assert.equal(confirms, 1, '受邀者也可以确认作废，不再重新创建答卷');
  assert.ok(source('pages/home/index.wxml').includes('bindtap="abandonAssessment"'));
  for (const file of ['components/test-detail/index.js', 'components/account-center/index.js']) {
    let component;
    let destination;
    vm.runInNewContext(source(file), {
      Component(d) { component = d; }, require: () => ({}), wx: { navigateTo(o) { destination = o.url; } }
    });
    component.methods.joinPair();
    assert.equal(destination, '/subpackages/pair/pages/join/index');
    assert.ok(source(file.replace('.js', '.wxml')).includes('bindtap="joinPair"'));
  }
  console.log('ASSESSMENT_RECOVERY_CHECK_OK single/pair/resume/reopen/submit-retry/navigation-failure/optional');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
