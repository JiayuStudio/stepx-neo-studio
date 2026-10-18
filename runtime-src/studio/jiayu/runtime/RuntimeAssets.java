package studio.jiayu.runtime;
import android.content.res.AssetManager;
import java.io.*;
import java.util.Locale;

/** Serves only files packaged inside this app, retaining binary bytes and nested paths. */
final class RuntimeAssets {
 static boolean path(String p){
  if(p==null||p.isEmpty()||p.length()>240||p.startsWith("/")||p.indexOf('\\')>=0)return false;
  for(int i=0;i<p.length();i++)if(p.charAt(i)<32||p.charAt(i)==127)return false;
  for(String s:p.split("/",-1))if(s.isEmpty()||s.equals(".")||s.equals(".."))return false;
  return true;
 }
 static byte[] read(AssetManager assets,String path)throws IOException{
  if(!path(path))throw new IOException("Invalid asset path");InputStream in;
  try{in=assets.open("project/"+path);}catch(FileNotFoundException e){in=assets.open("project/public/"+path);}
  try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("Asset too large");out.write(b,0,n);}return out.toByteArray();
  }
 }
 static String mime(String path){
  String n=path.toLowerCase(Locale.ROOT);
  if(n.endsWith(".html"))return "text/html";
  if(n.endsWith(".js")||n.endsWith(".mjs"))return "text/javascript";
  if(n.endsWith(".css"))return "text/css";
  if(n.endsWith(".json"))return "application/json";
  if(n.endsWith(".svg"))return "image/svg+xml";
  if(n.endsWith(".png"))return "image/png";
  if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return "image/jpeg";
  if(n.endsWith(".webp"))return "image/webp";
  if(n.endsWith(".gif"))return "image/gif";
  if(n.endsWith(".ico"))return "image/x-icon";
  if(n.endsWith(".woff"))return "font/woff";
  if(n.endsWith(".woff2"))return "font/woff2";
  if(n.endsWith(".ttf"))return "font/ttf";
  if(n.endsWith(".mp3"))return "audio/mpeg";
  if(n.endsWith(".wav"))return "audio/wav";
  if(n.endsWith(".ogg"))return "audio/ogg";
  if(n.endsWith(".mp4"))return "video/mp4";
  if(n.endsWith(".wasm"))return "application/wasm";
  return "application/octet-stream";
 }
}
