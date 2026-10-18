package studio.jiayu.matrix;
import org.json.*;
/** Portable animation contract; no scripts, credentials or arbitrary tool names. */
public final class MatrixSpec {
 public static JSONObject parse(String text)throws Exception{
  if(text.length()>350000)throw new Exception("点阵文件超过 350 KB");
  JSONObject o=new JSONObject(text);if(!"stepx-matrix/1".equals(o.optString("schema"))||o.optInt("width")!=17||o.optInt("height")!=23)throw new Exception("点阵必须为 stepx-matrix/1，17 × 23");
  integer(o.get("width"),17,17,"宽度");integer(o.get("height"),23,23,"高度");if(!(o.opt("loop") instanceof Boolean))throw new Exception("loop 必须是布尔值");
  JSONArray fs=o.getJSONArray("frames");if(fs.length()<1||fs.length()>120)throw new Exception("动画需要 1–120 帧");
  for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);integer(f.get("durationMs"),200,60000,"帧时长");JSONArray a=f.getJSONArray("pixels");if(a.length()!=391)throw new Exception("第 "+(i+1)+" 帧必须有 391 个像素");for(int j=0;j<391;j++)integer(a.get(j),0,255,"亮度");}
  return o;
 }
 static void integer(Object o,int min,int max,String label)throws Exception{if(!(o instanceof Number)||((Number)o).doubleValue()!=((Number)o).intValue()||((Number)o).intValue()<min||((Number)o).intValue()>max)throw new Exception(label+"必须为 "+min+"–"+max+" 的整数");}
}
