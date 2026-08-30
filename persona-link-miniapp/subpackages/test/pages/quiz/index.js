const QUESTION_TITLES = [
  '接到一个全新任务时，你通常会？',
  '团队讨论陷入沉默时，你更可能？',
  '同事突然来寻求帮助时，你会？',
  '计划快到截止时间时，你倾向于？',
  '面对不熟悉的工作领域，你通常？',
  '团队意见发生分歧时，你会？',
  '一天同时收到多项任务时，你先？',
  '当项目出现变动时，你更倾向于？',
  '有人指出你的方案有问题时，你会？',
  '需要做一个快速决定时，你通常？',
  '团队获得好结果时，你更在意？',
  '工作中出现小失误时，你会？',
  '遇到重复又琐碎的任务时，你通常？',
  '合作伙伴回复很慢时，你会？',
  '需要公开表达观点时，你倾向于？',
  '一个人完成复杂任务时，你会？',
  '临时被邀请加入新项目时，你通常？',
  '任务要求不够清楚时，你会？',
  '团队节奏变得很快时，你倾向于？',
  '看到同事压力很大时，你会？',
  '复盘一次不理想的合作时，你更关注？',
  '完成一天工作后，你最希望？'
];

const PAIR_QUESTION_TITLES = QUESTION_TITLES.concat([
  '两个人安排周末时，你更希望？',
  '对方情绪低落时，你通常会？',
  '共同计划临时改变时，你更在意？',
  '发生小摩擦以后，你倾向于？',
  '两个人需要做决定时，你更习惯？',
  '对方需要独处时，你通常会？',
  '一起完成一件事时，你最看重？',
  '你希望彼此用什么方式表达在意？'
]);

const OPTIONS = [
  { value: 'A', text: '先快速评估影响，调整计划' },
  { value: 'B', text: '听取大家意见，再一起决定' },
  { value: 'C', text: '先行动再说，边做边调整' },
  { value: 'D', text: '有点慌，先等别人安排' }
];

const SINGLE_STORAGE_PREFIX = 'single-test-draft-';

Page({
  data: {
    currentIndex: 0,
    questionNumber: '01',
    total: QUESTION_TITLES.length,
    progress: 5,
    question: QUESTION_TITLES[0],
    options: OPTIONS,
    selected: '',
    answers: [],
    isLast: false,
    mode: 'single',
    nextTarget: 'invite',
    storageKey: `${SINGLE_STORAGE_PREFIX}horse`,
    pageTitle: '单人测试',
    testId: 'horse'
  },

  onLoad(options) {
    const mode = options.mode === 'pair' ? 'pair' : 'single';
    const testId = options.id || 'horse';
    const singleTotal = Number(options.count) === 20 ? 20 : QUESTION_TITLES.length;
    const questions = mode === 'pair' ? PAIR_QUESTION_TITLES : QUESTION_TITLES.slice(0, singleTotal);
    const storageKey = mode === 'pair' ? 'pair-test-draft' : `${SINGLE_STORAGE_PREFIX}${testId}`;
    const draft = wx.getStorageSync(storageKey);
    const answers = draft && Array.isArray(draft.answers) ? draft.answers : [];
    const currentIndex = draft && Number.isInteger(draft.currentIndex)
      ? Math.min(draft.currentIndex, questions.length - 1)
      : 0;
    this.questions = questions;
    this.setData({
      answers,
      mode,
      nextTarget: options.next === 'wait' ? 'wait' : 'invite',
      storageKey,
      pageTitle: mode === 'pair' ? '双人测试' : '单人测试',
      testId,
      total: questions.length
    });
    this.showQuestion(currentIndex);
  },

  chooseOption(event) {
    const selected = event.currentTarget.dataset.value;
    const answers = this.data.answers.slice();
    answers[this.data.currentIndex] = selected;
    this.setData({ selected, answers });
    this.saveDraft(this.data.currentIndex, answers);
  },

  goPrevious() {
    if (this.data.currentIndex === 0) {
      wx.navigateBack();
      return;
    }
    this.showQuestion(this.data.currentIndex - 1);
  },

  continueTest() {
    if (!this.data.selected) {
      wx.showToast({ title: '请先选择一个答案', icon: 'none' });
      return;
    }
    if (this.data.isLast) {
      wx.removeStorageSync(this.data.storageKey);
      if (this.data.mode === 'pair' && this.data.nextTarget === 'wait') {
        const pairSession = wx.getStorageSync('personaPairSession') || {};
        pairSession.selfCompleted = true;
        wx.setStorageSync('personaPairSession', pairSession);
      }
      const url = this.data.mode === 'pair'
        ? `/subpackages/pair/pages/${this.data.nextTarget}/index`
        : `/subpackages/test/pages/result/index?id=${this.data.testId}`;
      wx.redirectTo({ url });
      return;
    }
    const nextIndex = this.data.currentIndex + 1;
    this.saveDraft(nextIndex, this.data.answers);
    this.showQuestion(nextIndex);
  },

  showQuestion(currentIndex) {
    const questions = this.questions || QUESTION_TITLES;
    const number = currentIndex + 1;
    this.setData({
      currentIndex,
      questionNumber: number < 10 ? `0${number}` : String(number),
      progress: Math.round(number / questions.length * 100),
      question: questions[currentIndex],
      selected: this.data.answers[currentIndex] || '',
      isLast: currentIndex === questions.length - 1
    });
  },

  saveDraft(currentIndex, answers) {
    wx.setStorageSync(this.data.storageKey, { currentIndex, answers });
  }
});
