// Public, rendered Studio DOM only. No cookies, framework state or private API.
function studioLink(op) {
 const normalize=s=>s.replace(/\r/g,'').replace(/\n{2,}/g,'\n\n').trim();
 const visible=e=>!!e&&e.getBoundingClientRect().width>0&&e.getBoundingClientRect().height>0&&getComputedStyle(e).visibility!=='hidden'&&!e.closest('[inert],[aria-hidden="true"]');
 if(location.href!==op.url)return {error:'Studio项目已切换，联动停止'};
 const section=document.querySelector('section[aria-label="开发对话"]');
 if(!visible(section)){const expand=document.querySelector('button[aria-label="展开对话"]');if(expand){expand.click();return {retry:true}}return {error:'找不到Studio开发对话，请检查登录和网页版本'};}
 const list=section.querySelector('[aria-label="消息列表"]');
 const editor=section.querySelector('[contenteditable="true"][aria-label="消息输入"]');
 if(!list||!visible(editor))return {error:'Studio消息列表或输入框不可用'};
 const users=[...list.querySelectorAll('[aria-label="用户消息"]')];
 const turns=[...list.querySelectorAll('[data-slot="turn-summary"]')];
 const last=turns.at(-1), li=last?.closest('li');
 let reply='';if(li){const c=li.cloneNode(true);c.querySelectorAll('button,time,[data-slot="turn-actions"],[data-slot="chat-turn"]').forEach(e=>e.remove());reply=c.textContent.trim().slice(-32000);}
 const busy=[...section.querySelectorAll('button[aria-label]')].some(e=>visible(e)&&/停止|取消生成/.test(e.getAttribute('aria-label')));
 const snapshot={url:location.href,users:users.length,turns:turns.length,lastUser:users.at(-1)?.textContent||'',summary:last?.innerText||'',reply,draft:editor.innerText.trim(),busy};
 if(op.action==='snapshot')return snapshot;
 if(busy)return {error:'Studio仍在生成，请等待后再开始'};
 if(op.action==='fill'){
  if(snapshot.draft)return {error:'Studio已有未发送草稿，已保留，请处理后重试'};
  editor.focus();let sel=getSelection(),range=document.createRange();range.selectNodeContents(editor);sel.removeAllRanges();sel.addRange(range);
  document.execCommand('insertText',false,op.text);editor.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:null}));
  return {filled:normalize(editor.innerText)===normalize(op.text),...snapshot};
 }
 if(op.action==='submit'){
  if(normalize(editor.innerText)!==normalize(op.text))return {error:'交接草稿已变化，为避免误发已停止'};
  const buttons=[...section.querySelectorAll('button[aria-label="发送"]')].filter(visible);
  if(buttons.length!==1||buttons[0].disabled)return {error:'Studio发送按钮不可用；草稿已保留'};
  buttons[0].click();return {clicked:true};
 }
 return {error:'未知操作'};
}
