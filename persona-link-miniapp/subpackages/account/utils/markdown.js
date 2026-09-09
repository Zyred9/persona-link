const MarkdownIt = require('../vendor/markdown-it.min');

// 原始 HTML 只按文字展示；链接继续使用解析器默认的危险协议校验。
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false });
const styles = {
  paragraph_open: 'margin:0 0 14px;line-height:1.8;word-break:break-word;',
  heading_open: 'margin:22px 0 12px;line-height:1.5;font-weight:bold;word-break:break-word;',
  bullet_list_open: 'margin:10px 0;padding-left:24px;list-style-type:disc;',
  ordered_list_open: 'margin:10px 0;padding-left:24px;list-style-type:decimal;',
  list_item_open: 'margin:6px 0;line-height:1.8;',
  blockquote_open: 'margin:14px 0;padding:10px 12px;border-left:4px solid #c4a5ff;background:#f7f2ff;',
  table_open: 'width:100%;table-layout:fixed;border-collapse:collapse;margin:14px 0;',
  th_open: 'border:1px solid #d8d0de;padding:8px;background:#f7f2ff;word-break:break-all;',
  td_open: 'border:1px solid #d8d0de;padding:8px;word-break:break-all;',
  hr: 'border:0;border-top:1px solid #d8d0de;margin:20px 0;'
};
Object.keys(styles).forEach((type) => {
  markdown.renderer.rules[type] = (tokens, index, options, env, renderer) => {
    tokens[index].attrJoin('style', styles[type]);
    return renderer.renderToken(tokens, index, options);
  };
});
const imageRule = markdown.renderer.rules.image;
markdown.renderer.rules.image = (tokens, index, options, env, renderer) => {
  tokens[index].attrJoin('style', 'max-width:100%;height:auto;display:block;margin:12px 0;');
  return imageRule(tokens, index, options, env, renderer);
};
const codeStyle = 'white-space:pre-wrap;word-break:break-all;font-family:monospace;';
markdown.renderer.rules.code_inline = (tokens, index) =>
  `<code style="${codeStyle}background:#f1edf4;padding:2px 4px;">${markdown.utils.escapeHtml(tokens[index].content)}</code>`;
const renderCode = (tokens, index) =>
  `<pre style="${codeStyle}margin:14px 0;padding:12px;background:#f1edf4;"><code>${markdown.utils.escapeHtml(tokens[index].content)}</code></pre>`;
markdown.renderer.rules.fence = renderCode;
markdown.renderer.rules.code_block = renderCode;

// rich-text 不提供外链跳转：正文展示链接标题及完整 URL，便于选择复制。
markdown.renderer.rules.link_open = () => '<span style="color:#7351aa;word-break:break-all;">';
markdown.renderer.rules.link_close = (tokens, index) => {
  for (let start = index - 1; start >= 0; start -= 1) {
    if (tokens[start].type === 'link_open') {
      const url = markdown.utils.escapeHtml(tokens[start].attrGet('href') || '');
      return `（${url}）</span>`;
    }
  }
  return '</span>';
};

module.exports = { renderMarkdown: (content) => markdown.render(typeof content === 'string' ? content : '') };
