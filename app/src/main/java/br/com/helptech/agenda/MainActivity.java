package br.com.helptech.agenda;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.webkit.*;
import android.view.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import android.util.Base64;

public class MainActivity extends Activity {
 WebView web; android.content.SharedPreferences prefs;
 final String PREF="helptech_android_v2";
 @Override public void onCreate(Bundle b){super.onCreate(b);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);prefs=getSharedPreferences(PREF,MODE_PRIVATE);
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=getPackageManager().PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},10);
  web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(true);web.addJavascriptInterface(new Bridge(),"HT");web.setWebViewClient(new WebViewClient());web.loadUrl("file:///android_asset/index.html");
 }
 String secret(String value,boolean encrypt) throws Exception {
  String alias="helptech-local-key";java.security.KeyStore ks=java.security.KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(alias)){javax.crypto.KeyGenerator kg=javax.crypto.KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(alias,3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").build());kg.generateKey();}
  javax.crypto.SecretKey k=(javax.crypto.SecretKey)ks.getKey(alias,null);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
  if(encrypt){c.init(Cipher.ENCRYPT_MODE,k);return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);}
  String[] p=value.split(":",2);c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),"UTF-8");
 }
 String hash(String password,String salt) throws Exception {byte[] s=Base64.decode(salt,Base64.NO_WRAP);PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),s,120000,256);byte[] h=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return Base64.encodeToString(h,Base64.NO_WRAP);}
 String response(String url,String key,String method,String body) throws Exception {
  URL u=new URL(url);if(!"http".equals(u.getProtocol())&&!"https".equals(u.getProtocol()))throw new Exception("Endereço inválido");
  HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestMethod(method);c.setRequestProperty("X-HelpTech-Key",key);c.setRequestProperty("Accept","application/json");
  if("POST".equals(method)){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.getBytes("UTF-8"));}}
  int status=c.getResponseCode();InputStream stream=status<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream buf=new ByteArrayOutputStream();if(stream!=null){byte[] chunk=new byte[4096];int n;while((n=stream.read(chunk))!=-1)buf.write(chunk,0,n);stream.close();}c.disconnect();if(status>=400)throw new Exception("HTTP "+status+": "+buf.toString("UTF-8"));return buf.toString("UTF-8");
 }
 void emit(String event,String json){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.HTevent("+JSONObject.quote(event)+","+json+")",null);});}
 public class Bridge {
  @JavascriptInterface public String state(){JSONObject j=new JSONObject();try{j.put("registered",prefs.contains("passhash"));j.put("configured",prefs.contains("endpoint"));j.put("endpoint",prefs.getString("endpoint","http://100.87.64.24:8765"));j.put("username",prefs.getString("username",""));}catch(Exception e){}return j.toString();}
  @JavascriptInterface public String register(String user,String pass){try{if(prefs.contains("passhash"))return "Usuário já cadastrado";if(user.trim().length()<3||pass.length()<8)return "Usuário mínimo 3 caracteres e senha mínimo 8 caracteres";byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);String s=Base64.encodeToString(salt,Base64.NO_WRAP);prefs.edit().putString("username",user.trim()).putString("salt",s).putString("passhash",hash(pass,s)).apply();return "OK";}catch(Exception e){return "Erro ao cadastrar";}}
  @JavascriptInterface public boolean login(String user,String pass){try{String stored=prefs.getString("passhash","");return prefs.getString("username","").equals(user.trim()) && MessageDigest.isEqual(stored.getBytes("UTF-8"),hash(pass,prefs.getString("salt","")).getBytes("UTF-8"));}catch(Exception e){return false;}}
  @JavascriptInterface public String config(String endpoint,String key){try{URL u=new URL(endpoint);if(!("http".equals(u.getProtocol())||"https".equals(u.getProtocol()))||u.getHost().isEmpty()||key.trim().isEmpty())return "Endereço ou chave inválidos";String base=u.getProtocol()+"://"+u.getAuthority();prefs.edit().putString("endpoint",base).putString("key",secret(key.trim(),true)).apply();return "OK";}catch(Exception e){return "Não foi possível salvar as configurações";}}
  @JavascriptInterface public void sync(String requestId,String method,String data){new Thread(()->{try{String endpoint=prefs.getString("endpoint","");String key=secret(prefs.getString("key",""),false);if(endpoint.isEmpty())throw new Exception("Configure a rede primeiro");String result=response(endpoint+"/api/appointments",key,method,data);emit("sync",new JSONObject().put("requestId",requestId).put("ok",true).put("result",new JSONObject(result)).toString());}catch(Exception e){try{emit("sync",new JSONObject().put("requestId",requestId).put("ok",false).put("error",e.getMessage()).toString());}catch(Exception ignored){}}}).start();}
  @JavascriptInterface public void alarms(String data){try{JSONArray a=new JSONArray(data);AlarmScheduler.schedule(MainActivity.this,a);}catch(Exception ignored){}}
  @JavascriptInterface public void clearAlarms(){AlarmScheduler.clear(MainActivity.this);}
 }
 @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.HTback&&window.HTback()",null);else super.onBackPressed();}
}
