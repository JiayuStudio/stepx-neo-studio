package studio.jiayu.neo;
import org.json.*;
/** Headless Android-runtime checks; no activity, account or network is accessed. */
public final class CloudRelayTest {
 interface Work{void run()throws Exception;}
 static void reject(Work w)throws Exception{try{w.run();}catch(Exception expected){return;}throw new Exception("Expected rejection");}
 static JSONObject project()throws Exception{return new JSONObject().put("id","relay-fixture").put("files",new JSONObject().put("index.html","<html><body>fixture</body></html>")).put("binaryFiles",new JSONObject().put("hero.png","AQID"));}
 static void verified(JSONObject p,String id)throws Exception{p.put("cloud",new JSONObject().put("id",id).put("base",CloudFiles.hashes(CloudFiles.entries(p))));}
 public static void main(String[] args)throws Exception{
  JSONObject p=project();CloudRelay r=new CloudRelay(p);r.associate("remote-fixture");verified(p,"remote-fixture");String draft=r.finish(p);if(!draft.contains("当前 Studio 项目")||draft.contains("fixture"))throw new Exception("Unexpected draft content");
  CloudRelay linked=new CloudRelay(p);if(!linked.destinationId.equals("remote-fixture"))throw new Exception("Linked destination lost");linked.finish(p);
  reject(()->linked.associate("other"));p.getJSONObject("files").put("index.html","changed");reject(()->linked.finish(p));
  JSONObject b=project();verified(b,"remote-fixture");CloudRelay binary=new CloudRelay(b);b.getJSONObject("binaryFiles").put("hero.png","BAUG");reject(()->binary.finish(b));
  JSONObject partial=project();verified(partial,"remote-fixture");CloudRelay q=new CloudRelay(partial);partial.getJSONObject("cloud").getJSONObject("base").remove("hero.png");reject(()->q.finish(partial));
  verified(partial,"remote-fixture");partial.getJSONObject("cloud").put("newUpload",true);reject(()->q.finish(partial));partial.getJSONObject("cloud").remove("newUpload");partial.getJSONObject("cloud").put("id","other");reject(()->q.finish(partial));
  JSONObject empty=new JSONObject().put("id","empty").put("files",new JSONObject());reject(()->new CloudRelay(empty));
  System.out.println("CLOUD_RELAY_PASS: existing association, new association, unchanged upload, text conflict, binary conflict, partial verification, pending upload, wrong target, empty project");
 }
}
