package studio.jiayu.neo;
import org.json.*;import java.io.*;
final class CloudFilesTest {
 static JSONArray checks;interface Attempt{void run()throws Exception;}
 static void ok(boolean b,String n)throws Exception{if(!b)throw new Exception(n);checks.put(n);}
 static void reject(String n,Attempt f)throws Exception{boolean bad=false;try{f.run();}catch(Exception e){bad=true;}ok(bad,n);}
 static JSONObject files(String a,String b)throws Exception{return new JSONObject().put("index.html",CloudFiles.entry(a,"utf8")).put("src/a.tsx",CloudFiles.entry(b,"utf8"));}
 static void run(MainActivity a){checks=new JSONArray();JSONObject r=new JSONObject();try{
 JSONObject initial=files("base","base"),base=CloudFiles.hashes(initial),local=files("local","base"),remote=files("base","cloud");
 JSONObject merged=CloudFiles.merge(local,remote,base);ok(merged.getJSONObject("index.html").getString("content").equals("local"),"independent local edit retained");ok(merged.getJSONObject("src/a.tsx").getString("content").equals("cloud"),"independent remote edit applied");
 reject("same file divergent edit rejected",()->CloudFiles.merge(files("local","base"),files("remote","base"),base));
 remote.remove("src/a.tsx");ok(!CloudFiles.merge(initial,remote,base).has("src/a.tsx"),"remote deletion of unchanged file");
 reject("delete modify conflict rejected",()->CloudFiles.merge(files("base","edited"),remote,base));
 JSONObject addition=files("base","base").put("new.txt",CloudFiles.entry("new","utf8"));ok(CloudFiles.merge(initial,addition,base).has("new.txt"),"remote addition");
 reject("unbased initial collision rejected",()->CloudFiles.merge(local,initial,new JSONObject()));
 JSONObject p=new JSONObject().put("files",new JSONObject().put("src/a.tsx","hello")).put("binaryFiles",new JSONObject().put("img.png","AP8BAg=="));JSONObject entries=CloudFiles.entries(p);CloudFiles.assign(p,entries);ok(p.getJSONObject("binaryFiles").getString("img.png").equals("AP8BAg=="),"binary roundtrip");CloudFiles.snapshot(p);p.getJSONObject("binaryFiles").put("img.png","AA==");ok(p.getJSONArray("snapshots").getJSONObject(0).getJSONObject("binaryFiles").getString("img.png").equals("AP8BAg=="),"binary snapshot copied");
 for(String path:new String[]{"../escape","a/../b","/abs","a\\b","a\nb\nc"})ok(!CloudFiles.path(path),"reject path "+path.replace('\n',' '));
 for(String path:new String[]{".env","capsule/signing/capsule.properties","key.pem","node_modules/a.js"})ok(CloudFiles.excluded(path),"exclude "+path);
 ok(CloudFiles.path("src/app/你好.tsx"),"unicode nested source");JSONObject bad=files("a","b");bad.getJSONObject("index.html").put("sha256","broken");reject("hash corruption rejected",()->CloudFiles.validate(bad));
 r.put("result","PASS");}catch(Exception e){try{r.put("result","FAIL").put("error",e.toString());}catch(Exception ignored){}}try{r.put("count",checks.length()).put("checks",checks);try(FileOutputStream f=new FileOutputStream(new File(a.getFilesDir(),"cloud-selftest.json"))){f.write(r.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}}
}
