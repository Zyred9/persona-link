const { authenticatedRequestData, createIdempotencyKey } = require('../../utils/request');
const { trackEvent } = require('../../utils/analytics');

Component({
  options: {
    styleIsolation: 'apply-shared'
  },

  data: {
    state: 'loading',
    currentIndex: 0,
    questionNumber: '01',
    total: 0,
    progress: 0,
    question: '',
    options: [],
    selectedOptionIds: [],
    questionTypeText: '单选题',
    minSelectCount: 1,
    maxSelectCount: 1,
    isMultiple: false,
    isLast: false,
    saving: false,
    errorDescription: '暂时无法加载答卷，请稍后重试。'
  },

  lifetimes: {
    detached() {
      this.requestVersion = (this.requestVersion || 0) + 1;
      this.assessment = null;
    }
  },

  methods: {
    start(options) {
      const source = options || {};
      const answerSessionId = String(source.answerSessionId || '');
      this.requestVersion = (this.requestVersion || 0) + 1;
      this.answerSessionId = answerSessionId;
      this.flow = source.flow || 'single';
      this.pairSessionId = source.pairSessionId || '';
      this.submitRequestId = null;
      this.assessment = null;
      this.setData({
        state: 'loading',
        currentIndex: 0,
        questionNumber: '01',
        total: 0,
        progress: 0,
        question: '',
        options: [],
        selectedOptionIds: [],
        isLast: false,
        saving: false
      });
      if (!answerSessionId) {
        this.setData({ state: 'error', errorDescription: '答卷参数缺失，请返回题型详情页重新开始。' });
        return;
      }
      if (source.assessment && String(source.assessment.answerSessionId) === answerSessionId) {
        try {
          this.applyAssessment(source.assessment);
        } catch (error) {
          this.setData({ state: 'error', errorDescription: error.message || '答卷加载失败' });
        }
        return;
      }
      this.loadAssessment();
    },

    deactivate() {
      this.requestVersion = (this.requestVersion || 0) + 1;
      this.assessment = null;
      this.setData({ saving: false });
    },

    async loadAssessment() {
      if (!this.answerSessionId) {
        this.setData({ state: 'error', errorDescription: '答卷参数缺失，请返回题型详情页重新开始。' });
        return;
      }
      const requestVersion = (this.requestVersion || 0) + 1;
      this.requestVersion = requestVersion;
      this.setData({ state: 'loading' });
      try {
        const assessment = await authenticatedRequestData({
          url: `/api/miniapp/assessments/${encodeURIComponent(this.answerSessionId)}`
        });
        if (this.requestVersion !== requestVersion) return;
        this.applyAssessment(assessment);
      } catch (error) {
        if (this.requestVersion !== requestVersion) return;
        this.setData({ state: 'error', errorDescription: error.message || '答卷加载失败' });
      }
    },

    applyAssessment(assessment) {
      if (!assessment || !Array.isArray(assessment.questions) || assessment.questions.length === 0) {
        throw new Error('答卷中没有可作答的题目');
      }
      this.assessment = assessment;
      const currentIndex = Math.min(
        Math.max(Number(assessment.firstUnansweredIndex) || 0, 0),
        assessment.questions.length - 1
      );
      this.setData({ state: 'ready', total: assessment.questions.length });
      this.showQuestion(currentIndex);
    },

    chooseOption(event) {
      if (this.data.saving) return;
      const optionId = String(event.currentTarget.dataset.optionId);
      let selectedOptionIds = this.data.selectedOptionIds.slice();
      if (this.data.isMultiple) {
        const selectedIndex = selectedOptionIds.indexOf(optionId);
        if (selectedIndex >= 0) selectedOptionIds.splice(selectedIndex, 1);
        else if (selectedOptionIds.length >= this.data.maxSelectCount) {
          wx.showToast({ title: `最多选择${this.data.maxSelectCount}项`, icon: 'none' });
          return;
        } else selectedOptionIds.push(optionId);
      } else {
        selectedOptionIds = [optionId];
      }
      this.assessment.questions[this.data.currentIndex].selectedOptionIds = selectedOptionIds.slice();
      this.applySelection(selectedOptionIds);
    },

    applySelection(selectedOptionIds) {
      const selected = new Set(selectedOptionIds);
      this.setData({
        selectedOptionIds,
        options: this.data.options.map((option) => Object.assign({}, option, {
          selected: selected.has(option.optionId)
        }))
      });
    },

    goPrevious() {
      if (this.data.currentIndex === 0) {
        this.triggerEvent('back');
        return;
      }
      this.showQuestion(this.data.currentIndex - 1);
    },

    async continueTest() {
      if (this.data.saving) return;
      const selectedCount = this.data.selectedOptionIds.length;
      if (selectedCount < this.data.minSelectCount || selectedCount > this.data.maxSelectCount) {
        wx.showToast({
          title: this.data.isMultiple
            ? `请选择${this.data.minSelectCount}-${this.data.maxSelectCount}项`
            : '请先选择一个答案',
          icon: 'none'
        });
        return;
      }
      const requestVersion = this.requestVersion;
      this.setData({ saving: true });
      try {
        const question = this.assessment.questions[this.data.currentIndex];
        await authenticatedRequestData({
          url: `/api/miniapp/assessments/${encodeURIComponent(this.answerSessionId)}/answers`,
          method: 'PUT',
          data: { questionId: question.questionId, optionIds: this.data.selectedOptionIds.slice() }
        });
        if (this.requestVersion !== requestVersion) return;
        if (this.data.isLast) {
          await this.submitAssessment(requestVersion);
          return;
        }
        this.setData({ saving: false });
        this.showQuestion(this.data.currentIndex + 1);
      } catch (error) {
        if (this.requestVersion !== requestVersion) return;
        this.setData({ saving: false });
        wx.showToast({ title: error.message || '答案保存失败', icon: 'none' });
      }
    },

    async submitAssessment(requestVersion) {
      this.submitRequestId = this.submitRequestId || createIdempotencyKey('submit');
      const report = await authenticatedRequestData({
        url: `/api/miniapp/assessments/${encodeURIComponent(this.answerSessionId)}/submit`,
        method: 'POST',
        data: { submitRequestId: this.submitRequestId }
      });
      if (this.requestVersion !== requestVersion) return;
      trackEvent(4, '/subpackages/test/pages/quiz/index', this.assessment.testId);
      let url;
      if (this.flow === 'pair-initiator') {
        url = `/subpackages/pair/pages/invite/index?answerSessionId=${encodeURIComponent(this.answerSessionId)}`;
      } else if (this.flow === 'pair-partner') {
        url = `/subpackages/pair/pages/wait/index?pairSessionId=${encodeURIComponent(this.pairSessionId)}`;
      } else {
        if (!report || !report.reportId) throw new Error('报告生成失败');
        url = `/subpackages/test/pages/result/index?reportId=${encodeURIComponent(report.reportId)}`;
      }
      this.triggerEvent('finish', { url });
    },

    navigationFailed() {
      this.setData({ saving: false });
    },

    showQuestion(currentIndex) {
      const questions = this.assessment.questions;
      const question = questions[currentIndex];
      const selectedOptionIds = Array.isArray(question.selectedOptionIds)
        ? question.selectedOptionIds.map(String)
        : [];
      const selected = new Set(selectedOptionIds);
      const number = currentIndex + 1;
      const isMultiple = Number(question.questionType) === 2;
      this.setData({
        currentIndex,
        questionNumber: number < 10 ? `0${number}` : String(number),
        progress: Math.round(number / questions.length * 100),
        question: question.questionText,
        options: (question.options || []).map((option, index) => Object.assign({}, option, {
          optionCode: String.fromCharCode(65 + index),
          optionId: String(option.optionId),
          selected: selected.has(String(option.optionId))
        })),
        selectedOptionIds,
        minSelectCount: question.minSelectCount,
        maxSelectCount: question.maxSelectCount,
        isMultiple,
        questionTypeText: isMultiple
          ? `多选题（${question.minSelectCount}-${question.maxSelectCount}项）`
          : '单选题',
        isLast: currentIndex === questions.length - 1
      });
    },

    retry() {
      this.loadAssessment();
    }
  }
});
