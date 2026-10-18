package studio.jiayu.neo;
import org.json.*;
import java.io.*;
/** Debug-only fixture tests. Never mutates the user's workspace. */
final class ProjectTrashTest {
 static JSONArray checks;interface Attempt{void run()throws Exception;}
 static void ok(boolean x,String n)throws Exception{if(!x)throw new Exception(n);checks.put(n);}
 static void reject(String n,Attempt f)throws Exception{boolean fail=false;try{f.run();}catch(Exception e){fail=true;}ok(fail,n);}
 static void run(MainActivity a){JSONObject report=new JSONObject();checks=new JSONArray();try{
  JSONObject p=new JSONObject("{id:'fixture',name:'测试 <script>',files:{'index.html':'keep'},tasks:[{id:'t'}],history:[{text:'history'}],snapshots:[{files:{x:'y'}}],builds:[{file:'fixture-1.apk'}],studioUrl:'https://studio.stepx.com/project/fixture'}");
  JSONObject base=new JSONObject().put("projects",new JSONArray().put(p).put(new JSONObject().put("id","other").put("files",new JSONObject()))).put("usage",new JSONArray().put("keep token")).put("profiles",new JSONArray().put("keep profile")).put("chats",new JSONArray().put("keep conversation"));String before=base.toString();
  JSONObject moved=ProjectTrash.apply(base,"trash","fixture",123);ok(base.toString().equals(before),"transition leaves input unchanged");ok(moved.getJSONArray("projects").length()==1,"only target removed");ok(ProjectTrash.trash(moved).getJSONObject(0).getLong("deletedAt")==123,"deleted timestamp persisted");ok(ProjectTrash.trash(moved).getJSONObject(0).getJSONObject("project").toString().equals(p.toString()),"files tasks history snapshots builds and link preserved");
  for(String k:new String[]{"usage","profiles","chats"})ok(moved.getJSONArray(k).toString().equals(base.getJSONArray(k).toString()),k+" untouched");
  JSONObject restored=ProjectTrash.apply(new JSONObject(moved.toString()),"restore","fixture",124);ok(restored.getJSONArray("projects").getJSONObject(1).toString().equals(p.toString()),"restore after serialization preserves complete project");ok(ProjectTrash.trash(restored).length()==0,"restore clears trash entry");
  reject("duplicate delete rejected",()->ProjectTrash.apply(moved,"trash","fixture",0));reject("unknown target rejected",()->ProjectTrash.apply(base,"trash","missing",0));reject("cannot purge live project",()->ProjectTrash.apply(base,"purge","fixture",0));
  JSONObject conflict=new JSONObject(moved.toString());conflict.getJSONArray("projects").put(p);reject("restore refuses ID collision",()->ProjectTrash.apply(conflict,"restore","fixture",0));reject("purge refuses ID collision",()->ProjectTrash.apply(conflict,"purge","fixture",0));
  JSONObject purged=ProjectTrash.apply(moved,"purge","fixture",0);ok(ProjectTrash.trash(purged).length()==0&&purged.getJSONArray("projects").length()==1,"purge removes only deleted project");ok(purged.getJSONArray("usage").toString().equals(base.getJSONArray("usage").toString()),"purge retains accounting");report.put("result","PASS");
 }catch(Exception e){try{report.put("result","FAIL").put("error",e.toString());}catch(Exception ignored){}}try{report.put("count",checks.length()).put("checks",checks);try(FileOutputStream out=new FileOutputStream(new File(a.getFilesDir(),"trash-selftest.json"))){out.write(report.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}}
}
