const { authenticatedRequestData, createIdempotencyKey } = require('../../utils/request');
const { trackEvent } = require('../../utils/analytics');

const QUIZ_SWIPE_MIN_DISTANCE = 60;

Component({
  options: {
    styleIsolation: 'apply-shared'
  },

  data: {
    state: 'loading',
    currentIndex: 0,
    questionToken: 0,
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
    required: true,
    submissionPending: false,
    isLast: false,
    saving: false,
    allAnswered: false,
    questionSlideClass: '',
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
      this.submissionPending = false;
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
        saving: false,
        allAnswered: false,
        questionSlideClass: ''
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
      // 服务端返回的题目与已保存答案一致；本地改动后再标记为未保存。
      assessment.questions.forEach((question) => { question.saved = true; });
      this.assessment = assessment;
      // 路由参数不能覆盖服务端答卷所属的业务流程。
      this.pairSessionId = assessment.pairSessionId || '';
      this.flow = Number(assessment.answerType) === 2
        ? (this.pairSessionId ? 'pair-partner' : 'pair-initiator') : 'single';
      this.triggerEvent('context', { answerType: Number(assessment.answerType) || 1 });
      this.submissionPending = Number(assessment.answerStatus) === 3;
      this.setData({ submissionPending: this.submissionPending });
      const currentIndex = Math.min(
        Math.max(Number(assessment.firstUnansweredIndex) || 0, 0),
        assessment.questions.length - 1
      );
      this.setData({ state: 'ready', total: assessment.questions.length });
      this.showQuestion(currentIndex);
    },

    isCurrentQuestionEvent(event) {
      return this.data.state === 'ready' && this.assessment
        && event && event.currentTarget && event.currentTarget.dataset
        && String(event.currentTarget.dataset.questionToken) === String(this.questionToken);
    },

    clearInvalidSelection() {
      const question = this.assessment.questions[this.data.currentIndex];
      const optionIds = new Set((question.options || []).map((option) => String(option.optionId)));
      const selectedOptionIds = this.data.selectedOptionIds.filter((id) => optionIds.has(String(id)));
      if (selectedOptionIds.length === this.data.selectedOptionIds.length) return false;
      question.selectedOptionIds = selectedOptionIds.slice();
      this.applySelection(selectedOptionIds);
      wx.showToast({ title: '选项已更新，请重新确认答案', icon: 'none' });
      return true;
    },

    chooseOption(event) {
      if (this.data.saving || this.submissionPending || !this.isCurrentQuestionEvent(event)) return;
      if (this.clearInvalidSelection()) return;
      const optionId = String(event.currentTarget.dataset.optionId);
      const question = this.assessment.questions[this.data.currentIndex];
      if (!(question.options || []).some((option) => String(option.optionId) === optionId)) return;
      let selectedOptionIds = this.data.selectedOptionIds.slice();
      if (this.data.isMultiple) {
        const selectedIndex = selectedOptionIds.indexOf(optionId);
        if (selectedIndex >= 0) selectedOptionIds.splice(selectedIndex, 1);
        else if (selectedOptionIds.length >= this.data.maxSelectCount) {
          wx.showToast({ title: `最多选择${this.data.maxSelectCount}项`, icon: 'none' });
          return;
        } else selectedOptionIds.push(optionId);
      } else {
        selectedOptionIds = !this.data.required && selectedOptionIds.includes(optionId) ? [] : [optionId];
      }
      this.assessment.questions[this.data.currentIndex].selectedOptionIds = selectedOptionIds.slice();
      this.applySelection(selectedOptionIds);
    },

    applySelection(selectedOptionIds) {
      const selected = new Set(selectedOptionIds);
      const question = this.assessment && this.assessment.questions[this.data.currentIndex];
      if (question) question.saved = false;
      this.setData({
        selectedOptionIds,
        allAnswered: this.allQuestionsAnswered(),
        options: this.data.options.map((option) => Object.assign({}, option, {
          selected: selected.has(option.optionId)
        }))
      });
    },

    allQuestionsAnswered() {
      const questions = this.assessment && this.assessment.questions;
      return Array.isArray(questions) && questions.every((question) => {
        const selected = Array.isArray(question.selectedOptionIds) ? question.selectedOptionIds : [];
        return selected.length > 0 || question.required === false;
      });
    },

    goPrevious(event) {
      if (this.data.saving || this.submissionPending || !this.isCurrentQuestionEvent(event)) return;
      if (this.data.currentIndex === 0) {
        this.triggerEvent('back');
        return;
      }
      this.showQuestion(this.data.currentIndex - 1, 'prev');
    },

    async continueTest(event, forceSubmit) {
      if (this.data.saving || !this.isCurrentQuestionEvent(event)) return;
      if (!this.submissionPending && this.clearInvalidSelection()) return;
      const selectedCount = this.data.selectedOptionIds.length;
      if (!this.submissionPending && (((selectedCount > 0 || this.data.required)
        && selectedCount < this.data.minSelectCount) || selectedCount > this.data.maxSelectCount)) {
        wx.showToast({
          title: this.data.isMultiple
            ? `请选择${this.data.minSelectCount}-${this.data.maxSelectCount}项`
            : '请先选择一个答案',
          icon: 'none'
        });
        return;
      }
      const requestVersion = this.requestVersion;
      const questionToken = this.questionToken;
      const currentIndex = this.data.currentIndex;
      const question = this.assessment.questions[currentIndex];
      const selectedOptionIds = this.data.selectedOptionIds.slice();
      const isLast = this.data.isLast;
      this.setData({ saving: true });
      try {
        if (this.submissionPending) {
          await this.submitAssessment(requestVersion);
          return;
        }
        await authenticatedRequestData({
          url: `/api/miniapp/assessments/${encodeURIComponent(this.answerSessionId)}/answers`,
          method: 'PUT',
          data: { questionId: question.questionId, optionIds: selectedOptionIds }
        });
        question.saved = true;
        if (this.requestVersion !== requestVersion || this.questionToken !== questionToken) return;
        if (isLast || forceSubmit) {
          // 翻回去改过的其他题目可能还没保存，交卷前必须一并提交。
          await this.saveDirtyAnswers();
          if (this.requestVersion !== requestVersion || this.questionToken !== questionToken) return;
          await this.submitAssessment(requestVersion);
          return;
        }
        this.showQuestion(currentIndex + 1, 'next');
      } catch (error) {
        if (this.requestVersion !== requestVersion || this.questionToken !== questionToken) return;
        this.setData({ saving: false });
        wx.showToast({ title: error.message || '答案保存失败', icon: 'none' });
      }
    },

    async saveDirtyAnswers() {
      const questions = this.assessment && this.assessment.questions;
      if (!Array.isArray(questions)) return;
      for (const question of questions) {
        if (question.saved) continue;
        const selectedOptionIds = Array.isArray(question.selectedOptionIds)
          ? question.selectedOptionIds.map(String)
          : [];
        // 必答题没有答案时保持原样，交给交卷接口给出明确错误。
        if (selectedOptionIds.length === 0 && question.required !== false) continue;
        await authenticatedRequestData({
          url: `/api/miniapp/assessments/${encodeURIComponent(this.answerSessionId)}/answers`,
          method: 'PUT',
          data: { questionId: question.questionId, optionIds: selectedOptionIds }
        });
        question.saved = true;
      }
    },

    async submitDirect(event) {
      if (this.data.saving || this.data.submissionPending || !this.data.allAnswered) return;
      // 复用继续逻辑：先保存当前题的修改，再直接交卷。
      await this.continueTest(event, true);
    },

    async submitAssessment(requestVersion) {
      this.submitRequestId = this.submitRequestId || createIdempotencyKey('submit');
      // 超时不代表服务端未提交；重试直接复用提交接口，不能再次保存已结束答卷。
      this.submissionPending = true;
      this.setData({ submissionPending: true });
      let report;
      try {
        report = await authenticatedRequestData({
        url: `/api/miniapp/assessments/${encodeURIComponent(this.answerSessionId)}/submit`,
        method: 'POST',
        data: { submitRequestId: this.submitRequestId }
        });
      } catch (error) {
        if (this.requestVersion === requestVersion
          && error.statusCode >= 400 && error.statusCode < 500 && error.statusCode !== 401) {
          this.submissionPending = false;
          this.setData({ submissionPending: false });
        }
        throw error;
      }
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

    handleQuizTouchStart(event) {
      const touch = event.touches && event.touches[0];
      if (!touch) return;
      this.quizTouchStart = { x: touch.clientX, y: touch.clientY };
    },

    handleQuizTouchEnd(event) {
      const start = this.quizTouchStart;
      this.quizTouchStart = null;
      const touch = event.changedTouches && event.changedTouches[0];
      if (!start || !touch || this.data.state !== 'ready' || this.data.saving || this.submissionPending) return;
      const deltaX = touch.clientX - start.x;
      const deltaY = touch.clientY - start.y;
      if (Math.abs(deltaX) < QUIZ_SWIPE_MIN_DISTANCE || Math.abs(deltaX) <= Math.abs(deltaY)) return;
      const questionEvent = { currentTarget: { dataset: { questionToken: this.questionToken } } };
      if (deltaX < 0) this.continueTest(questionEvent);
      else this.goPrevious(questionEvent);
    },

    showQuestion(currentIndex, direction) {
      const requestVersion = this.requestVersion;
      const questionToken = (this.questionToken || 0) + 1;
      this.questionToken = questionToken;
      const questions = this.assessment.questions;
      const question = questions[currentIndex];
      const selectedOptionIds = Array.isArray(question.selectedOptionIds)
        ? question.selectedOptionIds.map(String)
        : [];
      const selected = new Set(selectedOptionIds);
      const number = currentIndex + 1;
      const isMultiple = Number(question.questionType) === 2;
      const slideClass = direction === 'next' ? 'quiz-panel--next' : direction === 'prev' ? 'quiz-panel--prev' : '';
      this.setData({
        saving: true,
        questionSlideClass: '',
        questionToken,
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
        required: question.required !== false,
        questionTypeText: (isMultiple
          ? `多选题（${question.minSelectCount}-${question.maxSelectCount}项）`
          : '单选题') + (question.required === false ? ' · 可跳过' : ''),
        isLast: currentIndex === questions.length - 1,
        allAnswered: this.allQuestionsAnswered()
      }, () => {
        // 旧题的迟到事件不能写入新题；新题完成渲染后才允许继续操作。
        if (this.requestVersion !== requestVersion || this.questionToken !== questionToken) return;
        this.setData({ saving: false, questionSlideClass: slideClass });
      });
    },

    retry() {
      this.loadAssessment();
    }
  }
});
