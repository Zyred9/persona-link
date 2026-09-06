const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

let definition;
const requests = [];
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../components/quiz-runner/index.js'), 'utf8'), {
  Component(value) { definition = value; },
  require(name) {
    if (name === '../../utils/request') return {
      authenticatedRequestData: async (request) => { requests.push(request); },
      createIdempotencyKey: () => 'test'
    };
    if (name === '../../utils/analytics') return { trackEvent() {} };
    throw new Error(`Unexpected module: ${name}`);
  },
  wx: { showToast() { throw new Error('Unexpected validation failure'); } }
});

async function main() {
  for (const questionType of [1, 2]) {
    const options = ['D', 'C', 'B', 'A'].map((optionCode, index) => ({
      optionId: 100 + index, optionCode, optionText: `内容${index}`, scoreValue: index
    }));
    const question = {
      questionId: 'question-1', questionType, questionText: '题目',
      minSelectCount: 1, maxSelectCount: questionType, options, selectedOptionIds: [101]
    };
    const instance = {
      ...definition.methods,
      data: { ...definition.data },
      answerSessionId: 'session-1',
      setData(patch) { Object.assign(this.data, patch); }
    };
    instance.applyAssessment({ questions: [question, { ...question, questionId: 'question-2' }] });
    assert.deepStrictEqual(Array.from(instance.data.options, (option) => option.optionCode), ['A', 'B', 'C', 'D']);
    assert.deepStrictEqual(Array.from(instance.data.options, (option) => [option.optionId, option.optionText, option.scoreValue]),
      options.map((option) => [String(option.optionId), option.optionText, option.scoreValue]));
    assert.strictEqual(instance.data.options[1].selected, true, '已选答案必须按原 id 回显');
    assert.deepStrictEqual(options.map((option) => option.optionCode), ['D', 'C', 'B', 'A'], '展示重编号不得修改原始答卷');
    instance.chooseOption({ currentTarget: { dataset: { optionId: '103' } } });
    await instance.continueTest();
    assert.deepStrictEqual(Array.from(requests.at(-1).data.optionIds), questionType === 1 ? ['103'] : ['101', '103'],
      '单选、多选必须提交原选项 id');
    assert.strictEqual(requests.at(-1).data.questionId, 'question-1');
  }
  console.log('Quiz option order checks passed.');
}

main().catch((error) => { console.error(error); process.exitCode = 1; });
