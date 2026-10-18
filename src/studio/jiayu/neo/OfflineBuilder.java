package studio.jiayu.neo;
import android.content.Context;
import android.graphics.*;
import android.security.keystore.*;
import org.json.*;
import com.android.apksig.ApkSigner;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;
import javax.security.auth.x500.X500Principal;

/** Offline packaging of Web projects using the bundled, precompiled runtime. */
final class OfflineBuilder {
 static JSONObject build(Context c,JSONObject p,int number)throws Exception{
  String id=p.getString("id");if(!id.matches("[a-zA-Z0-9_-]{1,100}"))throw new Exception("项目 ID 不适合打包");String suffix=id.replaceAll("[^a-zA-Z0-9]","").toLowerCase(Locale.ROOT);if(suffix.length()<20)suffix=UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");String pkg="studio.jiayu.neoapp.p"+suffix.substring(0,20),version="0.1."+(number-1);
  File dir=new File(c.getFilesDir(),"builds");dir.mkdirs();File unsigned=new File(dir,id+"-unsigned.apk"),output=new File(dir,id+"-"+number+".apk");
  JSONObject files=p.getJSONObject("files");if(files.has("neo-matrix.json"))studio.jiayu.matrix.MatrixSpec.parse(files.getString("neo-matrix.json"));if(!files.has("index.html"))throw new Exception("项目缺少 index.html");
  try(ZipInputStream in=new ZipInputStream(c.getAssets().open("runtime-template.apk"));ZipOutputStream out=new AlignedZip(new Counter(new FileOutputStream(unsigned)))){
   ZipEntry entry;while((entry=in.getNextEntry())!=null){String n=entry.getName();if(n.startsWith("META-INF/")||entry.isDirectory())continue;byte[] b=MainActivity.readLimited(in,4*1024*1024);if(n.equals("AndroidManifest.xml"))b=manifest(b,pkg,p.optString("name","Neo App"),version,number);if(n.equals("res/drawable/icon.png"))b=icon(p.optString("name","Neo"));put(out,n,b);}
   for(Iterator<String> it=files.keys();it.hasNext();){String n=it.next();if(!n.matches("[\\p{L}\\p{N}_-][\\p{L}\\p{N} ._-]{0,99}")||n.contains("..")||n.equals("neo-runtime.js"))throw new Exception("项目文件名不适合打包："+n);put(out,"assets/project/"+n,files.getString(n).getBytes(StandardCharsets.UTF_8));}
   put(out,"assets/project.json",new JSONObject().put("id",id).put("name",p.optString("name")).put("version",version).toString().getBytes(StandardCharsets.UTF_8));
  }
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="neo-offline-apk-signing";
  if(!ks.containsAlias(alias)){KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");g.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).setCertificateSubject(new X500Principal("CN=Neo Local Developer")).setCertificateSerialNumber(java.math.BigInteger.ONE).setCertificateNotBefore(new Date(0)).setCertificateNotAfter(new Date(4102444800000L)).build());g.generateKeyPair();}
  PrivateKey key=(PrivateKey)ks.getKey(alias,null);X509Certificate certificate=(X509Certificate)ks.getCertificate(alias);
  ApkSigner.SignerConfig signer=new ApkSigner.SignerConfig.Builder("neo-local",key,Collections.singletonList(certificate)).build();
  try{new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(unsigned).setOutputApk(output).setMinSdkVersion(30).setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false).build().sign();}finally{unsigned.delete();}
  MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(output)){byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
  return new JSONObject().put("number",number).put("version",version).put("package",pkg).put("file",output.getName()).put("bytes",output.length()).put("sha256",hash.toString()).put("time",System.currentTimeMillis()).put("route","offline-device");
 }
 static final class Counter extends FilterOutputStream {long count;Counter(OutputStream o){super(o);}public void write(int b)throws IOException{out.write(b);count++;}public void write(byte[]b,int off,int len)throws IOException{out.write(b,off,len);count+=len;}}
 static final class AlignedZip extends ZipOutputStream {final Counter counter;AlignedZip(Counter c){super(c);counter=c;}}
 static void put(ZipOutputStream o,String n,byte[] b)throws Exception{ZipEntry e=new ZipEntry(n);e.setTime(315532800000L);e.setMethod(ZipEntry.STORED);e.setSize(b.length);e.setCompressedSize(b.length);CRC32 crc=new CRC32();crc.update(b);e.setCrc(crc.getValue());long offset=((AlignedZip)o).counter.count+30+n.getBytes(StandardCharsets.UTF_8).length+4;int pad=(int)((4-offset%4)%4);byte[]extra=new byte[4+pad];extra[0]=(byte)0xfe;extra[1]=(byte)0xca;extra[2]=(byte)pad;e.setExtra(extra);o.putNextEntry(e);o.write(b);o.closeEntry();}
 static byte[] icon(String name)throws Exception{Bitmap b=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);p.setShader(new LinearGradient(0,0,192,192,new int[]{0xffe6edf2,0xff465660,0xffb6c7cc},null,Shader.TileMode.CLAMP));c.drawRoundRect(0,0,192,192,40,40,p);p.setShader(new LinearGradient(0,15,0,177,new int[]{0xff175f58,0xff072c31,0xff147b70},null,Shader.TileMode.CLAMP));c.drawRoundRect(13,13,179,179,30,30,p);p.setShader(null);p.setColor(0x50ffffff);c.drawRoundRect(20,20,172,90,23,23,p);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(68);String text=name.replace("Neo","").trim();if(text.isEmpty())text="N";c.drawText(text.substring(0,Math.min(2,text.length())),96,124,p);ByteArrayOutputStream o=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,o);b.recycle();return o.toByteArray();}
 static int i(byte[]b,int n){return ByteBuffer.wrap(b,n,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
 static int h(byte[]b,int n){return ByteBuffer.wrap(b,n,2).order(ByteOrder.LITTLE_ENDIAN).getShort()&65535;}
 static void putI(byte[]b,int n,int v){ByteBuffer.wrap(b,n,4).order(ByteOrder.LITTLE_ENDIAN).putInt(v);}
 static void le(ByteArrayOutputStream o,int v){o.write(v&255);o.write(v>>>8&255);o.write(v>>>16&255);o.write(v>>>24&255);}
 static int[] len8(byte[]b,int n){int x=b[n++]&255;return (x&128)==0?new int[]{x,n}:new int[]{((x&127)<<8)|(b[n++]&255),n};}
 static int[] len16(byte[]b,int n){int x=h(b,n);n+=2;return(x&32768)==0?new int[]{x,n}:new int[]{((x&32767)<<16)|h(b,n),n+2};}
 static void length8(ByteArrayOutputStream o,int n)throws Exception{if(n>32767)throw new Exception("Manifest 字符串过长");if(n>127)o.write((n>>8)|128);o.write(n&255);}
 static byte[] manifest(byte[]src,String pkg,String name,String version,int number)throws Exception{
  if(h(src,0)!=3)throw new Exception("模板 Manifest 无效");ByteArrayOutputStream chunks=new ByteArrayOutputStream();List<String> names=new ArrayList<>();
  for(int at=h(src,2);at<src.length;){int type=h(src,at),size=i(src,at+4);if(size<8||at+size>src.length)throw new Exception("模板块长度无效");byte[]chunk=Arrays.copyOfRange(src,at,at+size);
   if(type==1){int count=i(chunk,8),styleCount=i(chunk,12),flags=i(chunk,16),start=i(chunk,20),header=h(chunk,2);if(styleCount!=0)throw new Exception("不支持带样式的模板字符串池");ByteArrayOutputStream data=new ByteArrayOutputStream();int[] offsets=new int[count];
    for(int x=0;x<count;x++){int pos=start+i(chunk,header+4*x);String s;if((flags&256)!=0){int[]a=len8(chunk,pos),b=len8(chunk,a[1]);s=new String(chunk,b[1],b[0],StandardCharsets.UTF_8);}else{int[]a=len16(chunk,pos);s=new String(chunk,a[1],a[0]*2,StandardCharsets.UTF_16LE);}names.add(s);if(s.equals("studio.jiayu.template"))s=pkg;if(s.equals("NEO_TEMPLATE_TITLE"))s=name;if(s.equals("0.1.TEMPLATE"))s=version;offsets[x]=data.size();byte[]u=s.getBytes(StandardCharsets.UTF_8);length8(data,s.length());length8(data,u.length);data.write(u);data.write(0);}
    while(data.size()%4!=0)data.write(0);ByteArrayOutputStream pool=new ByteArrayOutputStream();le(pool,0x001c0001);le(pool,28+count*4+data.size());le(pool,count);le(pool,0);le(pool,256);le(pool,28+count*4);le(pool,0);for(int off:offsets)le(pool,off);pool.write(data.toByteArray());chunk=pool.toByteArray();
   }else if(type==0x0102){int start=16+h(chunk,24),width=h(chunk,26),count=h(chunk,28);for(int x=0;x<count;x++){int pos=start+x*width;int index=i(chunk,pos+4);if(index>=0&&index<names.size()&&names.get(index).equals("versionCode"))putI(chunk,pos+16,number);}}
   chunks.write(chunk);at+=size;
  }
  ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(Arrays.copyOf(src,8));out.write(chunks.toByteArray());byte[]result=out.toByteArray();putI(result,4,result.length);return result;
 }
}
