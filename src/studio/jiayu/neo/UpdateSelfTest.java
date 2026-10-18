package studio.jiayu.neo;
import org.json.*;
import java.io.*;
/** Invoked only by an explicit debug-build intent. Does not touch user projects or keys. */
final class UpdateSelfTest {
 interface Attempt{void run()throws Exception;}
 static JSONArray checks;
 static void ok(boolean b,String name)throws Exception{if(!b)throw new Exception(name);checks.put(name);}
 static void reject(String name,Attempt f)throws Exception{boolean rejected=false;try{f.run();}catch(Exception e){rejected=true;}ok(rejected,name);}
 static JSONObject release()throws Exception{return new JSONObject().put("tag_name","v1.1.4").put("assets",new JSONArray().put(new JSONObject().put("name","StepXNeoStudio-1.1.4-release.apk").put("state","uploaded").put("id",123).put("size",1000).put("digest","sha256:"+new String(new char[64]).replace('\0','a')).put("url","https://api.github.com/repos/"+AppUpdater.REPO+"/releases/assets/123")));}
 static void run(MainActivity a){JSONObject report=new JSONObject();checks=new JSONArray();try{
  ok(UpdateRelease.compare("1.10.0","1.9.9")>0,"numeric version comparison");ok(UpdateRelease.compare("v1.1.4","1.1.4")==0,"equal version");ok(UpdateRelease.compare("1.1.2","1.1.4")<0,"older version");reject("prerelease version rejected",()->UpdateRelease.parts("1.1.4-beta"));
  ok(UpdateRelease.parse(release(),AppUpdater.REPO).assetId==123,"valid stable asset");
  reject("draft rejected",()->UpdateRelease.parse(release().put("draft",true),AppUpdater.REPO));reject("prerelease rejected",()->UpdateRelease.parse(release().put("prerelease",true),AppUpdater.REPO));
  for(String field:new String[]{"name","digest","size","url"}){JSONObject r=release();r.getJSONArray("assets").getJSONObject(0).put(field,field.equals("size")?104857601:field.equals("url")?"https://evil.example/a.apk":"bad");reject("invalid "+field,()->UpdateRelease.parse(r,AppUpdater.REPO));}
  JSONObject duplicate=release();duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(0));reject("duplicate rejected",()->UpdateRelease.parse(duplicate,AppUpdater.REPO));
  AppUpdater u=a.updater;reject("HTTP rejected",()->u.open("http://api.github.com/", "", "application/json"));reject("untrusted host rejected",()->u.open("https://example.com/", "", "application/json"));reject("userinfo rejected",()->u.open("https://x@api.github.com/", "", "application/json"));
  ok(u.failure(new IOException("HTTP 401")).contains("令牌"),"auth error explained");ok(u.failure(new IOException("HTTP 429")).contains("频繁"),"rate limit explained");
  File dir=new File(a.getFilesDir(),"update-tests");File valid=new File(dir,"valid.apk");if(valid.exists()){UpdateRelease r=UpdateRelease.parse(release(),AppUpdater.REPO);u.verify(valid,r);ok(true,"valid signed newer APK");for(String name:new String[]{"tampered","wrong-signer","wrong-package","debug","older"})reject("reject APK "+name,()->u.verify(new File(dir,name+".apk"),r));}else throw new Exception("missing APK fixtures");
  report.put("result","PASS");
 }catch(Exception e){try{report.put("result","FAIL").put("error",e.toString());}catch(Exception ignored){}}try{report.put("count",checks.length()).put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(a.getFilesDir(),"update-selftest.json"))){out.write(report.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}}
}
