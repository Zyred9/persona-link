const {
  requestData,
  authenticatedRequestData,
  createIdempotencyKey
} = require('../../utils/request');
const { trackEvent } = require('../../utils/analytics');
const { resolveImageUrl } = require('../../utils/image');

const DETAIL_SWIPE_MIN_DISTANCE = 60;

Component({
  options: {
    styleIsolation: 'apply-shared'
  },

  properties: {
    testId: {
      type: String,
      value: '',
      observer(testId) {
        this.loadTest(testId);
      }
    },
    testType: {
      type: Number,
      value: 1
    }
  },

  data: {
    state: 'loading',
    test: null,
    starting: false,
    errorDescription: '暂时无法获取题型详情，请稍后重试。'
  },

  lifetimes: {
    detached() {
      this.requestVersion = (this.requestVersion || 0) + 1;
    }
  },

  methods: {
    deactivate() {
      this.requestVersion = (this.requestVersion || 0) + 1;
      this.createRequestId = null;
      this.setData({ starting: false });
    },

    async loadTest(testId) {
      const normalizedTestId = String(testId || '');
      const requestVersion = (this.requestVersion || 0) + 1;
      this.requestVersion = requestVersion;
      if (this.currentTestId !== normalizedTestId) {
        this.currentTestId = normalizedTestId;
        this.createRequestId = null;
      }
      if (!normalizedTestId) {
        this.setData({
          state: 'error',
          test: null,
          starting: false,
          errorDescription: '题型参数缺失，请返回首页重新选择。'
        });
        return;
      }

      this.setData({ state: 'loading', test: null, starting: false });
      try {
        const test = await requestData({
          url: `/api/miniapp/tests/${encodeURIComponent(normalizedTestId)}`
        });
        if (this.requestVersion !== requestVersion || this.data.testId !== normalizedTestId) {
          return;
        }
        const expectedTestType = Number(this.data.testType) === 2 ? 2 : 1;
        if (!test || Number(test.testType) !== expectedTestType) {
          throw new Error(expectedTestType === 2 ? '当前题型不是双人测试' : '当前题型不是单人测试');
        }
        this.setData({ state: 'ready', test: Object.assign({}, test, {
          imageUrl: resolveImageUrl(test.detailImageUrl)
            || `/assets/images/${expectedTestType === 2 ? 'pair' : 'single'}-detail-hero.png`
        }) });
      } catch (error) {
        if (this.requestVersion !== requestVersion || this.data.testId !== normalizedTestId) {
          return;
        }
        this.setData({
          state: 'error',
          errorDescription: error.message || '题型详情加载失败'
        });
      }
    },

    async startAssessment() {
      if (this.data.starting || !this.data.test) {
        return;
      }
      const isPair = Number(this.data.testType) === 2;
      const testId = this.data.testId;
      const requestVersion = this.requestVersion;
      this.setData({ starting: true });
      this.createRequestId = this.createRequestId || createIdempotencyKey('assessment');
      const createRequestId = this.createRequestId;
      try {
        const assessment = await authenticatedRequestData({
          url: '/api/miniapp/assessments',
          method: 'POST',
          data: {
            testId,
            createRequestId
          }
        });
        if (this.requestVersion !== requestVersion || this.data.testId !== testId) {
          return;
        }
        if (!assessment || !assessment.answerSessionId) {
          throw new Error('答卷创建失败');
        }
        trackEvent(
          3,
          isPair ? '/subpackages/pair/pages/detail/index' : '/subpackages/test/pages/detail/index',
          testId
        );
        const flow = isPair ? '&flow=pair-initiator' : '';
        wx.navigateTo({
          url: `/subpackages/test/pages/quiz/index?answerSessionId=${encodeURIComponent(assessment.answerSessionId)}${flow}`,
          success: () => {
            if (this.createRequestId === createRequestId) {
              this.createRequestId = null;
            }
          },
          complete: () => {
            if (this.requestVersion === requestVersion && this.data.testId === testId) {
              this.setData({ starting: false });
            }
          }
        });
      } catch (error) {
        if (this.requestVersion !== requestVersion || this.data.testId !== testId) {
          return;
        }
        this.setData({ starting: false });
        wx.showToast({
          title: error.message || '暂时无法开始测试',
          icon: 'none'
        });
      }
    },

    retry() {
      this.loadTest(this.data.testId);
    },

    handleSwipeStart(event) {
      const touch = event.touches && event.touches[0];
      if (!touch) return;
      this.swipeStart = { x: touch.clientX, y: touch.clientY };
    },

    handleSwipeEnd(event) {
      const start = this.swipeStart;
      this.swipeStart = null;
      const touch = event.changedTouches && event.changedTouches[0];
      if (!start || !touch) return;
      const deltaX = touch.clientX - start.x;
      const deltaY = touch.clientY - start.y;
      // 从左向右滑动且横向位移占优时返回，避免和纵向滚动冲突。
      if (deltaX < DETAIL_SWIPE_MIN_DISTANCE || Math.abs(deltaX) <= Math.abs(deltaY)) return;
      this.triggerEvent('back');
    },

    joinPair() {
      wx.navigateTo({ url: '/subpackages/pair/pages/join/index' });
    }
  }
});
