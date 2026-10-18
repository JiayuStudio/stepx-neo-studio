package studio.jiayu.neo;
import android.os.*;
import android.app.AlertDialog;
import java.util.UUID;
import org.json.*;
import java.nio.charset.StandardCharsets;
final class StudioUsage {
 final MainActivity a;boolean running;int generation;StudioUsage(MainActivity activity){a=activity;JSONObject old=a.state.optJSONObject("studioUsageCheck");if(old!=null&&"running".equals(old.optString("status")))try{old.put("status","interrupted").put("message","应用已重启，未重复发送诊断；请先核对 Studio 任务。旧报告保留。");}catch(Exception ignored){}}
 void requestRuntime(){
  if(running||a.link.running||a.active!=null||a.building){a.toast("请先结束当前任务");return;}
  String url=a.studio.getUrl();if(url==null||!url.matches("https://studio\\.stepx\\.com/builder/(app|agent|skill)/[A-Za-z0-9_-]+/?")){a.toast("先在 Studio 打开要统计的项目");return;}
  new AlertDialog.Builder(a).setTitle("读取 Studio 项目 Token").setMessage("会向当前 Studio 项目发送一次只读诊断，让它读取自己的 SDK 用量日志。诊断本身会消耗 Studio 额度，请保持前台。\n\n统计范围是该项目已落盘日志，可能不含最近对话及本次诊断；不是账号套餐余额或官方账单。已有草稿和正在执行的任务会保留。").setPositiveButton("读取项目用量",(d,w)->startRuntime(url)).setNegativeButton("取消",null).show();
 }
 void startRuntime(String url){if(running||a.link.running||a.active!=null||a.building||!url.equals(a.studio.getUrl())){a.toast("Studio 状态已变化，请重试");return;}try{
  String scanner=asset("studio-usage-scan.cjs"),js=asset("studio-runtime.js"),dom=asset("studio-link.js");
  JSONObject op=new JSONObject().put("nonce",UUID.randomUUID().toString()).put("scanner",scanner);running=true;int id=++generation;a.show("studio");a.awake(true);
  JSONObject pending=new JSONObject().put("status","running").put("time",System.currentTimeMillis()).put("message","正在读取 Studio 项目日志用量，请保持前台");a.state.put("studioUsageCheck",pending);a.saveState();a.event("studioUsage",pending);
  a.studio.evaluateJavascript(dom+";"+js+";window.__neoRuntimeCancelled=false;window.__neoRuntimeId="+JSONObject.quote(op.getString("nonce"))+";window.__neoUsageGeneration="+id+";window.__neoUsageResult=null;neoRunStudioUsage("+op+").then(r=>{if(window.__neoUsageGeneration==="+id+")window.__neoUsageResult=r});void 0",null);
  poll(id,url,System.currentTimeMillis()+510000);
 }catch(Exception e){finish(new JSONObject(),"用量读取未启动："+a.safeError(e));}}
 String asset(String name)throws Exception{return new String(MainActivity.readLimited(a.getAssets().open(name),64000),StandardCharsets.UTF_8);}
 void stop(){if(!running)return;++generation;a.studio.evaluateJavascript("window.__neoRuntimeCancelled=true;window.__neoUsageGeneration=-1;void 0",null);finish(new JSONObject(),"已停止本机读取；已发送的诊断需在 Studio 中停止。旧报告保留。");}
 void check(){if(running)return;String url=a.studio.getUrl();if(url==null||!url.startsWith("https://studio.stepx.com/")){a.toast("请先打开并登录官方 Studio");return;}try{
  String js=new String(MainActivity.readLimited(a.getAssets().open("studio-usage.js"),32000),StandardCharsets.UTF_8);running=true;int id=++generation;
  a.studio.evaluateJavascript(js+";window.__neoUsageGeneration="+id+";window.__neoUsageResult=null;neoInspectStudioUsage().then(r=>{if(window.__neoUsageGeneration==="+id+")window.__neoUsageResult=r});void 0",null);a.toast("正在检查当前 Studio 项目的官方用量字段");poll(id,url,System.currentTimeMillis()+60000);
 }catch(Exception e){running=false;a.toast("检查脚本未加载");}}
 void poll(int id,String url,long deadline){new Handler(a.getMainLooper()).postDelayed(()->{if(id!=generation)return;if(a.isDestroyed()){running=false;return;}if(!url.equals(a.studio.getUrl())){finish(new JSONObject(),"检查期间 Studio 页面已变化，请重试");return;}
  a.studio.evaluateJavascript("window.__neoUsageResult||null",s->{if(id!=generation)return;try{if(s==null||s.equals("null")){if(System.currentTimeMillis()<deadline){poll(id,url,deadline);return;}finish(new JSONObject(),"检查超时，请重试");return;}finish(new JSONObject(s),null);}catch(Exception e){finish(new JSONObject(),"官方返回格式无法识别");}});
 },400);}
 void storeReport(JSONObject report)throws Exception{JSONObject previous=a.state.optJSONObject("studioUsageReport");a.state.put("studioUsageReport",report);JSONObject reports=a.state.optJSONObject("studioUsageReports");if(reports==null)reports=new JSONObject();if(previous!=null)reports.put(previous.getString("projectId"),previous);reports.put(report.getString("projectId"),report);while(reports.length()>30){String oldest=null;long time=Long.MAX_VALUE;java.util.Iterator<String> keys=reports.keys();while(keys.hasNext()){String key=keys.next();long t=reports.getJSONObject(key).optLong("generatedAt");if(t<time){time=t;oldest=key;}}if(oldest==null)break;reports.remove(oldest);}a.state.put("studioUsageReports",reports);}
 void exportReport(String id)throws Exception{JSONObject reports=a.state.optJSONObject("studioUsageReports"),r=reports==null?null:reports.optJSONObject(id);if(r==null){r=a.state.optJSONObject("studioUsageReport");if(r==null||!id.equals(r.optString("projectId")))throw new Exception("未找到这个项目的用量报告");}a.exportText("studio-project-usage.json",r.toString(2),"application/json");}
 void finish(JSONObject r,String error){running=false;a.awake(a.prefs.getBoolean("awake",false));try{if(error!=null)r.put("status","error").put("message",error).put("time",System.currentTimeMillis());synchronized(a){JSONObject before=new JSONObject(a.state.toString());try{a.state.put("studioUsageCheck",r);if("runtime".equals(r.optString("status"))&&r.optJSONObject("report")!=null)storeReport(r.getJSONObject("report"));a.saveState();}catch(Exception e){a.state=before;throw e;}}a.event("studioUsage",r);if("runtime".equals(r.optString("status"))&&(a.currentTab.equals("studio")||a.currentTab.equals("usage")))a.show("usage");}catch(Exception e){a.toast("用量检查记录保存失败");}}
}
