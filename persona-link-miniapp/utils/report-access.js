const { authenticatedRequestData } = require('./request');

// 单人、双人共用权限入口；前端观看回调不是可信的服务端奖励凭证。
function createReportAccess(page, reportUrl) {
  let disposed = false;
  let busy = false;
  let pending = null;
  let hidden = false;
  let clearAd = () => {};
  let cancelAd = () => {};

  function play(adUnitId) {
    return new Promise((resolve) => {
      let settled = false;
      let ad;
      const finish = (result) => {
        if (settled) return;
        settled = true;
        try { clearAd(); } finally { resolve(result); }
      };
      const onLoad = () => {};
      const onError = (error) => {
        const errorCode = Number(error && error.errCode);
        finish(Number.isInteger(errorCode) && errorCode >= 1000 && errorCode <= 1009
          ? { outcome: 2, errorCode } : { unavailable: true });
      };
      const onClose = (result) => finish(result && result.isEnded === true ? { outcome: 1 } : null);
      cancelAd = () => finish(null);
      try {
        if (!wx.createRewardedVideoAd) {
          onError({ errCode: 'UNSUPPORTED' });
          return;
        }
        ad = wx.createRewardedVideoAd({ adUnitId });
        clearAd = () => {
          ad.offLoad(onLoad);
          ad.offError(onError);
          ad.offClose(onClose);
          clearAd = () => {};
        };
        ad.onLoad(onLoad);
        ad.onError(onError);
        ad.onClose(onClose);
        Promise.resolve(ad.load()).then(() => {
          if (hidden || disposed) { finish(null); return; }
          if (!settled) return ad.show();
        }).catch(onError);
      } catch (error) {
        onError(error);
      }
    });
  }

  return {
    async run(loadContent, watch = false) {
      if (disposed || busy || hidden) return;
      busy = true;
      page.setData({ state: 'loading', result: null });
      try {
        let access = await authenticatedRequestData({ url: `${reportUrl}/access` });
        if (disposed) return;
        if (access.status === 2 && (pending || (watch && !hidden))) {
          if (!pending) {
            const outcome = await play(access.adUnitId);
            if (disposed) return;
            if (!outcome) {
              page.setData({ state: 'locked', adDescription: '完整观看视频后可查看结果，答题记录已保留。', adAction: '看广告解锁结果' });
              return;
            }
            if (outcome.unavailable) throw new Error('当前无法播放广告，请更新微信或稍后重试');
            pending = Object.assign({ taskId: access.taskId }, outcome);
          }
          access = await authenticatedRequestData({ url: `${reportUrl}/ad-result`, method: 'POST', data: pending });
          pending = null;
          if (disposed) return;
        }
        if (access.status === 2) {
          page.setData({ state: 'locked', adDescription: watch ? '广告暂不可用，请稍后重试，答题记录已保留。' : '观看一段视频即可解锁本次结果，解锁后无须重复观看。', adAction: '看广告解锁结果' });
          return;
        }
        if (access.status !== 1) throw new Error('报告访问状态异常，请稍后重试');
        pending = null;
        await loadContent(() => !disposed);
      } catch (error) {
        if (disposed) return;
        if (error.code === 40901) pending = null;
        page.setData({ state: 'error', errorDescription: pending ? (pending.outcome === 1 ? '广告结果提交未成功，请点击重新加载重试，无须重复观看。' : '广告异常处理未成功，请点击重新加载重试。') : (error.message || '报告加载失败') });
      } finally {
        busy = false;
      }
    },
    setHidden(value) { hidden = value; },
    dispose() {
      disposed = true;
      cancelAd();
      clearAd();
    }
  };
}

module.exports = { createReportAccess };
