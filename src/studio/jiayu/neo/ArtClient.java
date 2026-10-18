package studio.jiayu.neo;
import org.json.*;
import android.graphics.*;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Native image transport. Credentials are used only on the configured API origin. */
final class ArtClient {
 volatile boolean cancelled; volatile HttpURLConnection connection;
 final boolean debug; ArtClient(boolean debug){this.debug=debug;}
 void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
 void check()throws Exception{if(cancelled)throw new InterruptedIOException("已停止等待；远端任务可能仍在运行，请恢复原任务，不要重复生成");}
 static URI endpoint(String value,boolean debug)throws Exception{
  URI u;try{u=new URI(value);}catch(Exception e){throw new Exception("绘图接口地址无效");}
  if(u.getHost()==null||u.getRawUserInfo()!=null||u.getFragment()!=null||u.getRawQuery()!=null||!("https".equals(u.getScheme())||(debug&&"http".equals(u.getScheme())&&u.getHost().equals("127.0.0.1"))))throw new Exception("绘图接口必须使用不含凭据、查询参数和片段的 HTTPS 地址");return u;
 }
 static String base(JSONObject p){String b=p.optString("endpoint").replaceAll("/+$","");return b.endsWith("/v1")?b:b+"/v1";}
 static String createUrl(JSONObject p){return p.optString("protocol").equals("custom")?p.optString("endpoint"):base(p)+(p.optString("protocol").equals("omnia")?"/media/tasks":"/images/generations");}
 static String pollUrl(JSONObject p,String id)throws Exception{
  if(!id.matches("[A-Za-z0-9_-]{1,256}"))throw new Exception("任务 ID 无效");
  String url=p.optString("protocol").equals("omnia")?base(p)+"/media/tasks/"+id:p.optString("pollUrl").replace("{{taskId}}",id);
  URI u=new URI(url),origin=new URI(createUrl(p));if(!Objects.equals(u.getScheme(),origin.getScheme())||!Objects.equals(u.getHost(),origin.getHost())||u.getPort()!=origin.getPort())throw new Exception("任务查询必须使用同一 API 服务，未发送密钥");return url;
 }
 static Object at(JSONObject o,String path){Object v=o;if(path==null||path.isEmpty())return null;try{for(String part:path.replace("[",".").replace("]","").split("\\.")){if(part.isEmpty())continue;v=v instanceof JSONArray?((JSONArray)v).get(Integer.parseInt(part)):((JSONObject)v).get(part);}return v==JSONObject.NULL?null:v;}catch(Exception e){return null;}}
 static String value(JSONObject o,String path){Object v=at(o,path);return v instanceof String?(String)v:"";}
 static Object template(Object input,Map<String,String> vars)throws Exception{
  if(input instanceof JSONObject){JSONObject out=new JSONObject();Iterator<String> it=((JSONObject)input).keys();while(it.hasNext()){String k=it.next();out.put(k,template(((JSONObject)input).get(k),vars));}return out;}
  if(input instanceof JSONArray){JSONArray out=new JSONArray();for(int i=0;i<((JSONArray)input).length();i++)out.put(template(((JSONArray)input).get(i),vars));return out;}
  if(input instanceof String){String s=(String)input;java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\{(prompt|model|size|quality)\\}\\}").matcher(s);StringBuffer b=new StringBuffer();while(m.find())m.appendReplacement(b,java.util.regex.Matcher.quoteReplacement(vars.get(m.group(1))));m.appendTail(b);return b.toString();}return input;
 }
 static JSONObject body(JSONObject p,String prompt)throws Exception{
  if(p.optString("protocol").equals("custom")){Map<String,String> vars=new HashMap<>();vars.put("prompt",prompt);for(String k:new String[]{"model","size","quality"})vars.put(k,p.optString(k));return (JSONObject)template(new JSONObject(p.getString("template")),vars);}
  JSONObject parameters=new JSONObject().put("n",1);if(!p.optString("size").isEmpty())parameters.put("size",p.getString("size"));if(!p.optString("quality").isEmpty())parameters.put("quality",p.getString("quality"));
  if(p.optString("protocol").equals("omnia"))return new JSONObject().put("model",p.getString("model")).put("type","image").put("operation","generate").put("input",new JSONObject().put("prompt",prompt)).put("parameters",parameters);
  return parameters.put("model",p.getString("model")).put("prompt",prompt);
 }
 static void validate(JSONObject p,boolean debug)throws Exception{
  if(!p.optString("id").matches("[A-Za-z0-9_-]{1,80}"))throw new Exception("绘图配置 ID 无效");
  if(!Arrays.asList("omnia","images","custom").contains(p.optString("protocol")))throw new Exception("请选择绘图协议");endpoint(createUrl(p),debug);
  if(p.optString("model").length()>256||p.optString("name").length()>60)throw new Exception("绘图配置内容过长");
  String h=p.optString("keyHeader","Authorization"),prefix=p.optString("keyPrefix","Bearer ");if(!h.matches("[A-Za-z0-9-]{1,60}")||Arrays.asList("host","cookie","content-length","content-type","connection").contains(h.toLowerCase(Locale.ROOT))||prefix.length()>60||prefix.contains("\r")||prefix.contains("\n"))throw new Exception("密钥请求头无效");
  if(p.optString("protocol").equals("custom")){if(p.optString("template").length()>12000)throw new Exception("请求模板过长");new JSONObject(p.getString("template"));if(!p.optString("pollUrl").isEmpty())endpoint(pollUrl(p,"test"),debug);}
 }
 byte[] read(InputStream in,int max)throws Exception{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=input.read(buf))!=-1){check();if(out.size()+n>max)throw new Exception("绘图响应超过大小限制");out.write(buf,0,n);}return out.toByteArray();}}
 JSONObject json(JSONObject p,String key,String url,JSONObject body,String idem)throws Exception{
  check();endpoint(url,debug);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();connection=c;try{
   c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(90000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","StepX-Neo-Studio/1.2.3");
   if(!key.isEmpty())c.setRequestProperty(p.optString("keyHeader","Authorization"),p.optString("keyPrefix","Bearer ")+key);
   if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");if(!idem.isEmpty())c.setRequestProperty("Idempotency-Key",idem);byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
   int status=c.getResponseCode();if(status<200||status>=300)throw new Exception("绘图 API 返回 HTTP "+status+"；请核对接口、模型权限和余额。请求可能已提交，请先查询原任务");
   JSONObject out;try{out=new JSONObject(new String(read(c.getInputStream(),24*1024*1024),StandardCharsets.UTF_8));}catch(JSONException e){throw new Exception("绘图服务返回了非 JSON 数据");}
   if(out.optBoolean("success",true)==false)throw new Exception("绘图服务拒绝了请求，请在服务商控制台查看状态");return out;
  }finally{c.disconnect();connection=null;}
 }
 byte[] download(String url)throws Exception{
  for(int redirect=0;redirect<4;redirect++){check();URI u;try{u=new URI(url);}catch(Exception e){throw new Exception("图片地址无效");}
   if(u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||!("https".equals(u.getScheme())||(debug&&u.getHost().equals("127.0.0.1")&&"http".equals(u.getScheme()))))throw new Exception("只下载 HTTPS 图片");
   if(!(debug&&u.getHost().equals("127.0.0.1")))for(InetAddress ip:InetAddress.getAllByName(u.getHost()))if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())throw new Exception("图片下载不能访问本地网络地址");
   HttpURLConnection c=(HttpURLConnection)u.toURL().openConnection();connection=c;try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(60000);int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new Exception("图片跳转地址无效");url=u.resolve(next).toString();continue;}if(status!=200)throw new Exception("图片下载失败 HTTP "+status+"；可恢复任务刷新下载地址");return read(c.getInputStream(),16*1024*1024);}finally{c.disconnect();connection=null;}
  }throw new Exception("图片地址跳转过多");
 }
 static JSONObject usage(JSONObject response)throws Exception{
  JSONObject copy=new JSONObject(response.toString()),u=copy.optJSONObject("usage");if(u!=null){if(!u.has("prompt_tokens")&&u.has("input_tokens"))u.put("prompt_tokens",u.get("input_tokens"));if(!u.has("completion_tokens")&&u.has("output_tokens"))u.put("completion_tokens",u.get("output_tokens"));if(!u.has("prompt_tokens_details")&&u.has("input_tokens_details"))u.put("prompt_tokens_details",u.get("input_tokens_details"));}return Usage.parse(copy,"compatible");
 }
 static byte[] normalize(byte[] raw)throws Exception{
  BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,b);
  if(b.outWidth<1||b.outHeight<1||b.outWidth>8192||b.outHeight>8192||(long)b.outWidth*b.outHeight>33554432)throw new Exception("返回内容不是可用的 PNG/JPEG/WebP 图片，或尺寸过大");
  BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;while(Math.max(b.outWidth,b.outHeight)/opts.inSampleSize>2048)opts.inSampleSize*=2;Bitmap bitmap=BitmapFactory.decodeByteArray(raw,0,raw.length,opts);if(bitmap==null)throw new Exception("图片解码失败");
  try{int edge=1024;while(true){double scale=Math.min(1.0,(double)edge/Math.max(bitmap.getWidth(),bitmap.getHeight()));Bitmap out=scale<1?Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true):bitmap;ByteArrayOutputStream bytes=new ByteArrayOutputStream();out.compress(Bitmap.CompressFormat.PNG,100,bytes);if(out!=bitmap)out.recycle();if(bytes.size()<=1048576)return bytes.toByteArray();edge= (int)(edge*.75);if(edge<128)throw new Exception("图片无法压缩到项目单文件限制");}}finally{bitmap.recycle();}
 }
 byte[] image(JSONObject p,JSONObject result)throws Exception{
  String b64=p.optString("protocol").equals("custom")?value(result,p.optString("b64Path")):value(result,"data.0.b64_json");
  if(!b64.isEmpty()){if(b64.startsWith("data:")){int comma=b64.indexOf(',');if(comma<0)throw new Exception("图片编码无效");b64=b64.substring(comma+1);}try{return normalize(Base64.decode(b64,Base64.DEFAULT));}catch(IllegalArgumentException e){throw new Exception("图片 Base64 编码无效");}}
  String url=p.optString("protocol").equals("custom")?value(result,p.optString("urlPath")):value(result,"data.0.url");
  if(p.optString("protocol").equals("omnia")){for(String path:new String[]{"result.assets.0.url","result.images.0.url","result.0.url","output.0.url","result.url","output.url","output.images.0.url","assets.0.url"}){String candidate=value(result,path);if(!candidate.isEmpty()){url=candidate;break;}}}
  if(url.isEmpty())throw new Exception("响应中没有图片，请核对图片 URL 或 Base64 字段路径");return normalize(download(url));
 }
}
