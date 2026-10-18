package studio.jiayu.neo;
import org.json.*;
/** All values originate in provider usage. Detail fields are subsets, not extra totals. */
final class Usage {
 static String source(JSONObject profile){
  try{return "api.stepfun.com".equalsIgnoreCase(android.net.Uri.parse(profile.optString("endpoint")).getHost())?"step_api":"external_api";}catch(Exception e){return "external_api";}
 }
 static JSONObject parse(JSONObject body,String protocol)throws Exception{
  JSONObject r=new JSONObject();JSONObject u=body.optJSONObject("usage");
  boolean a=protocol.equals("anthropic");String ik=a?"input_tokens":"prompt_tokens",ok=a?"output_tokens":"completion_tokens";
  if(u==null||!u.has(ik)||!u.has(ok)||u.isNull(ik)||u.isNull(ok)){r.put("known",false);return r;}
  long input=u.getLong(ik),output=u.getLong(ok);if(input<0||output<0)throw new Exception("无效的用量响应");
  long read=a?u.optLong("cache_read_input_tokens",0):u.optLong("prompt_cache_hit_tokens",0);
  long write=a?u.optLong("cache_creation_input_tokens",0):0;
  JSONObject pd=u.optJSONObject("prompt_tokens_details"),cd=u.optJSONObject("completion_tokens_details");
  if(!a&&pd!=null)read=pd.optLong("cached_tokens",read);
  if(a)input+=read+write;
  return r.put("known",true).put("input",input).put("output",output).put("total",input+output).put("cacheRead",read).put("cacheWrite",write).put("reasoning",cd==null?0:cd.optLong("reasoning_tokens",0));
 }
}
