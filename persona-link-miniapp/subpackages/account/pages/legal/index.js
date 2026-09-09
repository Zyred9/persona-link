const { requestData } = require('../../../../utils/request');
const { renderMarkdown } = require('../../utils/markdown');
const TYPES = { userAgreement: 1, privacyGuide: 2, disclaimer: 3 };

Page({
  data: { document: { title: '协议内容' }, contentNodes: '', state: 'loading', error: '' },
  onLoad(options) {
    this.type = TYPES[options.type] || 1;
    this.loadDocument();
  },
  onUnload() { this.disposed = true; },
  async loadDocument() {
    if (this.loading) return;
    this.loading = true;
    this.setData({ state: 'loading', error: '' });
    try {
      const document = await requestData({ url: `/api/miniapp/legal-documents/${this.type}` });
      if (!this.disposed) this.setData({ document, contentNodes: renderMarkdown(document.content), state: 'ready' });
    } catch (error) {
      if (!this.disposed) this.setData({ state: 'error', error: error.message || '协议加载失败，请重试' });
    } finally { this.loading = false; }
  }
});
