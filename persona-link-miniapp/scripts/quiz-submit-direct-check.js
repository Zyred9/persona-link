const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.js'), 'utf8');
const requests = [], events = [], toasts = [];
let definition;
vm.runInNewContext(source, {
  Component(value) { definition = value; },
  require: () => ({ trackEvent() {}, createIdempotencyKey: () => 'submit-id',
    authenticatedRequestData: (request) => new Promise((resolve, reject) => requests.push({ ...request, resolve, reject })) }),
  wx: { showToast({ title }) { toasts.push(title); } }
});
const runner = { ...definition.methods, data: { ...definition.data },
  setData(patch, callback) { Object.assign(this.data, patch); if (callback) callback(); },
  triggerEvent(name, detail) { events.push({ name, detail }); } };
const event = (optionId) => ({ currentTarget: { dataset: { questionToken: runner.data.questionToken, optionId } } });
const question = (id) => ({ questionId: id, questionText: `题${id}`, questionType: 1, required: true,
  minSelectCount: 1, maxSelectCount: 1, selectedOptionIds: [], options: [{ optionId: 'a' }, { optionId: 'b' }] });
const multiQuestion = (id) => ({ questionId: id, questionText: `题${id}`, questionType: 2, required: true,
  minSelectCount: 1, maxSelectCount: 2, selectedOptionIds: [], options: [{ optionId: 'a' }, { optionId: 'b' }] });
const resolveNext = async () => { requests.at(-1).resolve(); await new Promise(setImmediate); };

async function main() {
  runner.start({ answerSessionId: '1', assessment: { answerSessionId: '1', answerType: 1,
    questions: [question('1'), multiQuestion('2'), question('3')] } });

  // 单选题选定即自动保存并进入下一题。
  runner.chooseOption(event('a'));
  await resolveNext();
  assert.equal(runner.data.currentIndex, 1, '单选题选择后自动进入下一题');
  assert.equal(requests.at(-1).method, 'PUT');
  assert.equal(requests.at(-1).data.questionId, '1');
  assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['a']);

  // 多选题选择不提交，点击继续才保存并进入下一题。
  runner.chooseOption(event('a'));
  runner.chooseOption(event('b'));
  assert.equal(requests.at(-1).data.questionId, '1', '多选题选择后不得自动保存');
  let pending = runner.continueTest(event());
  await resolveNext();
  assert.equal(requests.at(-1).data.questionId, '2');
  assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['a', 'b']);
  assert.equal(runner.data.currentIndex, 2);

  assert.equal(runner.data.allAnswered, false, '第 3 题未答时不能直接交卷');
  const count = requests.length;
  await runner.submitDirect(event());
  assert.equal(requests.length, count, '未答完时直接交卷必须被拦截');

  // 最后一题只标记已选，不自动交卷。
  runner.chooseOption(event('a'));
  assert.equal(runner.data.currentIndex, 2, '最后一题选择后不得自动进入');
  assert.equal(requests.length, count, '最后一题选择后不得自动保存');
  assert.equal(runner.data.isLast, true);
  assert.equal(runner.data.allAnswered, true, '全部作答后允许直接交卷');

  // 返回上一题改动多选答案：不会立即保存。
  runner.goPrevious(event());
  assert.equal(runner.data.currentIndex, 1);
  runner.chooseOption(event('b'));
  assert.deepEqual(Array.from(runner.data.selectedOptionIds), ['a'], '多选题取消选择不立即保存');

  // 直接交卷先保存当前题修改，再补交未保存的最后一题，最后提交。
  pending = runner.submitDirect(event());
  assert.equal(requests.at(-1).method, 'PUT', '直接交卷先保存当前题修改');
  assert.equal(requests.at(-1).data.questionId, '2');
  assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['a']);
  await resolveNext();
  assert.equal(requests.at(-1).method, 'PUT', '翻回去改过后未保存的题目必须补交');
  assert.equal(requests.at(-1).data.questionId, '3');
  assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['a']);
  await resolveNext();
  assert.equal(requests.at(-1).method, 'POST', '全部保存后才调用交卷接口');
  requests.at(-1).resolve({ reportId: 'report' });
  await pending;
  assert.equal(runner.data.submissionPending, true);
  assert.ok(events.at(-1).detail.url.includes('/test/pages/result/'), '交卷后进入结果页');
  for (const [flow, expected] of [
    ['pair-partner', '/subpackages/pair/pages/result/index?pairSessionId=99'],
    ['pair-initiator', '/subpackages/pair/pages/invite/index?answerSessionId=1']
  ]) {
    runner.flow = flow;
    runner.pairSessionId = '99';
    const submission = runner.submitAssessment(runner.requestVersion);
    requests.at(-1).resolve({ reportId: 'report' });
    await submission;
    assert.equal(events.at(-1).detail.url, expected);
  }

  const markup = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.wxml'), 'utf8');
  assert.match(markup, /wx:if="\{\{allAnswered && !isLast && !submissionPending\}\}"/);
  assert.match(markup, /bindtap="submitDirect"/);
  assert.match(markup, />直接交卷</);
  assert.match(markup, /bindtap="continueTest"/, '继续按钮必须保留');
  assert.equal((markup.match(/data-question-token=/g) || []).length, 4);
  assert.ok(toasts.length >= 0);
  console.log('QUIZ_SUBMIT_DIRECT_CHECK_OK auto-advance/multi-manual/all-answered/guard/unsaved-answers/save-then-submit/navigation');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
