package studio.jiayu.neo;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

class ApiClient {
 interface Progress {void update(String text,String reasoning);}
 Progress progress;final JSONArray usageSegments=new JSONArray();
 volatile HttpURLConnection connection;volatile boolean cancelled;
 static String endpoint(String raw,String protocol,boolean debug)throws Exception{
  URI u=new URI(raw.trim());boolean local=debug&&"http".equals(u.getScheme())&&"127.0.0.1".equals(u.getHost());
  if((!"https".equals(u.getScheme())&&!local)||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)throw new Exception("接口必须是无参数、无用户名的 HTTPS 地址");
  String s=raw.trim().replaceAll("/+$","");String suffix=protocol.equals("anthropic")?"/messages":"/chat/completions";
  return s.endsWith(suffix)?s:s+suffix;
 }
 static JSONArray models(JSONObject p,String secret,boolean debug)throws Exception{
  String protocol=p.optString("protocol","compatible");String request=endpoint(p.getString("endpoint"),protocol,debug);String suffix=protocol.equals("anthropic")?"/messages":"/chat/completions";String url=request.substring(0,request.length()-suffix.length())+"/models";
  if(secret.isEmpty())throw new Exception("请先保存 API Key");HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);if(protocol.equals("anthropic")){c.setRequestProperty("x-api-key",secret);c.setRequestProperty("anthropic-version","2023-06-01");}else c.setRequestProperty("Authorization","Bearer "+secret);
   int code=c.getResponseCode();if(code!=200)throw new IOException("模型检测 HTTP "+code+"。服务商可能不开放列表接口，可手动填写模型 ID。");byte[] bytes;try(InputStream in=c.getInputStream()){bytes=MainActivity.readLimited(in,2*1024*1024);}JSONArray data=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).getJSONArray("data"),ids=new JSONArray();java.util.TreeSet<String> sorted=new java.util.TreeSet<>();for(int i=0;i<data.length();i++){String id=data.getJSONObject(i).optString("id");if(!id.isEmpty())sorted.add(id);}for(String id:sorted)ids.put(id);return ids;
  }finally{c.disconnect();}
 }
 static JSONObject requestBody(JSONObject p,JSONArray messages,String system)throws Exception{
  String protocol=p.optString("protocol","compatible");
  JSONObject body=new JSONObject().put("model",p.getString("model")).put("stream",false);
  int max=Math.max(64,Math.min(ModelLimits.output(p.optString("endpoint"),p.optString("model")),p.optInt("maxTokens",4096)));
  if(protocol.equals("anthropic")){body.put("system",system).put("messages",messages).put("max_tokens",max);}
  else{JSONArray all=new JSONArray().put(new JSONObject().put("role","system").put("content",system));for(int i=0;i<messages.length();i++)all.put(messages.get(i));body.put("messages",all).put("max_tokens",max);String thinking=p.optString("thinking","");if(thinking.equals("enabled")||thinking.equals("disabled"))body.put("thinking",new JSONObject().put("type",thinking));}
  return body;
 }
 void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
 JSONObject request(JSONObject p,String secret,JSONArray messages,String system,boolean debug)throws Exception{
  String protocol=p.optString("protocol","compatible");String url=endpoint(p.getString("endpoint"),protocol,debug);
  if(secret.isEmpty())throw new Exception("请先在模型设置中保存 API Key");
  JSONObject body=requestBody(p,messages,system);
  boolean streaming=progress!=null&&p.optBoolean("streaming",true);if(streaming){body.put("stream",true);String host=new URI(url).getHost();if(!protocol.equals("anthropic")&&java.util.Arrays.asList("api.deepseek.com","api.stepfun.com","api.openai.com").contains(host))body.put("stream_options",new JSONObject().put("include_usage",true));}
  if(cancelled)throw new InterruptedIOException("已取消");
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();connection=c;
  try{
   c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(ModelLimits.deepSeek(p.optString("endpoint"),p.optString("model"))?0:120000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
   if(protocol.equals("anthropic")){c.setRequestProperty("x-api-key",secret);c.setRequestProperty("anthropic-version","2023-06-01");}else c.setRequestProperty("Authorization","Bearer "+secret);
   byte[]data=body.toString().getBytes(StandardCharsets.UTF_8);if(data.length>32*1024*1024)throw new IOException("请求超过32 MB传输限制；请分批处理");c.setFixedLengthStreamingMode(data.length);
   try(OutputStream o=c.getOutputStream()){o.write(data);}
   int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code+(code==400?"：模型参数或上下文超限；上下文总量包含输入和输出预留，请减少输入或输出上限":code==401?"：密钥或权限错误":code==429?"：额度或速率限制":code>=300&&code<400?"：已阻止重定向，请核对接口地址":"：请求失败，请检查模型、参数和服务状态"));
   JSONObject raw;
   if(c.getContentType()!=null&&c.getContentType().toLowerCase().contains("text/event-stream")){try(InputStream in=c.getInputStream()){raw=StreamResponse.read(in,protocol.equals("anthropic"),this);}}
   else{byte[] bytes;try(InputStream in=c.getInputStream()){bytes=MainActivity.readLimited(in,16*1024*1024);}raw=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
   String content="",reason="",finish="";
   if(protocol.equals("anthropic")){finish=raw.optString("stop_reason");JSONArray a=raw.getJSONArray("content");for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);if(x.optString("type").equals("text"))content+=x.optString("text");}}
   else{JSONObject choice=raw.getJSONArray("choices").getJSONObject(0);finish=choice.optString("finish_reason");JSONObject m=choice.getJSONObject("message");content=m.isNull("content")?"":m.optString("content");reason=m.optString("reasoning_content","");}
   if(content.isEmpty()&&reason.isEmpty())throw new IOException("接口没有返回文本内容");
   boolean incomplete=content.isEmpty()||finish.equals("length")||finish.equals("max_tokens");if(incomplete)content="[工作台提示：本次输出不完整。"+(content.isEmpty()?"接口只返回推理，没有最终答案。":"已达到输出上限，代码可能被截断。")+"请调整推理模式或输出上限后重试；已记录真实用量。]\n\n"+content;
   return new JSONObject().put("incomplete",incomplete).put("finishReason",finish).put("text",content).put("reasoning",reason).put("usage",Usage.parse(raw,protocol)).put("model",raw.optString("model",p.getString("model")));
  }finally{c.disconnect();connection=null;}
 }
}
