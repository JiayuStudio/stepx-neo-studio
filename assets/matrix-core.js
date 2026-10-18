'use strict';
const MatrixCore=(()=>{
 const blank=()=>({durationMs:500,pixels:Array(391).fill(0)});
 function validate(o){if(!o||o.schema!=='stepx-matrix/1'||o.width!==17||o.height!==23||typeof o.loop!=='boolean')throw Error('需要 stepx-matrix/1 格式，17 × 23，loop 为布尔值');if(!Array.isArray(o.frames)||!o.frames.length||o.frames.length>120)throw Error('需要 1–120 帧');for(const f of o.frames){if(!Number.isInteger(f.durationMs)||f.durationMs<200||f.durationMs>60000)throw Error('帧时长需要 200–60000 毫秒');if(!Array.isArray(f.pixels)||f.pixels.length!==391||f.pixels.some(v=>!Number.isInteger(v)||v<0||v>255))throw Error('每帧需要 391 个 0–255 整数');}return o;}
 function initial(){const frames=[blank(),blank()];for(const f of frames)f.durationMs=1000;for(let y=0;y<23;y++)for(let x=0;x<17;x++){if((x===1||x===15)&&y>=3&&y<=19||(y===3||y===19)&&x>=1&&x<=15)frames[0].pixels[y*17+x]=255;if(y>=3&&y<=19&&(x===y-3||x===19-y))frames[1].pixels[y*17+x]=255;}return {schema:'stepx-matrix/1',width:17,height:23,loop:true,frames};}
 function draw(canvas,frame){const c=canvas.getContext('2d');c.fillStyle='#151619';c.fillRect(0,0,340,460);for(let y=0;y<23;y++)for(let x=0;x<17;x++){const v=frame.pixels[y*17+x];c.beginPath();c.arc(x*20+10,y*20+10,6.5,0,Math.PI*2);c.fillStyle=v?`rgb(${v},${v},${v})`:'#303136';c.fill();}}
 return {blank,validate,initial,draw};
})();
if(typeof module!=='undefined')module.exports=MatrixCore;
