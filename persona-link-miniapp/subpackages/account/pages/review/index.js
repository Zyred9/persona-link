const { authenticatedRequestData } = require('../../../../utils/request');

const REVIEW_SWIPE_MIN_DISTANCE = 60;

Page({
  data: { state: 'loading', error: '', participants: [], activeKey: 'self', title: '', question: null, questionIndex: 0, questionCount: 0, questionSlideClass: '' },

  onLoad(options) {
    const id = options.pairSessionId || options.answerSessionId;
    if (id && /^\d+$/.test(String(id))) {
      const section = options.pairSessionId ? 'pairs' : 'assessments';
      this.reviewUrl = `/api/miniapp/${section}/${encodeURIComponent(id)}/review`;
    }
  },

  onShow() { this.loadReview(); },
  onHide() { this.requestVersion = (this.requestVersion || 0) + 1; },
  onUnload() { this.requestVersion = (this.requestVersion || 0) + 1; },

  async loadReview() {
    const version = this.requestVersion = (this.requestVersion || 0) + 1;
    this.setData({ state: 'loading', error: '', participants: [], question: null });
    try {
      if (!this.reviewUrl) throw new Error('答卷参数缺失，请返回测试记录重新打开');
      const review = await authenticatedRequestData({ url: this.reviewUrl, sessionBound: true });
      if (version !== this.requestVersion) return;
      const participants = review && review.participants;
      if (!Array.isArray(participants) || !participants.length
          || participants.some((person) => !Array.isArray(person.questions) || !person.questions.length)) {
        throw new Error('历史作答内容缺失，暂时无法回顾');
      }
      this.setData({ participants, activeKey: 'self', state: 'ready' });
      this.showQuestion(0);
    } catch (error) {
      if (version === this.requestVersion) this.setData({ state: 'error', error: error.message || '作答回顾加载失败，请重试' });
    }
  },

  switchParticipant(event) {
    const key = event.currentTarget.dataset.key;
    if (key === this.data.activeKey || !this.data.participants.some((person) => person.key === key)) return;
    this.setData({ activeKey: key });
    this.showQuestion(0);
  },

  showQuestion(index, direction) {
    const person = this.data.participants.find((item) => item.key === this.data.activeKey);
    if (!person || index < 0 || index >= person.questions.length) return;
    const source = person.questions[index];
    const selected = new Set((source.selectedOptionIds || []).map(String));
    const slideClass = direction === 'next' ? 'review-panel--next' : direction === 'prev' ? 'review-panel--prev' : '';
    this.setData({
      title: person.title,
      questionIndex: index,
      questionCount: person.questions.length,
      questionSlideClass: '',
      question: Object.assign({}, source, {
        options: (source.options || []).map((option) => Object.assign({}, option, { selected: selected.has(String(option.optionId)) })),
        unanswered: selected.size === 0
      })
    }, () => {
      // 先清空动画类再设置，保证连续同向滑动也能重新触发动画。
      if (slideClass) this.setData({ questionSlideClass: slideClass });
    });
  },

  previousQuestion() { this.showQuestion(this.data.questionIndex - 1, 'prev'); },
  nextQuestion() { this.showQuestion(this.data.questionIndex + 1, 'next'); },

  handleReviewTouchStart(event) {
    const touch = event.touches && event.touches[0];
    if (!touch) return;
    this.reviewTouchStart = { x: touch.clientX, y: touch.clientY };
  },

  handleReviewTouchEnd(event) {
    const start = this.reviewTouchStart;
    this.reviewTouchStart = null;
    const touch = event.changedTouches && event.changedTouches[0];
    if (!start || !touch) return;
    const deltaX = touch.clientX - start.x;
    const deltaY = touch.clientY - start.y;
    if (Math.abs(deltaX) < REVIEW_SWIPE_MIN_DISTANCE || Math.abs(deltaX) <= Math.abs(deltaY)) return;
    if (deltaX < 0) this.nextQuestion(); else this.previousQuestion();
  }
});
