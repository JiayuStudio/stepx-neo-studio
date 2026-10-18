import * as esbuild from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';const require=createRequire(import.meta.url);
const out='../../assets/local-vendor';fs.mkdirSync(out,{recursive:true});
const modules=['react','react/jsx-runtime','react-dom','react-dom/client','react-router-dom','lucide-react','clsx','tailwind-merge','@supabase/supabase-js','xgplayer'];
const names=Object.fromEntries(modules.map((m,i)=>[m,`m${i}.js`]));
const external=m=>({name:'shared-react',setup(b){b.onResolve({filter:/^(react|react-dom)(\/.*)?$/},a=>(a.kind!=='entry-point'&&!(a.importer==='<stdin>'&&a.path===m))?{path:'/__vendor/'+names[a.path],external:true}:null)}});
const metadata={};
for(const m of modules){
 const root=m.startsWith('@')?m.split('/').slice(0,2).join('/'):m.split('/')[0];
 const info=JSON.parse(fs.readFileSync('node_modules/'+root+'/package.json'));
 const entry=/^(react|react-dom)(\/|$)/.test(m)?{stdin:{contents:'export {'+Object.keys(require(m)).filter(k=>k!=='default'&&k!=='__esModule').join(',')+'} from '+JSON.stringify(m)+'; import def from '+JSON.stringify(m)+'; export default def;',resolveDir:process.cwd()}}:{entryPoints:[m]};
 const banner=m==='react'?undefined:{js:'import * as __neoReact from "/__vendor/m0.js";'+(m==='react-dom'?'':'import * as __neoReactDOM from "/__vendor/m2.js";')+'var require=(n)=>{if(n==="/__vendor/m0.js")return __neoReact.default;'+(m==='react-dom'?'':'if(n==="/__vendor/m2.js")return __neoReactDOM.default;')+'throw Error("Unsupported runtime dependency: "+n)};'};
 await esbuild.build({...entry,banner,outfile:out+'/'+names[m],bundle:true,format:'esm',platform:'browser',minify:true,define:{'process.env.NODE_ENV':'"production"'},plugins:[external(m)],metafile:true});
 metadata[m]={file:names[m],version:info.version,package:root};
}
fs.copyFileSync('node_modules/xgplayer/dist/index.min.css',out+'/xgplayer.css');metadata['xgplayer/dist/index.min.css']={file:'xgplayer.css',version:'3.0.26',package:'xgplayer'};
const compilerResult=await esbuild.build({metafile:true,stdin:{contents:'export {createTailwindcss} from "@mhsdesign/jit-browser-tailwindcss";export {default as satisfies} from "semver/functions/satisfies.js";',resolveDir:process.cwd()},outfile:out+'/compiler-tools.js',bundle:true,format:'iife',globalName:'NeoTools',platform:'browser',minify:true});
fs.writeFileSync(out+'/compiler-inputs.json',JSON.stringify(Object.keys(compilerResult.metafile.inputs),null,2));
fs.copyFileSync('node_modules/esbuild-wasm/lib/browser.min.js',out+'/esbuild.js');fs.copyFileSync('node_modules/esbuild-wasm/esbuild.wasm',out+'/esbuild.wasm');fs.writeFileSync(out+'/manifest.json',JSON.stringify(metadata));
// Retain package license texts alongside all redistributed compiler/runtime libraries.
let licenses='Bundled local compiler and runtime dependencies\n\n';
function scan(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){if(e.name.startsWith('.'))continue;const p=path.join(dir,e.name);if(!e.isDirectory())continue;if(e.name.startsWith('@')){scan(p);continue;}const pkg=path.join(p,'package.json');if(!fs.existsSync(pkg))continue;const j=JSON.parse(fs.readFileSync(pkg));licenses+=`\n===== ${j.name} ${j.version} (${j.license||'see upstream'}) =====\n`;for(const f of fs.readdirSync(p).filter(n=>/^(license|licence|copying|notice)(\.|$)/i.test(n))){if(fs.statSync(path.join(p,f)).isFile())licenses+=fs.readFileSync(path.join(p,f),'utf8')+'\n';}if(fs.existsSync(p+'/node_modules'))scan(p+'/node_modules');}}
scan('node_modules');licenses+='\n@mhsdesign/jit-browser-tailwindcss 0.4.2 declares MIT in its published package.json. Author: Marc Henry Schultz. Upstream: https://github.com/mhsdesign/jit-browser-tailwindcss . No standalone license text is included in the published package or repository root. Its bundled Tailwind CSS notices are retained below through the installed Tailwind package license.\n';fs.writeFileSync(out+'/LICENSES.txt',licenses);
console.log('Bundled',Object.keys(metadata).length,'module entry points');
