import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const c=vm.createContext({document:{querySelector:()=>({addEventListener(){}})},MutationObserver:class{observe(){}}});vm.runInContext(fs.readFileSync('assets/live-edit.js','utf8'),c);
assert.match(c.liveFilePreview('{"files":{"index.html":"<h1>updated</h1>","app.js":"let n=1;\\n',{'index.html':'old'}),/修改 · index.html[\s\S]*新增 · app.js[\s\S]*let n=1;/);
assert.match(c.liveFilePreview('{"files":{"style.css":null}}',{}),/删除 · style.css/);
assert.equal(c.liveFilePreview('ordinary response',{}),'ordinary response');
console.log('PASS incremental JSON code preview, changed/new/deleted file labels, no execution');
