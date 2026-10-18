import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const c=vm.createContext({});vm.runInContext(fs.readFileSync('assets/usage.js','utf8'),c);
const rows=[{provider:'DeepSeek',usage:{known:true,input:80,output:20,total:100,cacheRead:50}},{source:'external_api',model:'step-any-model-via-proxy',usage:{known:true,input:30,output:10,total:40}},{source:'step_api',usage:{known:true,input:10,output:20,total:30}},{source:'studio',usage:{known:false}},{source:'external_api',usage:{known:false}}];
const by=k=>rows.filter(r=>c.usageSource(r)===k);
assert.equal(c.usageTotals(by('external_api')).total,140);assert.equal(c.usageTotals(by('external_api')).unknown,1);assert.equal(c.usageTotals(by('external_api')).cacheRead,50);assert.equal(c.usageTotals(by('step_api')).total,30);assert.equal(c.usageTotals(by('studio')).unknown,1);assert.equal(c.usageSource(rows[1]),'external_api');
console.log('PASS: legacy API, proxy model name, Step API, Studio unknown and cache subset remain separate');
