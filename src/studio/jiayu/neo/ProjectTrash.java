package studio.jiayu.neo;
import org.json.*;
/** Pure state transitions; caller atomically persists before announcing success. */
final class ProjectTrash {
 static JSONArray trash(JSONObject s){JSONArray a=s.optJSONArray("projectTrash");return a==null?new JSONArray():a;}
 static int index(JSONArray a,String id,boolean bin)throws Exception{for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);if(bin)p=p.getJSONObject("project");if(id.equals(p.optString("id")))return i;}return -1;}
 static JSONObject apply(JSONObject original,String action,String id,long now)throws Exception{
  JSONObject s=new JSONObject(original.toString());JSONArray projects=s.getJSONArray("projects"),bin=trash(s);s.put("projectTrash",bin);int live=index(projects,id,false),deleted=index(bin,id,true);
  if(action.equals("trash")){if(live<0||deleted>=0)throw new Exception("项目已移动或不存在，请刷新列表");JSONObject p=projects.getJSONObject(live);bin.put(new JSONObject().put("project",p).put("deletedAt",now));projects.remove(live);}
  else if(action.equals("restore")){if(deleted<0)throw new Exception("最近删除中没有此项目");if(live>=0)throw new Exception("已有相同ID的项目，未覆盖现有内容");projects.put(bin.getJSONObject(deleted).getJSONObject("project"));bin.remove(deleted);}
  else if(action.equals("purge")){if(deleted<0)throw new Exception("最近删除中没有此项目");if(live>=0)throw new Exception("项目ID冲突，未删除任何内容");bin.remove(deleted);}
  else throw new Exception("未知项目操作");return s;
 }
}
