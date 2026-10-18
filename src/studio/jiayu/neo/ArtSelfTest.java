package studio.jiayu.neo;
import org.json.*;
import java.io.*;
import java.util.*;
import android.graphics.*;
final class ArtSelfTest {
 interface Work{void run()throws Exception;}
 static void rejected(Work w)throws Exception{try{w.run();}catch(Exception expected){return;}throw new Exception("Expected rejection");}
 static void run(MainActivity a){JSONObject out=new JSONObject();try{
  rejected(()->ArtClient.endpoint("http://api.example.com/v1",false));rejected(()->ArtClient.endpoint("https://secret@example.com/v1",false));rejected(()->ArtClient.endpoint("https://example.com/v1?key=value",false));ArtClient.endpoint("https://example.com/v1",false);
  JSONObject profile=new JSONObject().put("endpoint","https://example.com/generate").put("protocol","custom").put("pollUrl","https://another.example/tasks/{{taskId}}");rejected(()->ArtClient.pollUrl(profile,"test"));
  HashMap<String,String> vars=new HashMap<>();vars.put("prompt","quote \" slash \\ newline\n{{model}}");vars.put("model","should-not-replace");vars.put("size","");vars.put("quality","");JSONObject result=(JSONObject)ArtClient.template(new JSONObject().put("nested",new JSONArray().put(new JSONObject().put("p","{{prompt}}"))),vars);if(!result.getJSONArray("nested").getJSONObject(0).getString("p").equals(vars.get("prompt")))throw new Exception("Template corrupt");
  rejected(()->ArtClient.normalize("not-an-image".getBytes("UTF-8")));Bitmap bitmap=Bitmap.createBitmap(1500,900,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0x80ff8800);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();byte[] png=ArtClient.normalize(bytes.toByteArray());Bitmap image=BitmapFactory.decodeByteArray(png,0,png.length);if(image.getWidth()!=1024||image.getHeight()>1024||!image.hasAlpha())throw new Exception("Normalized image mismatch");image.recycle();
  JSONObject p=new JSONObject().put("files",new JSONObject().put("index.html","<body><img src=\"{{hero}}\"></body>"));rejected(()->ArtManager.path(p,"../escape.png"));rejected(()->ArtManager.path(p,".env"));ArtManager.insert(p,new JSONObject().put("referenceFile","index.html").put("placeholder","{{hero}}"),"public/hero.png");if(!p.getJSONObject("files").getString("index.html").contains("/hero.png"))throw new Exception("Reference missing");
  JSONObject usage=ArtClient.usage(new JSONObject().put("usage",new JSONObject().put("input_tokens",7).put("output_tokens",9)));if(usage.getLong("total")!=16)throw new Exception("Image usage mismatch");out.put("result","PASS").put("checks",11);
 }catch(Exception e){try{out.put("result","FAIL").put("reason",e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}try(FileOutputStream file=new FileOutputStream(new File(a.getFilesDir(),"art-selftest.json"))){file.write(out.toString().getBytes("UTF-8"));}catch(Exception ignored){}}
}
