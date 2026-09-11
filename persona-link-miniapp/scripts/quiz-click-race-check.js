const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.js'), 'utf8');
function setup(answerType = 1) {
  let definition;
  const requests = [], renders = [], toasts = [];
  vm.runInNewContext(source, {
    Component(value) { definition = value; },
    require: () => ({ trackEvent() {}, createIdempotencyKey: () => 'submit-id',
      authenticatedRequestData: (request) => new Promise((resolve, reject) => requests.push({ ...request, resolve, reject })) }),
    wx: { showToast({ title }) { toasts.push(title); } }
  });
  const runner = { ...definition.methods, data: { ...definition.data }, triggerEvent() {},
    setData(patch, callback) { Object.assign(this.data, patch); if (callback) renders.push(callback); } };
  function start(id = '1') {
    runner.start({ answerSessionId: id, assessment: { answerSessionId: id, answerType,
      questions: [1, 2, 3].map((number) => ({ questionId: String(number), questionText: `题${number}`,
        questionType: 2, required: true, minSelectCount: 1, maxSelectCount: 5,
        selectedOptionIds: [], options: [1, 2, 3, 4, 5].map((option) => ({ optionId: String(number * 10 + option) })) })) } });
  }
  function flush() { while (renders.length) renders.shift()(); }
  const event = (optionId) => ({ currentTarget: { dataset: { questionToken: runner.data.questionToken, optionId } } });
  start(); flush();
  return { runner, requests, renders, toasts, start, flush, event };
}

async function main() {
  for (const answerType of [1, 2]) {
    const { runner, requests, renders, toasts, start, flush, event } = setup(answerType);
    const oldOption = event('11'), oldContinue = event();
    runner.chooseOption(event('12'));
    const first = runner.continueTest(oldContinue);
    await runner.continueTest(oldContinue);
    runner.chooseOption(event('13'));
    runner.goPrevious(event());
    assert.equal(requests.length, 1, '保存中连点只能发一次请求');
    assert.deepEqual(Array.from(requests[0].data.optionIds), ['12']);
    requests[0].resolve(); await first;
    assert.equal(runner.data.currentIndex, 1);
    assert.equal(runner.data.options.length, 5);
    assert.equal(runner.data.saving, true, '下一题渲染完成前仍锁定');
    runner.chooseOption(event('21'));
    assert.equal(runner.data.selectedOptionIds.length, 0);
    flush();
    runner.chooseOption(oldOption);
    await runner.continueTest(oldContinue);
    runner.goPrevious(oldContinue);
    assert.equal(runner.data.currentIndex, 1);
    assert.equal(runner.data.selectedOptionIds.length, 0, '旧题选项不能污染第二题');
    assert.equal(requests.length, 1, '旧继续事件不能提交第二题');
    runner.chooseOption(event('11'));
    assert.equal(runner.data.selectedOptionIds.length, 0, '即使token有效也拒绝其他题选项');

    runner.data.selectedOptionIds = ['11', '21'];
    runner.assessment.questions[1].selectedOptionIds = ['11', '21'];
    await runner.continueTest(event());
    assert.equal(requests.length, 1, '污染恢复不得静默提交');
    assert.ok(!runner.data.selectedOptionIds.includes('11'));
    assert.ok(toasts.length > 0);
    if (!runner.data.selectedOptionIds.includes('21')) runner.chooseOption(event('21'));
    const failed = runner.continueTest(event());
    assert.equal(requests.at(-1).data.questionId, '2');
    assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['21']);
    requests.at(-1).reject(new Error('网络失败')); await failed;
    assert.equal(runner.data.saving, false);
    const secondEvent = event('22');
    runner.goPrevious(event()); flush();
    runner.chooseOption(secondEvent);
    assert.deepEqual(Array.from(runner.data.selectedOptionIds), ['12']);
    const retry = runner.continueTest(event()); requests.at(-1).resolve(); await retry; flush();
    const next = runner.continueTest(event()); requests.at(-1).resolve(); await next;
    assert.equal(runner.data.currentIndex, 2);

    const staleRender = renders.shift();
    assert.ok(staleRender);
    start('new-session');
    assert.equal(runner.data.saving, true);
    staleRender();
    assert.equal(runner.data.saving, true, '旧渲染回调不能解锁新答卷');
    flush();
    const prior = event('11');
    runner.chooseOption(prior);
    const oldSave = runner.continueTest(event());
    const pending = requests.at(-1);
    start('another-session'); flush();
    pending.resolve(); await oldSave;
    runner.chooseOption(prior);
    assert.equal(runner.data.currentIndex, 0);
    assert.equal(runner.data.selectedOptionIds.length, 0, '旧答卷响应与点击均失效');
  }
  const markup = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.wxml'), 'utf8');
  assert.equal((markup.match(/data-question-token=/g) || []).length, 4);
  console.log('QUIZ_CLICK_RACE_CHECK_OK single+pair/stale-events/render-lock/double-submit/invalid-selection/retry/session-switch');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
