'use strict';
const bottomJump=document.createElement('button');bottomJump.id='bottomJump';bottomJump.type='button';bottomJump.hidden=true;bottomJump.setAttribute('aria-label','前往对话底部');bottomJump.title='前往对话底部';bottomJump.innerHTML='<span aria-hidden="true"></span>';document.body.append(bottomJump);
bottomJump.addEventListener('click',()=>window.scrollTo({top:document.documentElement.scrollHeight,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'}));
let bottomJumpFrame=0,bottomComposer=null;
const bottomResize=new ResizeObserver(()=>scheduleBottomJump());
function scheduleBottomJump(){if(!bottomJumpFrame)bottomJumpFrame=requestAnimationFrame(()=>{bottomJumpFrame=0;const composer=document.querySelector('.composer');if(composer!==bottomComposer){bottomResize.disconnect();bottomComposer=composer;if(composer)bottomResize.observe(composer)}const enabled=typeof page!=='undefined'&&page==='ai'&&document.querySelector('#messages .message.assistant');bottomJump.hidden=!enabled||document.documentElement.scrollHeight-innerHeight-scrollY<80;const top=composer?.getBoundingClientRect().top??innerHeight;bottomJump.style.bottom=Math.max(12,innerHeight-Math.max(0,top)+12)+'px';});}
window.addEventListener('scroll',scheduleBottomJump,{passive:true});window.addEventListener('resize',scheduleBottomJump);window.visualViewport?.addEventListener('resize',scheduleBottomJump);
new MutationObserver(scheduleBottomJump).observe(document.querySelector('#app'),{childList:true,subtree:true});
