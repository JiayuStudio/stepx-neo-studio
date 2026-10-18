package studio.jiayu.neo;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import android.util.Base64;
/** Cloud source snapshots and conservative three-way merge. No network or state writes. */
final class CloudFiles {
 static boolean path(String p){if(p==null||p.isEmpty()||p.length()>240||p.startsWith("/")||java.util.regex.Pattern.compile("[\\\\\\x00-\\x1f\\x7f]").matcher(p).find())return false;for(String s:p.split("/",-1))if(s.isEmpty()||s.equals(".")||s.equals(".."))return false;return true;}
 static boolean excluded(String p){return p.matches("(?i)(?:.*/)?(?:\\.git|node_modules|\\.gradle|build|dist|\\.next|\\.cache)(?:/.*)?")||p.matches("(?i)(?:.*/)?(?:\\.env(?:\\..*)?|credentials(?:\\..*)?|secrets?(?:\\..*)?|local\\.properties|capsule\\.properties|id_rsa|id_ed25519)")||p.matches("(?i).*\\.(p12|pfx|jks|keystore|pem|key)");}
 static String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}
 static byte[] bytes(JSONObject e)throws Exception{String c=e.getString("content"),encoding=e.getString("encoding");if(encoding.equals("utf8"))return c.getBytes(StandardCharsets.UTF_8);if(encoding.equals("base64"))return Base64.decode(c,Base64.NO_WRAP);throw new Exception("不支持的文件编码");}
 static JSONObject entry(String content,String encoding)throws Exception{JSONObject e=new JSONObject().put("content",content).put("encoding",encoding);return e.put("sha256",hash(bytes(e)));}
 static JSONObject entries(JSONObject p)throws Exception{JSONObject out=new JSONObject(),f=p.getJSONObject("files"),b=p.optJSONObject("binaryFiles");for(Iterator<String>i=f.keys();i.hasNext();){String n=i.next();out.put(n,entry(f.getString(n),"utf8"));}if(b!=null)for(Iterator<String>i=b.keys();i.hasNext();){String n=i.next();if(out.has(n))throw new Exception("文件路径重复");out.put(n,entry(b.getString(n),"base64"));}validate(out);return out;}
 static void validate(JSONObject entries)throws Exception{if(entries.length()>300)throw new Exception("项目超过300个文件");long size=0;for(Iterator<String>i=entries.keys();i.hasNext();){String n=i.next();if(!path(n)||excluded(n))throw new Exception("同步不包含此路径："+n);JSONObject e=entries.getJSONObject(n);byte[]b=bytes(e);size+=b.length;if(b.length>1048576||size>4194304)throw new Exception("同步上限为单文件1 MB、项目4 MB");if(!hash(b).equals(e.getString("sha256")))throw new Exception("文件校验失败："+n);}}
 static JSONObject hashes(JSONObject e)throws Exception{JSONObject h=new JSONObject();for(Iterator<String>i=e.keys();i.hasNext();){String n=i.next();h.put(n,e.getJSONObject(n).getString("sha256"));}return h;}
 static String h(JSONObject e,String n){JSONObject v=e.optJSONObject(n);return v==null?"":v.optString("sha256");}
 static JSONObject merge(JSONObject local,JSONObject remote,JSONObject base)throws Exception{
  validate(local);validate(remote);JSONObject result=new JSONObject(local.toString());Set<String>names=new TreeSet<>();for(JSONObject o:new JSONObject[]{local,remote,base})for(Iterator<String>i=o.keys();i.hasNext();)names.add(i.next());ArrayList<String>conflicts=new ArrayList<>();
  for(String n:names){String l=h(local,n),r=h(remote,n),b=base.optString(n,"");if(l.equals(r))continue;if(l.equals(b)){if(remote.has(n))result.put(n,remote.get(n));else result.remove(n);}else if(!r.equals(b))conflicts.add(n);}
  if(!conflicts.isEmpty())throw new Exception("两边都修改了同一文件，未覆盖本地："+String.join("、",conflicts));validate(result);return result;
 }
 static void assign(JSONObject p,JSONObject entries)throws Exception{JSONObject f=new JSONObject(),b=new JSONObject();for(Iterator<String>i=entries.keys();i.hasNext();){String n=i.next();JSONObject e=entries.getJSONObject(n);(e.getString("encoding").equals("utf8")?f:b).put(n,e.getString("content"));}p.put("files",f).put("binaryFiles",b);}
 static void snapshot(JSONObject p)throws Exception{JSONArray old=p.optJSONArray("snapshots"),out=new JSONArray();if(old!=null)for(int i=Math.max(0,old.length()-1);i<old.length();i++)out.put(old.get(i));out.put(new JSONObject().put("time",System.currentTimeMillis()).put("files",new JSONObject(p.getJSONObject("files").toString())).put("binaryFiles",p.optJSONObject("binaryFiles")==null?new JSONObject():new JSONObject(p.getJSONObject("binaryFiles").toString())));p.put("snapshots",out);}
}
