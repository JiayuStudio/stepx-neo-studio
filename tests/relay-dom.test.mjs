import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const code=fs.readFileSync('assets/studio-link.js','utf8');
function fixture(){let clicks=0,expands=0;const url='https://studio.stepx.com/builder/app/p_test';const element=x=>Object.assign({getBoundingClientRect:()=>({width:20,height:20}),closest:()=>null},x);
 const editor=element({innerText:'',focus(){},dispatchEvent(){}}),send=element({disabled:false,click(){clicks++;editor.innerText=''},getAttribute:()=> '发送'}),stop=element({getAttribute:()=> '停止生成'});
 const list={querySelectorAll:()=>[]};let isBusy=false,isVisible=true,senders=[send];
 const section=element({getBoundingClientRect:()=>({width:isVisible?20:0,height:20}),querySelector:s=>s.includes('消息列表')?list:editor,querySelectorAll:s=>s==='button[aria-label]'?(isBusy?[stop]:[send]):senders});
 const document={querySelector:s=>s.startsWith('section')?section:{click(){expands++;}},createRange:()=>({selectNodeContents(){}}),execCommand:(cmd,_a,text)=>{editor.innerText=text.replace(/\n\n/g,'\n\n\n');return true;}};
 const ctx=vm.createContext({document,location:{href:url},getComputedStyle:()=>({visibility:'visible'}),getSelection:()=>({removeAllRanges(){},addRange(){}}),InputEvent:class{},console});vm.runInContext(code,ctx);
 return {editor,send,url,run:o=>ctx.studioLink({url,action:'snapshot',...o}),clicks:()=>clicks,expands:()=>expands,busy:x=>isBusy=x,hidden:()=>isVisible=false,ambiguous:()=>senders=[send,send]};}
let f=fixture();assert.match(f.run({url:'https://studio.stepx.com/builder/app/other'}).error,/切换/);assert.equal(f.clicks(),0);
f=fixture();f.editor.innerText='human draft';assert.match(f.run({action:'fill',text:'new'}).error,/已有/);assert.equal(f.editor.innerText,'human draft');
f=fixture();f.busy(true);assert.match(f.run({action:'fill',text:'new'}).error,/生成/);
f=fixture();f.hidden();assert.equal(f.run({action:'fill',text:'new'}).retry,true);assert.equal(f.expands(),1);
f=fixture();const text='line one\n\nline two';assert.equal(f.run({action:'fill',text}).filled,true);assert.equal(f.run({action:'submit',text}).clicked,true);assert.equal(f.clicks(),1);assert.match(f.run({action:'submit',text}).error,/变化/);assert.equal(f.clicks(),1);
f=fixture();f.editor.innerText='changed';assert.match(f.run({action:'submit',text:'expected'}).error,/变化/);
f=fixture();f.editor.innerText='exact';f.send.disabled=true;assert.match(f.run({action:'submit',text:'exact'}).error,/不可用/);assert.equal(f.clicks(),0);
f=fixture();f.editor.innerText='exact';f.ambiguous();assert.match(f.run({action:'submit',text:'exact'}).error,/不可用/);assert.equal(f.clicks(),0);
console.log('PASS: 8 DOM guard scenarios, including blank-line normalization and duplicate-send refusal');
