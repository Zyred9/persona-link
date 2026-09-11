const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
let definition, modal;
const requests = [], toasts = [];
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/home/index.js'), 'utf8'), {
  Page(value) { definition = value; },
  require: () => ({ authenticatedRequestData: (request) => new Promise((resolve, reject) => requests.push({ ...request, resolve, reject })) }),
  wx: { showModal(value) { modal = value; }, showToast(value) { toasts.push(value); } }
});
const page = () => ({ ...definition, data: { ...definition.data, currentAssessment: { answerSessionId: '12', answerType: 2, canRestart: false } },
  setData(patch) { Object.assign(this.data, patch); }, openQuiz() { throw new Error('作废不能打开新答卷'); } });
async function main() {
  const home = page();
  home.abandonAssessment();
  assert.match(modal.content, /配对也将取消/);
  await modal.success({ confirm: false });
  assert.equal(home.data.abandoningAssessment, false);
  assert.equal(requests.length, 0);
  const oldLoad = home.loadCurrentAssessment();
  const old = requests.at(-1);
  home.abandonAssessment();
  const activeModal = modal;
  home.abandonAssessment(); assert.equal(modal, activeModal);
  home.continueAssessment();
  const confirm = modal.success({ confirm: true });
  assert.equal(requests.at(-1).url, '/api/miniapp/assessments/12/abandon');
  assert.equal(requests.at(-1).method, 'POST');
  assert.equal(requests.at(-1).sessionBound, true);
  requests.at(-1).resolve(); await confirm;
  assert.equal(home.data.currentAssessment, null);
  old.resolve({ answerSessionId: '12' }); await oldLoad;
  assert.equal(home.data.currentAssessment, null, '旧续答查询不能复活已作废答卷');
  const retry = page();
  retry.abandonAssessment();
  const failed = modal.success({ confirm: true });
  requests.at(-1).reject(new Error('网络失败')); await failed;
  assert.equal(retry.data.currentAssessment.answerSessionId, '12');
  assert.equal(retry.data.abandoningAssessment, false);
  retry.abandonAssessment(); modal.fail();
  assert.equal(retry.data.abandoningAssessment, false);
  console.log('ASSESSMENT_ABANDON_CHECK_OK confirm/cancel/pair/no-new-session/duplicate/failure/stale-current');
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
