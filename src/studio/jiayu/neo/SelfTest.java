package studio.jiayu.neo;
import org.json.*;
import java.io.*;
final class SelfTest {
 static int passed;static JSONArray details;
 static void check(boolean b,String name)throws Exception{if(!b)throw new Exception("FAIL "+name);passed++;details.put(name);}
 static JSONObject response(String json)throws Exception{return new JSONObject(json);}
 static JSONObject profile(String path,String protocol)throws Exception{return new JSONObject().put("endpoint","http://127.0.0.1:8768/"+path).put("protocol",protocol).put("model","fixture").put("maxTokens",128);}
 static void run(MainActivity a){passed=0;details=new JSONArray();JSONObject out=new JSONObject();try{
  JSONObject u=Usage.parse(response("{usage:{prompt_tokens:100,completion_tokens:40,prompt_tokens_details:{cached_tokens:60},completion_tokens_details:{reasoning_tokens:10}}}"),"compatible");check(u.getLong("total")==140,"compatible subsets not double counted");check(u.getLong("cacheRead")==60&&u.getLong("reasoning")==10,"detail fields");
  u=Usage.parse(response("{usage:{input_tokens:10,output_tokens:20,cache_read_input_tokens:80,cache_creation_input_tokens:30}}"),"anthropic");check(u.getLong("input")==120&&u.getLong("total")==140,"anthropic all input buckets");
  check(!Usage.parse(response("{}"),"compatible").getBoolean("known"),"missing usage unknown");check(!Usage.parse(response("{usage:{prompt_tokens:4}}"),"compatible").getBoolean("known"),"partial usage unknown");check(Usage.parse(response("{usage:{prompt_tokens:0,completion_tokens:0}}"),"compatible").getBoolean("known"),"zero tokens valid");
  try{ApiClient.endpoint("http://example.com/v1","compatible",false);throw new Exception("accepted HTTP");}catch(Exception e){check(!e.getMessage().equals("accepted HTTP"),"reject cleartext endpoints");}
  try{ApiClient.endpoint("https://user:pass@example.com/v1","compatible",false);throw new Exception("accepted credentials");}catch(Exception e){check(!e.getMessage().equals("accepted credentials"),"reject URL credentials");}
  check(ApiClient.endpoint("https://example.com/v1/","compatible",false).equals("https://example.com/v1/chat/completions"),"endpoint suffix normalization");
  JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content","fixture"));
  JSONObject r=new ApiClient().request(profile("ok","compatible"),"fixture-secret",messages,"system",true);check(r.getString("text").equals("fixture reply")&&r.getJSONObject("usage").getLong("total")==140,"compatible transport and usage");
  r=new ApiClient().request(profile("anthropic","anthropic"),"fixture-secret",messages,"system",true);check(r.getString("text").equals("anthropic reply")&&r.getJSONObject("usage").getLong("total")==140,"anthropic transport and usage");
  r=new ApiClient().request(profile("missing","compatible"),"fixture-secret",messages,"system",true);check(!r.getJSONObject("usage").getBoolean("known"),"transport missing usage");
  r=new ApiClient().request(profile("thinking","compatible").put("thinking","disabled"),"fixture-secret",messages,"system",true);check(!r.optBoolean("incomplete"),"thinking toggle transport");
  r=new ApiClient().request(profile("incomplete","compatible"),"fixture-secret",messages,"system",true);check(r.optBoolean("incomplete")&&r.getString("text").contains("不完整")&&r.getJSONObject("usage").getLong("total")==140,"truncated reasoning keeps usage and warning");
  for(String path:new String[]{"bad","redirect"}){try{new ApiClient().request(profile(path,"compatible"),"fixture-secret",messages,"system",true);throw new Exception("accepted "+path);}catch(Exception e){check(e.getMessage().startsWith("HTTP "),"reject "+path);}}
  a.vault.put("selftest","secret-value");check(a.vault.get("selftest").equals("secret-value"),"keystore encryption roundtrip");a.vault.remove("selftest");check(!a.vault.has("selftest"),"key removal");
  check(!a.safeFile("../secret")&&!a.safeFile("x/y.html")&&a.safeFile("index.html"),"import traversal blocked");out.put("result","PASS");
 }catch(Exception e){try{out.put("result","FAIL").put("error",e.toString());}catch(Exception ignored){}}try{out.put("passed",passed).put("checks",details);try(FileOutputStream f=new FileOutputStream(new File(a.getFilesDir(),"selftest.json"))){f.write(out.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}}
}
