package studio.jiayu.neo;
import org.json.*;
/** Persisted per-project outcomes; restarting never resubmits an uncertain create. */
final class CloudQueue {
 static JSONObject start(JSONArray projects)throws Exception{JSONArray items=new JSONArray();for(int i=0;i<projects.length();i++){JSONObject p=projects.getJSONObject(i);items.put(new JSONObject().put("id",p.getString("id")).put("name",p.optString("name")).put("status","waiting"));}return new JSONObject().put("running",true).put("started",System.currentTimeMillis()).put("items",items);}
 static void mark(JSONObject queue,int index,String status,String message)throws Exception{queue.getJSONArray("items").getJSONObject(index).put("status",status).put("message",message);}
 static void end(JSONObject queue,boolean interrupted)throws Exception{queue.put("running",false).put("finished",System.currentTimeMillis());JSONArray items=queue.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.optString("status").equals("running")||item.optString("status").equals("waiting"))item.put("status",interrupted?"interrupted":"stopped").put("message",interrupted?"应用已重启，请核对后重试":"未继续上传");}}
}
