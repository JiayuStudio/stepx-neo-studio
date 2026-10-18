package studio.jiayu.neo;
import java.net.URI;
/** Official DeepSeek model limits; context includes input and generated tokens. */
final class ModelLimits {
 static boolean deepSeek(String endpoint,String model){try{return "https".equalsIgnoreCase(new URI(endpoint).getScheme())&&"api.deepseek.com".equalsIgnoreCase(new URI(endpoint).getHost())&&java.util.Arrays.asList("deepseek-flash","deepseek-v4-flash","deepseek-v4-pro","deepseek-v4-flash-vision-exp").contains(model);}catch(Exception e){return false;}}
 static int context(String endpoint,String model){return deepSeek(endpoint,model)?1048576:0;}
 static int output(String endpoint,String model){return deepSeek(endpoint,model)?393216:32768;}
 static int transportChars(String endpoint,String model){return deepSeek(endpoint,model)?8*1048576:95000;}
}
