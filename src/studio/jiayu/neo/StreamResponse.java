package studio.jiayu.neo;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Bounded SSE decoder; incomplete streams never become project writes. */
final class StreamResponse {
 final StringBuilder text=new StringBuilder(),reason=new StringBuilder();JSONObject usage;String model="",finish="";boolean ended;
 void event(String data,boolean anthropic,ApiClient.Progress progress)throws Exception{
  if(data.equals("[DONE]")){ended=true;return;}if(data.isEmpty())return;JSONObject o=new JSONObject(data);if(o.has("error")||o.optString("type").equals("error"))throw new IOException("流式接口返回错误");
  if(anthropic){String type=o.optString("type");if(type.equals("message_start")){JSONObject m=o.getJSONObject("message");usage=m.optJSONObject("usage");model=m.optString("model");}else if(type.equals("content_block_delta")){JSONObject d=o.getJSONObject("delta");if(d.optString("type").equals("text_delta"))text.append(d.optString("text"));else if(d.optString("type").equals("thinking_delta"))reason.append(d.optString("thinking"));}else if(type.equals("message_delta")){finish=o.getJSONObject("delta").optString("stop_reason");JSONObject u=o.optJSONObject("usage");if(u!=null){if(usage==null)usage=new JSONObject();java.util.Iterator<String> it=u.keys();while(it.hasNext()){String k=it.next();usage.put(k,u.get(k));}}}else if(type.equals("message_stop"))ended=true;
  }else{if(o.has("model"))model=o.optString("model");JSONObject u=o.optJSONObject("usage");if(u!=null)usage=u;JSONArray choices=o.optJSONArray("choices");if(choices!=null&&choices.length()>0){JSONObject c=choices.getJSONObject(0),d=c.optJSONObject("delta");if(d!=null){if(!d.isNull("content"))text.append(d.optString("content",""));if(!d.isNull("reasoning_content"))reason.append(d.optString("reasoning_content",""));}if(!c.isNull("finish_reason"))finish=c.optString("finish_reason","");}}
  if(text.length()+reason.length()>8*1024*1024)throw new IOException("流式响应过大");if(progress!=null)progress.update(text.toString(),reason.toString());
 }
 JSONObject result(boolean anthropic)throws Exception{if(!ended)throw new IOException("流式响应中断，未保存项目修改");JSONObject r=new JSONObject().put("model",model);if(usage!=null)r.put("usage",usage);if(anthropic)r.put("stop_reason",finish).put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",text.toString())));else r.put("choices",new JSONArray().put(new JSONObject().put("finish_reason",finish).put("message",new JSONObject().put("content",text.toString()).put("reasoning_content",reason.toString()))));return r;}
 static JSONObject read(InputStream input,boolean anthropic,ApiClient client)throws Exception{
  StreamResponse stream=new StreamResponse();BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));String line;StringBuilder event=new StringBuilder();long chars=0;
  while((line=r.readLine())!=null){if(client.cancelled)throw new InterruptedIOException("已取消");chars+=line.length();if(chars>32*1024*1024)throw new IOException("流式响应超出限制");if(line.isEmpty()){stream.event(event.toString(),anthropic,client.progress);event.setLength(0);if(stream.ended)break;}else if(line.startsWith("data:")){if(event.length()>0)event.append('\n');String data=line.substring(5);event.append(data.startsWith(" ")?data.substring(1):data);}}
  if(event.length()>0)stream.event(event.toString(),anthropic,client.progress);return stream.result(anthropic);
 }
}
