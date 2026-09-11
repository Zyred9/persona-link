Page({
  loadMore() {
    const account = this.selectComponent('#history-account-center');
    if (account) account.loadMoreRecords();
  }
});
