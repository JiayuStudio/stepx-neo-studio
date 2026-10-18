package studio.jiayu.neo;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.ai.edge.litertlm.*;
final class LocalAiClient extends ApiClient {
 final Context context;volatile Conversation conversation;
 LocalAiClient(Context c){context=c.getApplicationContext();}
 @Override void cancel(){cancelled=true;Conversation c=conversation;if(c!=null)try{c.cancelProcess();}catch(Exception ignored){}}
 @Override JSONObject request(JSONObject p,String ignored,JSONArray messages,String system,boolean debug)throws Exception{
  File file=LocalModels.modelFile(context,p.optString("model"));if(!file.isFile())throw new Exception("本地模型尚未下载，请进入本地模型管理");
  int tokens=Math.max(1024,Math.min(32768,p.optInt("contextTokens",8192))),output=Math.max(64,Math.min(tokens-512,p.optInt("maxTokens",2048)));
  Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);ExperimentalFlags.INSTANCE.setEnableBenchmark(true);ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(p.optBoolean("mtp",true));
  Backend backend=p.optString("backend","gpu").equals("cpu")?new Backend.CPU(4,null):new Backend.GPU();
  File cache=new File(context.getCacheDir(),"local-ai");if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("无法创建模型缓存");
  Engine engine=new Engine(new EngineConfig(file.getAbsolutePath(),backend,null,null,tokens,null,new File(context.getCacheDir(),"local-ai").getAbsolutePath()));boolean initialized=false;
  try{
   if(cancelled)throw new InterruptedIOException("已取消");engine.initialize();initialized=true;if(cancelled)throw new InterruptedIOException("已取消");
   List<Message> initial=new ArrayList<>();for(int i=0;i<messages.length()-1;i++){JSONObject m=messages.getJSONObject(i);initial.add(m.optString("role").equals("assistant")?Message.Companion.model(m.optString("content")):Message.Companion.user(m.optString("content")));}
   SamplerConfig sampler=new SamplerConfig(p.optInt("topK",64),p.optDouble("topP",0.95),p.optDouble("temperature",1.0),0);
   ConversationConfig config=new ConversationConfig(Contents.Companion.of(system),initial,Collections.emptyList(),sampler,false,null,Collections.emptyMap(),null,false,output,new ThinkingConfig(p.optBoolean("localThinking",false),p.optInt("thinkingBudget",512)));
   Conversation c=engine.createConversation(config);conversation=c;try{
    CountDownLatch done=new CountDownLatch(1);StringBuilder text=new StringBuilder(),reasoning=new StringBuilder();Throwable[] failure={null};
    c.sendMessageAsync(messages.getJSONObject(messages.length()-1).optString("content"),new MessageCallback(){
     public void onMessage(Message message){text.append(message.toString());String thought=message.getChannels().get("thought");if(thought!=null)reasoning.append(thought);if(progress!=null)progress.update(text.toString(),reasoning.toString());}
     public void onDone(){done.countDown();}
     public void onError(Throwable error){failure[0]=error;done.countDown();}
    });
    while(!done.await(250,TimeUnit.MILLISECONDS)){if(cancelled)c.cancelProcess();}
    if(cancelled)throw new InterruptedIOException("本地生成已取消");if(failure[0]!=null)throw new Exception("本地推理失败："+failure[0].getMessage());
    JSONObject usage=new JSONObject().put("known",false),bench=new JSONObject();int decoded=-1;
    try{BenchmarkInfo b=c.getBenchmarkInfo();decoded=b.getLastDecodeTokenCount();int input=b.getLastPrefillTokenCount();if(input>=0&&decoded>=0)usage.put("known",true).put("input",input).put("output",decoded).put("total",input+decoded);bench.put("prefillTokensPerSecond",b.getLastPrefillTokensPerSecond()).put("decodeTokensPerSecond",b.getLastDecodeTokensPerSecond()).put("firstTokenSeconds",b.getTimeToFirstTokenInSecond());}catch(Exception ignoredStats){}
    boolean incomplete=decoded>=output||text.length()==0;
    return new JSONObject().put("text",text.toString()).put("reasoning",reasoning.toString()).put("usage",usage).put("model",p.optString("model")).put("incomplete",incomplete).put("benchmark",bench);
   }finally{conversation=null;c.close();}
  }catch(OutOfMemoryError e){throw new Exception("本地内存不足，请降低上下文或选择更小的模型");}finally{if(initialized)engine.close();}
 }
}
