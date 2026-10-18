package studio.jiayu.neo;

import android.app.Dialog;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Handler;
import android.view.WindowInsets;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline compiler and isolated project renderer. Neither WebView has an Android bridge. */
final class LocalRunner {
 final MainActivity a; final JSONObject project; final Handler handler=new Handler();
 final Map<String,byte[]> files=new java.util.concurrent.ConcurrentHashMap<>();
 Dialog dialog; WebView compiler,view; LinearLayout box; TextView status; boolean closed,online,ready; String detail="";
 String host,projectId; long deadline;final JSONArray events=new JSONArray();
 synchronized JSONObject report()throws Exception{return new JSONObject().put("ready",ready).put("closed",closed).put("events",new JSONArray(events.toString()));}
 synchronized void record(String kind,String message){try{if(events.length()>=100)events.remove(0);events.put(new JSONObject().put("kind",kind).put("message",message).put("time",System.currentTimeMillis()));}catch(Exception ignored){}}
 LocalRunner(MainActivity activity,JSONObject source)throws Exception{
  a=activity;projectId=source.getString("id");project=new JSONObject().put("files",new JSONObject(source.getJSONObject("files").toString())).put("binaryFiles",source.optJSONObject("binaryFiles")==null?new JSONObject():new JSONObject(source.getJSONObject("binaryFiles").toString()));
  CloudFiles.entries(project);
  host="p"+CloudFiles.hash(source.getString("id").getBytes(StandardCharsets.UTF_8)).substring(0,24)+".neo-preview.invalid";
 }
 void start(){
  dialog=new Dialog(a,android.R.style.Theme_Material_Light_NoActionBar);box=new LinearLayout(a);box.setOrientation(1);box.setBackgroundColor(a.BG);
  LinearLayout bar=new LinearLayout(a);bar.addView(a.button("关闭","关闭本地运行",()->dialog.dismiss()));
  bar.addView(a.button("重载","重新运行当前快照",()->{if(view!=null)view.reload();}));
  Switch network=new Switch(a);network.setText("联网");network.setTextColor(a.INK);network.setOnCheckedChangeListener((b,on)->{online=on;if(ready)status.setText("本地运行 · "+(on?"联网":"离线")+" · 点此查看说明");if(view!=null){view.getSettings().setBlockNetworkLoads(!on);view.reload();} });bar.addView(network);
  box.addView(bar);status=new TextView(a);status.setTextColor(a.INK);status.setTextSize(12);status.setPadding(12,4,12,4);status.setText("正在 Neo 本机编译… 首次运行可能需要数秒");status.setOnClickListener(v->{if(ready)new android.app.AlertDialog.Builder(a).setTitle("本地运行").setMessage(detail+"\n\n项目在 Neo 本机编译和执行。联网开关只开放 HTTPS 请求；系统权限、Step 原生桥接和服务端功能不在此预览中提供。").setPositiveButton("知道了",null).show();});box.addView(status);
  compiler=new WebView(a);compiler.getSettings().setJavaScriptEnabled(true);compiler.getSettings().setAllowFileAccess(false);compiler.getSettings().setAllowContentAccess(false);compiler.getSettings().setBlockNetworkLoads(true);
  compiler.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String path=r.getUrl().getPath();if(!r.getUrl().getHost().equals("neo-compiler.invalid"))return blocked();try{if(path.equals("/project.json"))return response("application/json",project.toString().getBytes(StandardCharsets.UTF_8));return asset(path.substring(1));}catch(Exception e){return blocked();}}@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}});
  box.addView(compiler,new LinearLayout.LayoutParams(1,1));dialog.setContentView(box);dialog.setOnDismissListener(d->{closed=true;handler.removeCallbacksAndMessages(null);if(compiler!=null){compiler.stopLoading();compiler.destroy();compiler=null;}if(view!=null){view.stopLoading();view.destroy();view=null;}files.clear();});
  dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setDecorFitsSystemWindows(false);dialog.getWindow().getInsetsController().setSystemBarsAppearance(0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);box.setOnApplyWindowInsetsListener((v,in)->{Insets i=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());box.setPadding(i.left,i.top,i.right,i.bottom);return in;});box.requestApplyInsets();
  deadline=System.currentTimeMillis()+90000;compiler.loadUrl("https://neo-compiler.invalid/local-runner.html");handler.postDelayed(this::poll,250);
 }
 void poll(){if(closed||compiler==null)return;if(System.currentTimeMillis()>deadline){status.setText("本机编译超时，请关闭后重试或缩小工程。");compiler.stopLoading();return;}compiler.evaluateJavascript("JSON.stringify(window.__neoBuild||null)",raw->{if(closed)return;try{Object parsed=new JSONTokener(raw).nextValue();if(parsed instanceof String&&!parsed.equals("null")){JSONObject result=new JSONObject((String)parsed);if(result.has("error")){record("compile_error",result.getString("error"));status.setText("本机编译失败："+result.getString("error"));return;}if(result.has("files")){JSONObject out=result.getJSONObject("files");long size=0;for(Iterator<String>i=out.keys();i.hasNext();){String n=i.next();if(!CloudFiles.path(n))throw new Exception("非法输出路径");byte[]b=CloudFiles.bytes(out.getJSONObject(n));size+=b.length;if(size>16777216)throw new Exception("编译结果超过16MB");files.put(n,b);}show(result.optString("note"));return;}}handler.postDelayed(this::poll,250);}catch(Exception e){status.setText("本机编译失败："+e.getMessage());}});}
 void show(String note){
  box.removeView(compiler);compiler.destroy();compiler=null;ready=true;detail=note;record("compiled",note);status.setText("本地运行 · "+(online?"联网":"离线")+" · 点此查看说明");view=new WebView(a);WebSettings s=view.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(!online);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);
  CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
  view.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){Uri u=r.getUrl();if(host.equals(u.getHost())){String path=u.getPath();try{if(path.startsWith("/__vendor/"))return asset("local-vendor/"+path.substring(10));String n=path.substring(1);if(n.isEmpty())n="index.html";byte[]b=files.get(n);if(b==null)b=files.get("public/"+n);if(b!=null)return response(mime(n),b);return blocked();}catch(Exception e){return blocked();}}return online&&"https".equals(u.getScheme())?null:blocked();}@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !host.equals(r.getUrl().getHost());}});
  view.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){record(m.messageLevel().toString(),m.message()+" ("+m.lineNumber()+")");if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR)status.setText("运行错误："+m.message()+"（"+m.lineNumber()+"）");return true;}@Override public void onPermissionRequest(PermissionRequest r){r.deny();}});
  box.addView(view,new LinearLayout.LayoutParams(-1,0,1));view.loadUrl("https://"+host+"/index.html");
 }
 WebResourceResponse asset(String path)throws Exception{if(!CloudFiles.path(path))return blocked();return new WebResourceResponse(mime(path),"UTF-8",a.getAssets().open(path));}
 static WebResourceResponse response(String type,byte[]b){return new WebResourceResponse(type,"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(b));}
 static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",404,"Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 static String mime(String n){n=n.toLowerCase(Locale.ROOT);if(n.endsWith("html"))return "text/html";if(n.endsWith("js")||n.endsWith("mjs"))return "text/javascript";if(n.endsWith("css"))return "text/css";if(n.endsWith("wasm"))return "application/wasm";if(n.endsWith("json"))return "application/json";if(n.endsWith("svg"))return "image/svg+xml";if(n.endsWith("png"))return "image/png";if(n.endsWith("jpg")||n.endsWith("jpeg"))return "image/jpeg";if(n.endsWith("webp"))return "image/webp";if(n.endsWith("woff2"))return "font/woff2";if(n.endsWith("mp3"))return "audio/mpeg";if(n.endsWith("mp4"))return "video/mp4";return "application/octet-stream";}
}
