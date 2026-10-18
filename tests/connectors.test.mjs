import assert from 'node:assert/strict';import fs from 'node:fs';
const d=JSON.parse(fs.readFileSync(new URL('../assets/official-connectors.json',import.meta.url)));
assert.equal(d.scope,'all');assert.equal(d.items.length,39);assert.equal(new Set(d.items.map(c=>c.id)).size,39);assert.equal(d.items.reduce((n,c)=>n+c.capabilities.length,0),358);
for(const c of d.items){assert.match(c.id,/^[a-zA-Z0-9_-]+$/);assert.equal(c.url,'https://studio.stepx.com/connectors/'+c.id);assert.ok(c.builderTypes.length);for(const k of ['connection','icon','creatorUserId','developer'])assert.ok(!(k in c));for(const t of c.capabilities){assert.ok(t.name);assert.ok(t.description)}}
assert.equal(d.items.filter(c=>c.builderTypes.includes('app')).length,34);assert.equal(d.items.find(c=>c.id==='ic_atomic_990012').capabilities.length,2);
console.log('PASS official catalog: 39 connectors, 358 capabilities, 34 app-compatible; no account connection fields');
