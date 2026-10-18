package studio.jiayu.neo;
import org.json.*;
import java.util.*;
/** Pure, atomic project-file transitions, bound to an exact request baseline. */
final class ProjectEdits {
 static boolean same(JSONObject a,JSONObject b)throws Exception{if(a.length()!=b.length())return false;Iterator<String> it=a.keys();while(it.hasNext()){String k=it.next();if(!b.has(k)||!Objects.equals(a.get(k),b.get(k)))return false;}return true;}
 static JSONObject parse(String text)throws Exception{
  String s=text.trim();if(s.startsWith("```")){int start=s.indexOf('\n'),end=s.lastIndexOf("```");if(start<0||end<=start||!s.substring(end+3).trim().isEmpty())throw new Exception("文件回复格式不完整");s=s.substring(start+1,end).trim();}
  JSONObject o=new JSONObject(s);if(!(o.opt("files") instanceof JSONObject))throw new Exception("模型没有返回 files 文件对象");return o.getJSONObject("files");
 }
 static void validate(JSONObject files)throws Exception{if(files.length()>40||files.toString().length()>450000)throw new Exception("项目上限为 40 个文件、45 万字符");if(!(files.opt("index.html") instanceof String))throw new Exception("必须保留 index.html 入口");Iterator<String> it=files.keys();while(it.hasNext()){String n=it.next();if(!n.matches("[\\p{L}\\p{N}_-][\\p{L}\\p{N} ._-]{0,99}")||n.contains("..")||!n.matches("(?i).+\\.(html|css|js|json|md|txt|svg)")||!(files.get(n) instanceof String))throw new Exception("无效的根目录文本文件："+n);}}
 static JSONObject apply(JSONObject p,JSONObject base,JSONObject changes,String source)throws Exception{
  if(!same(p.getJSONObject("files"),base))throw new Exception("文件已变化，本轮建议未写入；请基于最新文件重新发送");
  JSONObject next=new JSONObject(base.toString());Iterator<String> it=changes.keys();while(it.hasNext()){String n=it.next();if(changes.isNull(n)){if(!next.has(n))throw new Exception("不能删除不存在的文件："+n);next.remove(n);}else{if(!(changes.get(n) instanceof String))throw new Exception("文件内容必须是文本");next.put(n,changes.getString(n));}}if(next.has("neo-matrix.json"))studio.jiayu.matrix.MatrixSpec.parse(next.getString("neo-matrix.json"));if(p.optBoolean("cloudSource")){JSONObject temp=new JSONObject(p.toString()).put("files",next);CloudFiles.entries(temp);}else validate(next);
  JSONObject out=new JSONObject(p.toString());if(same(next,base))return out;
  JSONArray snaps=out.optJSONArray("snapshots");if(snaps==null)snaps=new JSONArray();JSONArray kept=new JSONArray();for(int i=Math.max(0,snaps.length()-4);i<snaps.length();i++)kept.put(snaps.get(i));kept.put(new JSONObject().put("time",System.currentTimeMillis()).put("files",new JSONObject(base.toString())));out.put("snapshots",kept).put("files",next).put("updated",System.currentTimeMillis());
  JSONArray h=out.optJSONArray("history");if(h==null)h=new JSONArray();h.put(new JSONObject().put("time",System.currentTimeMillis()).put("type","apply").put("text",source+" · "+changes.length()+" 个文件；运行验收待完成。"));out.put("history",h);return out;
 }
 static String sourceInstructions(){return "你正在修改云端项目的本地源码副本。返回严格 JSON：{\"summary\":\"说明\",\"files\":{\"相对路径\":\"完整内容\"}}。只列需要修改的文本文件；删除用null。保留其他文件。允许子目录和TSX等源码，不可直接修改凭据、签名材料或二进制资源；新图片使用generate_image工具保存后引用。不执行shell、不假装完成构建；可在工作台本地运行支持的前端工程；发布构建仍可上传Studio。保持用户项目品牌，不擅自添加模型厂商联名或接入成功宣传。";}
 static String instructions(){return "你正在直接修改用户选定的本地 HTML/CSS/JS 项目。只返回一个严格 JSON 对象：{\"summary\":\"说明\",\"files\":{\"文件名\":\"完整内容\"}}。只列出需要新增或修改的根目录文本文件；删除文件用 null，但必须保留 index.html。支持 html/css/js/json/md/txt/svg，最多40个文件、45万字符。不支持子目录、npm、shell或Kotlin/Gradle。不得省略代码或在最终交付中留下占位符；绘图工具可使用随后替换的临时图片路径占位符。下方最新文件快照是唯一修改基线，保持未要求修改的功能。游戏要保留可操作的玩法、结束与重试；不要声称已经运行或验收。";}
}
