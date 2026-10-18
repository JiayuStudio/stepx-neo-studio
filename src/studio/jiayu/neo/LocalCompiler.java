package studio.jiayu.neo;
import android.webkit.*;
import android.os.Handler;
import org.json.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
/** Runs the same bundled offline compiler used by the visible preview. */
final class LocalCompiler {
 static JSONObject check(MainActivity a,JSONObject project)throws Exception{
  JSONObject snapshot=new JSONObject(project.toString());CloudFiles.entries(snapshot);CountDownLatch done=new CountDownLatch(1);JSONObject[] result={null};WebView[] view={null};Handler handler=new Handler(a.getMainLooper());
  a.runOnUiThread(()->{try{WebView w=new WebView(a);view[0]=w;w.getSettings().setJavaScriptEnabled(true);w.getSettings().setAllowFileAccess(false);w.getSettings().setAllowContentAccess(false);w.getSettings().setBlockNetworkLoads(true);w.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){try{if(!"neo-compiler.invalid".equals(r.getUrl().getHost()))return LocalRunner.blocked();String path=r.getUrl().getPath().substring(1);if(path.equals("project.json"))return LocalRunner.response("application/json",snapshot.toString().getBytes(StandardCharsets.UTF_8));if(!CloudFiles.path(path))return LocalRunner.blocked();return new WebResourceResponse(LocalRunner.mime(path),"UTF-8",a.getAssets().open(path));}catch(Exception e){return LocalRunner.blocked();}}});w.loadUrl("https://neo-compiler.invalid/local-runner.html");long deadline=System.currentTimeMillis()+60000;Runnable poll=new Runnable(){public void run(){if(done.getCount()==0)return;if(System.currentTimeMillis()>deadline){done.countDown();return;}w.evaluateJavascript("JSON.stringify(window.__neoBuild||null)",raw->{try{Object decoded=new JSONTokener(raw).nextValue();if(decoded instanceof String&&!decoded.equals("null")){result[0]=new JSONObject((String)decoded);done.countDown();return;}}catch(Exception ignored){}handler.postDelayed(this,200);});}};handler.postDelayed(poll,200);}catch(Exception e){done.countDown();}});
  try{if(!done.await(65,TimeUnit.SECONDS)||result[0]==null)throw new Exception("本地编译检查超时");JSONObject r=result[0];if(r.has("error"))return new JSONObject().put("ok",false).put("error",r.getString("error"));return new JSONObject().put("ok",true).put("outputFiles",r.getJSONObject("files").length()).put("note",r.optString("note")).put("scope","本机源码编译检查；实际交互仍需运行验证");}finally{done.countDown();a.runOnUiThread(()->{if(view[0]!=null){view[0].stopLoading();view[0].destroy();}});}
 }
}
