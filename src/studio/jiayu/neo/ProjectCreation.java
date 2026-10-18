package studio.jiayu.neo;
import org.json.*;
import java.util.*;
import java.util.regex.*;
/** Explicit user requests create a separate project and conversation binding. */
final class ProjectCreation {
 static boolean requested(String text){
  String s=text.trim();if(s.length()>80000||s.startsWith("```")||s.startsWith("\"")||s.startsWith("“")||s.startsWith("「"))return false;
  String first=s.split("[。！？!?\\n]",2)[0];
  if(Pattern.compile("不要|别|无需|不需要|不用|不要再|如何|怎么|能否|是否|例如|示例|假如|如果|don't|do not|how to",Pattern.CASE_INSENSITIVE).matcher(first).find())return false;
  return Pattern.compile("^(?:(?:请|帮我|给我|现在|先|再|麻烦|在本地项目中|在本地|用AI助手|让AI助手)[，,\\s]*)*(?:新建|创建|建立|另建)(?:一个|个|一款)?[^。！？!?\\n]{0,36}?(?:项目|工程|应用|APP|app)(?:[\\s，,：:。]|$|来|并|用|做|写|叫|名|的|支持|包含|实现|可以)").matcher(s).find()
   ||Pattern.compile("^(?:please\\s+)?(?:create|start|make)\\s+(?:me\\s+)?(?:a\\s+)?new\\s+(?:local\\s+)?(?:project|app)\\b",Pattern.CASE_INSENSITIVE).matcher(s).find();
 }
 static JSONObject create(JSONObject state,String chatId,String text)throws Exception{
  if(!requested(text))throw new Exception("请明确说明新建本地项目");JSONObject chat=null;JSONArray chats=state.getJSONArray("chats");for(int i=0;i<chats.length();i++)if(chatId.equals(chats.getJSONObject(i).optString("id")))chat=chats.getJSONObject(i);
  if(chat==null||!chat.optString("projectId").isEmpty()||chat.getJSONArray("messages").length()!=1||!text.equals(chat.getJSONArray("messages").getJSONObject(0).optString("content")))throw new Exception("新项目需要独立的新对话，请重新发送");
  String name="AI 新项目";Matcher quoted=Pattern.compile("[“「\"]([^”」\"\\n]{1,40})[”」\"]").matcher(text);if(quoted.find())name=quoted.group(1).trim();
  else{Matcher m=Pattern.compile("(?:新建|创建|建立|另建)(?:一个|个|一款)?(.{1,24}?)(?:项目|工程|应用|APP|app)").matcher(text);if(m.find()&&!m.group(1).contains("\n"))name=m.group(1).trim();}
  if(name.isEmpty())name="AI 新项目";JSONArray ps=state.getJSONArray("projects");String base=name;int suffix=2;Set<String>names=new HashSet<>();for(int i=0;i<ps.length();i++)names.add(ps.getJSONObject(i).optString("name"));while(names.contains(name))name=base+" "+suffix++;
  JSONObject p=new JSONObject().put("id",UUID.randomUUID().toString()).put("name",name).put("updated",System.currentTimeMillis()).put("files",new JSONObject().put("index.html","<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head><body><main>项目已创建，等待 AI 编写代码。</main></body></html>"));
  ps.put(p);chat.put("projectId",p.getString("id"));return p;
 }
}
