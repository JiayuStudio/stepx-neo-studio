/* Compiles only the selected local snapshot; no Studio login or native bridge. */
(async()=>{
 const [p,vendor]=await Promise.all([fetch('/project.json').then(r=>r.json()),fetch('/local-vendor/manifest.json').then(r=>r.json())]);
 const text=p.files, binaries=p.binaryFiles||{}, output={}, enc=s=>({encoding:'utf8',content:s});
 for(const [n,s]of Object.entries(text))output[n]=enc(s);
 for(const [n,s]of Object.entries(binaries))output[n]={encoding:'base64',content:s};
 if(!text['index.html'])throw Error('缺少 index.html。当前本机运行支持 HTML / React / TSX 前端工程。');
 await esbuild.initialize({wasmURL:'/local-vendor/esbuild.wasm',worker:false});
 let pkg={};if(text['package.json'])pkg=JSON.parse(text['package.json']);
 const used=new Set(), warnings=new Set();
 const clean=n=>{let out=[];for(const part of n.split('/')){if(!part||part==='.')continue;if(part==='..'){if(!out.length)throw Error('路径越出项目：'+n);out.pop();}else out.push(part);}return out.join('/')};
 const dir=n=>n.slice(0,n.lastIndexOf('/')+1);
 const exists=n=>Object.hasOwn(text,n)||Object.hasOwn(binaries,n);
 const resolve=n=>{for(const x of [n,n+'.ts',n+'.tsx',n+'.js',n+'.jsx',n+'.json',n+'/index.ts',n+'/index.tsx',n+'/index.js',n+'/index.jsx'])if(exists(x))return x;throw Error('找不到本地文件：'+n)};
 const aliases={'@/':'src/'};
 if(text['tsconfig.json']){try{const t=JSON.parse(text['tsconfig.json'].replace(/\/\*[\s\S]*?\*\/|^\s*\/\/.*$/gm,'').replace(/,\s*([}\]])/g,'$1'));for(const [k,v]of Object.entries(t.compilerOptions?.paths||{}))if(k.endsWith('/*')&&v[0]?.endsWith('/*'))aliases[k.slice(0,-1)]=clean((t.compilerOptions.baseUrl||'.')+'/'+v[0].slice(0,-1))+'/';}catch(e){warnings.add('tsconfig 使用默认 @/ → src/');}}
 const local=(n,from='')=>{for(const [key,val]of Object.entries(aliases))if(n.startsWith(key))return resolve(clean(val+n.slice(key.length)));return resolve(clean(n.startsWith('/')?n:dir(from)+n))};
 let tw;
 async function css(n,stack=[]){if(stack.includes(n))throw Error('CSS 循环导入：'+n);let s=text[n];
  const re=/@import\s+(?:url\(\s*)?['"]([^'"]+)['"]\s*\)?\s*;/g;let out='',last=0;
  for(const m of s.matchAll(re)){out+=s.slice(last,m.index);if(/^(https?:|\/\/)/.test(m[1]))out+=m[0];else out+=await css(local(m[1],n),[...stack,n]);last=m.index+m[0].length;}s=out+s.slice(last);
  // Resolve CSS resource URLs before inlining so nested resources retain their base directory.
  s=s.replace(/url\(\s*(['"]?)([^)'"\s]+)\1\s*\)/g,(all,q,url)=>/^(data:|https?:|\/\/|#)/.test(url)?all:'url("/'+clean(url.startsWith('/')?url:dir(n)+url)+'")');
  return s;
 }
 async function styles(n){let s=await css(n);if(/@(?:tailwind|apply|layer)\b/.test(s)){
   if(!tw){let config={};const src=text['tailwind.config.js']||text['tailwind.config.ts'];if(src){const t=await esbuild.transform(src,{loader:'ts',format:'cjs'});const module={exports:{}};new Function('module','exports','require',t.code)(module,module.exports,n=>{throw Error('本机尚不支持 Tailwind 配置插件：'+n)});config=module.exports.default||module.exports;}tw=NeoTools.createTailwindcss({tailwindConfig:config});}
   s=await tw.generateStylesFromContent(s,Object.entries(text).map(([n,content])=>({content,extension:n.split('.').pop()})));
  }return s;
 }
 const plugin={name:'neo-local-files',setup(b){b.onResolve({filter:/.*/},a=>{
   let n=a.path;
   if(vendor[n]){const v=vendor[n],wanted=pkg.dependencies?.[v.package];if(wanted&&!NeoTools.satisfies(v.version,wanted))throw Error('依赖版本不兼容：'+v.package+' 要求 '+wanted+'，本机内置 '+v.version);used.add(v.package+' '+v.version);if(v.file.endsWith('.css'))return {path:v.file,namespace:'vendor-css'};return {path:'/__vendor/'+v.file,external:true};}
   if(n.startsWith('.')||n.startsWith('/')||Object.keys(aliases).some(k=>n.startsWith(k)))return {path:local(n,a.importer),namespace:'project'};
   throw Error('本机尚未内置依赖：'+n+'。请使用云端运行或改用已支持依赖。');
  });b.onLoad({filter:/.*/,namespace:'vendor-css'},async a=>({contents:await fetch('/local-vendor/'+a.path).then(r=>r.text()),loader:'css'}));b.onLoad({filter:/.*/,namespace:'project'},async a=>{
   const ext=a.path.split('.').pop().toLowerCase();
   if(ext==='css')return {contents:await styles(a.path),loader:'css'};
   if(['png','jpg','jpeg','webp','gif','ico','woff','woff2','ttf','mp3','mp4','svg'].includes(ext)){const bytes=binaries[a.path]?Uint8Array.from(atob(binaries[a.path]),c=>c.charCodeAt(0)):new TextEncoder().encode(text[a.path]);return {contents:bytes,loader:'dataurl'};}
   return {contents:text[a.path],loader:({tsx:'tsx',ts:'ts',jsx:'jsx',js:'jsx',mjs:'js',json:'json'})[ext]||'text'};
  });}};
 const doc=new DOMParser().parseFromString(text['index.html'],'text/html');
 let count=0;
 for(const script of [...doc.querySelectorAll('script')]){
  const src=script.getAttribute('src'),mod=script.type==='module';if(!src&&!mod)continue;
  if(src&&/^(https?:|\/\/|data:)/.test(src)){warnings.add('外部资源需手动开启联网');continue;}
  if(!mod&&src&&!/\.(?:ts|tsx|jsx)$/.test(src))continue;
  const key='__neo_run_'+count++,opts={bundle:true,write:false,format:mod?'esm':'iife',platform:'browser',target:'chrome110',jsx:'automatic',outfile:'/'+key+'.js',plugins:[plugin],define:{'process.env.NODE_ENV':'"production"','import.meta.env':JSON.stringify({MODE:'production',PROD:true,DEV:false,BASE_URL:'/'})},logLevel:'silent'};
  if(src)opts.entryPoints=['/'+local(src)];else opts.stdin={contents:script.textContent,resolveDir:'/',sourcefile:'inline.tsx',loader:'tsx'};
  const result=await esbuild.build(opts);
  for(const f of result.outputFiles){output[f.path.slice(1)]=enc(f.text);if(f.path.endsWith('.css')){const link=doc.createElement('link');link.rel='stylesheet';link.href=f.path;doc.head.appendChild(link);}}
  script.removeAttribute('integrity');script.textContent='';script.src='/'+key+'.js';
 }
 for(const link of [...doc.querySelectorAll('link[rel=stylesheet]')]){const href=link.getAttribute('href');if(href.startsWith('/__neo_run_'))continue;if(/^(https?:|\/\/)/.test(href)){warnings.add('外部样式需手动开启联网');continue;}const n=local(href);const dest='__neo_style_'+count+++'.css';output[dest]=enc(await styles(n));link.href='/'+dest;}
 if(text['vite.config.ts']||text['vite.config.js'])warnings.add('未执行 Vite 插件；原生桥接与后端需独立验证');
 doc.querySelectorAll('meta[http-equiv="Content-Security-Policy"]').forEach(n=>n.remove());
 output['index.html']=enc('<!doctype html>\n'+doc.documentElement.outerHTML);
 window.__neoBuild={files:output,note:[count?'JS/TSX 已编译':'静态网页',...warnings].join(' · '),libraries:[...used]};
})().catch(e=>{window.__neoBuild={error:e.errors?e.errors.map(x=>(x.location?.file||'')+':'+(x.location?.line||'')+' '+x.text).join('\n'):String(e.message||e)}});
