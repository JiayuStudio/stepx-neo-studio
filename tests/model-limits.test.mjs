import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const c=vm.createContext({URL});vm.runInContext(fs.readFileSync('assets/model-limits.js','utf8'),c);
for(const model of ['deepseek-flash','deepseek-v4-flash','deepseek-v4-pro','deepseek-v4-flash-vision-exp']){const l=c.modelLimits({endpoint:'https://api.deepseek.com/v1',model});assert.equal(l.context,1048576);assert.equal(l.output,393216);assert.ok(l.chars>1048576*6)}
for(const endpoint of ['https://api.deepseek.com.evil.invalid','https://proxy.example','http://api.deepseek.com'])assert.equal(c.modelLimits({endpoint,model:'deepseek-flash'}).official,false);
assert.equal(c.modelLimits({endpoint:'https://api.deepseek.com',model:'unknown-model'}).official,false);
console.log('PASS official DeepSeek aliases, exact HTTPS host, context/output distinction, long input allowance');
