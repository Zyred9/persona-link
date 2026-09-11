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
const resolveNext = async () => { requests.at(-1).resolve(); await new Promise(setImmediate); };

async function main() {
  runner.start({ answerSessionId: '1', assessment: { answerSessionId: '1', answerType: 1,
    questions: [question('1'), question('2'), question('3')] } });

  runner.chooseOption(event('a'));
  let pending = runner.continueTest(event());
  await resolveNext();
  runner.chooseOption(event('a'));
  pending = runner.continueTest(event());
  await resolveNext();
  assert.equal(runner.data.currentIndex, 2);

  runner.goPrevious(event());
  assert.equal(runner.data.currentIndex, 1);
  assert.equal(runner.data.allAnswered, false, '第 3 题未答时不能直接交卷');
  const count = requests.length;
  await runner.submitDirect(event());
  assert.equal(requests.length, count, '未答完时直接交卷必须被拦截');

  pending = runner.continueTest(event());
  await resolveNext();
  // 第 3 题只选择不交卷，直接返回上一题修改。
  runner.chooseOption(event('b'));
  assert.equal(runner.data.allAnswered, true, '全部作答后允许直接交卷');
  assert.equal(runner.data.isLast, true);
  runner.goPrevious(event());
  assert.equal(runner.data.currentIndex, 1);
  runner.chooseOption(event('b'));

  pending = runner.submitDirect(event());
  assert.equal(requests.at(-1).method, 'PUT', '直接交卷先保存当前题修改');
  assert.equal(requests.at(-1).data.questionId, '2');
  assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['b']);
  await resolveNext();
  assert.equal(requests.at(-1).method, 'PUT', '翻回去改过后未保存的题目必须补交');
  assert.equal(requests.at(-1).data.questionId, '3');
  assert.deepEqual(Array.from(requests.at(-1).data.optionIds), ['b']);
  await resolveNext();
  assert.equal(requests.at(-1).method, 'POST', '全部保存后才调用交卷接口');
  requests.at(-1).resolve({ reportId: 'report' });
  await pending;
  assert.equal(runner.data.submissionPending, true);
  assert.ok(events.at(-1).detail.url.includes('/test/pages/result/'), '交卷后进入结果页');

  const markup = fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.wxml'), 'utf8');
  assert.match(markup, /wx:if="\{\{allAnswered && !isLast && !submissionPending\}\}"/);
  assert.match(markup, /bindtap="submitDirect"/);
  assert.match(markup, />直接交卷</);
  assert.equal((markup.match(/data-question-token=/g) || []).length, 4);
  assert.ok(toasts.length >= 0);
  console.log('QUIZ_SUBMIT_DIRECT_CHECK_OK all-answered/guard/unsaved-answers/save-then-submit/navigation');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
