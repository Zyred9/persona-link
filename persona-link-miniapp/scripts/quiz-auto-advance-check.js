const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.js'), 'utf8');

function setup(questions) {
  let definition;
  const requests = [], events = [], toasts = [];
  vm.runInNewContext(source, {
    Component(value) { definition = value; },
    require: () => ({ trackEvent() {}, createIdempotencyKey: () => 'submit-id',
      authenticatedRequestData: (request) => new Promise((resolve, reject) => requests.push({ ...request, resolve, reject })) }),
    wx: { showToast({ title }) { toasts.push(title); } }
  });
  const runner = { ...definition.methods, data: { ...definition.data },
    setData(patch, callback) { Object.assign(this.data, patch); if (callback) callback(); },
    triggerEvent(name, detail) { events.push({ name, detail }); } };
  runner.start({ answerSessionId: '1', assessment: { answerSessionId: '1', answerType: 1, questions } });
  const event = (optionId) => ({ currentTarget: { dataset: { questionToken: runner.data.questionToken, optionId } } });
  return { runner, requests, events, toasts, event };
}

const single = (id, required = true) => ({ questionId: id, questionText: `题${id}`, questionType: 1, required,
  minSelectCount: 1, maxSelectCount: 1, selectedOptionIds: [], options: [{ optionId: 'a' }, { optionId: 'b' }] });
const multiple = (id) => ({ questionId: id, questionText: `题${id}`, questionType: 2, required: true,
  minSelectCount: 1, maxSelectCount: 2, selectedOptionIds: [], options: [{ optionId: 'a' }, { optionId: 'b' }] });
const flush = () => new Promise((resolve) => setImmediate(resolve));

async function main() {
  // 单选题非最后一题：选定即自动保存并进入下一题。
  {
    const { runner, requests, event } = setup([single('1'), single('2')]);
    runner.chooseOption(event('a'));
    assert.equal(requests.length, 1, '单选题选择后必须自动保存');
    assert.equal(requests[0].method, 'PUT');
    assert.equal(requests[0].data.questionId, '1');
    assert.deepEqual(Array.from(requests[0].data.optionIds), ['a']);
    assert.equal(runner.data.saving, true, '自动进入期间锁定操作');
    requests[0].resolve();
    await flush();
    assert.equal(runner.data.currentIndex, 1, '单选题选择后自动进入下一题');
    assert.equal(runner.data.questionSlideClass, 'quiz-panel--next');
    assert.equal(runner.data.saving, false);
  }

  // 自动进入期间连点只能触发一次请求。
  {
    const { runner, requests, event } = setup([single('1'), single('2')]);
    runner.chooseOption(event('a'));
    runner.chooseOption(event('b'));
    assert.equal(requests.length, 1, '自动进入过程中连点只能触发一次保存');
    assert.deepEqual(Array.from(requests[0].data.optionIds), ['a']);
    requests[0].resolve();
    await flush();
    assert.equal(runner.data.currentIndex, 1);
  }

  // 自动保存失败：停留在当前题并提示，继续按钮可重试。
  {
    const { runner, requests, toasts, event } = setup([single('1'), single('2')]);
    runner.chooseOption(event('a'));
    requests[0].reject(new Error('网络失败'));
    await flush();
    assert.equal(runner.data.currentIndex, 0, '自动进入失败必须停留在当前题');
    assert.equal(runner.data.saving, false);
    assert.ok(toasts.length > 0, '自动保存失败必须提示用户');
    const retry = runner.continueTest(event());
    assert.equal(requests.length, 2, '继续按钮必须可重试保存');
    assert.equal(requests[1].data.questionId, '1');
    requests[1].resolve();
    await retry;
    assert.equal(runner.data.currentIndex, 1);
  }

  // 单选题最后一题：只标记已选，用户点击交卷后才提交。
  {
    const { runner, requests, events, event } = setup([single('1'), single('2')]);
    runner.chooseOption(event('a'));
    requests[0].resolve();
    await flush();
    assert.equal(runner.data.currentIndex, 1);
    runner.chooseOption(event('b'));
    assert.equal(requests.length, 1, '最后一题选择后不得自动交卷');
    assert.deepEqual(Array.from(runner.data.selectedOptionIds), ['b']);
    assert.equal(runner.data.isLast, true);
    const submit = runner.continueTest(event());
    assert.equal(requests.at(-1).data.questionId, '2');
    assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['b']);
    requests.at(-1).resolve();
    await flush();
    assert.equal(requests.at(-1).method, 'POST', '保存当前题后才调用交卷');
    requests.at(-1).resolve({ reportId: 'report' });
    await submit;
    assert.ok(events.at(-1).detail.url.includes('/test/pages/result/'), '交卷后进入结果页');
  }

  // 多选题：选择不自动进入，必须点击继续。
  {
    const { runner, requests, event } = setup([multiple('1'), multiple('2')]);
    runner.chooseOption(event('a'));
    runner.chooseOption(event('b'));
    assert.equal(requests.length, 0, '多选题选择后不得自动保存或进入下一题');
    assert.equal(runner.data.currentIndex, 0);
    assert.deepEqual(Array.from(runner.data.selectedOptionIds), ['a', 'b']);
    const next = runner.continueTest(event());
    assert.equal(requests.at(-1).data.questionId, '1');
    assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['a', 'b']);
    requests.at(-1).resolve();
    await next;
    assert.equal(runner.data.currentIndex, 1, '多选题点击继续后才进入下一题');
  }

  // 可选单选题取消选择时不自动进入，仍可由跳过按钮推进。
  {
    const { runner, requests, event } = setup([single('1', false), single('2')]);
    runner.chooseOption(event('a'));
    requests[0].resolve();
    await flush();
    assert.equal(runner.data.currentIndex, 1);
    runner.goPrevious(event());
    assert.equal(runner.data.currentIndex, 0);
    runner.chooseOption(event('a'));
    assert.equal(requests.length, 1, '取消选择不得触发保存或自动进入');
    assert.equal(runner.data.selectedOptionIds.length, 0);
    const skip = runner.continueTest(event());
    assert.deepEqual(Array.from(requests.at(-1).data.optionIds), [], '可跳过题目提交空选项');
    requests.at(-1).resolve();
    await skip;
    assert.equal(runner.data.currentIndex, 1);
  }

  const markup = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.wxml'), 'utf8');
  assert.match(markup, /bindtap="continueTest"/, '继续按钮必须保留');
  assert.match(markup, /上一题/);
  console.log('QUIZ_AUTO_ADVANCE_CHECK_OK single/auto-save/failure-retry/last-manual/multiple-manual/optional-skip/button-kept');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
