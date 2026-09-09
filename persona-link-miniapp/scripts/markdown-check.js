const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { renderMarkdown } = require('../subpackages/account/utils/markdown');

// 无 window、document、Node require 的环境也可装载浏览器 UMD。
const exportsModule = { exports: {} };
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../subpackages/account/vendor/markdown-it.min.js'), 'utf8'), {
  module: exportsModule, exports: exportsModule.exports
});
assert.equal(typeof exportsModule.exports, 'function');
assert.ok(new exportsModule.exports().render('**粗体**').includes('<strong>粗体</strong>'));
const result = renderMarkdown('# 用户协议\n\n## 条款\n\n**粗体**和*斜体*及~~删除~~\n\n- 条目\n  - 子条目\n\n1. 第一条\n2. 第二条\n\n> 引用\n\n---\n\n`内联代码`\n\n```js\n<script>代码</script>\n```\n\n|项目|说明|\n|---|---|\n|记录|答题|\n\n[网站](https://example.com/privacy)\n\n![图片](https://example.com/image.png)');
for (const tag of ['h1', 'h2', 'strong', 'em', 's', 'ul', 'ol', 'li', 'blockquote', 'hr', 'code', 'pre', 'table', 'th', 'td', 'img']) {
  assert.match(result, new RegExp(`<${tag}(?:>| )`), tag);
}
assert.ok(result.includes('https://example.com/privacy'));
assert.ok(result.includes('max-width:100%'));
assert.ok(result.includes('table-layout:fixed'));
assert.ok(result.includes('white-space:pre-wrap'));
const unsafe = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>\n\n[坏链接](javascript:alert(1))\n\n[编码](jav&#x61;script:alert(1))\n\n![危险图](data:text/html,hello)');
assert.ok(!unsafe.includes('<script'));
assert.ok(!unsafe.includes('<img'));
assert.ok(!unsafe.includes('href='));
assert.ok(unsafe.includes('&lt;script&gt;'));
assert.ok(renderMarkdown('正文'.repeat(15000) + '\n\n**末尾标记**').includes('<strong>末尾标记</strong>'));
assert.equal(renderMarkdown(null), '');
console.log('MARKDOWN_CHECK_OK syntax/security/long-text/no-DOM');
