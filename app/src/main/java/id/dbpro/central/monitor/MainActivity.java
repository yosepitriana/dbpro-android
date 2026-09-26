package id.dbpro.central.monitor;

import android.app.*;
import android.os.*;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.text.method.PasswordTransformationMethod;
import android.text.method.HideReturnsTransformationMethod;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler timer = new Handler(Looper.getMainLooper());
    private LinearLayout page;
    private String token;
    private TextView liveLabel, updatedLabel, serverName;
    private MetricCard cpu, ram, disk, network;
    private LinearLayout serviceList, incidentList;
    private boolean dark;
    private boolean loading;
    private ObjectAnimator refreshAnimator;
    private final Runnable refresh = new Runnable() { public void run() { loadDashboard(); timer.postDelayed(this, 2_000); } };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getPreferences(MODE_PRIVATE).getBoolean("dark", false);
        getWindow().setStatusBarColor(dark?0xFF0B1120:Color.WHITE); getWindow().setNavigationBarColor(dark?0xFF0B1120:0xFFF8FAFC);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        token = getPreferences(MODE_PRIVATE).getString("token", null);
        if (token == null) showLogin(); else biometricUnlockOrDashboard();
    }
    @Override protected void onDestroy() {
        timer.removeCallbacksAndMessages(null);
        if(refreshAnimator!=null) refreshAnimator.cancel();
        super.onDestroy();
    }

    private void biometricUnlockOrDashboard(){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.P){ showDashboard(); return; }
        BiometricManager manager=getSystemService(BiometricManager.class);
        if(manager==null || manager.canAuthenticate()!=BiometricManager.BIOMETRIC_SUCCESS){ showDashboard(); return; }
        new BiometricPrompt.Builder(this)
            .setTitle("DBpro Central")
            .setSubtitle("Gunakan sidik jari untuk masuk")
            .setNegativeButton("Gunakan password",getMainExecutor(),(dialog,which)->showLogin())
            .build()
            .authenticate(new android.os.CancellationSignal(),getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){ showDashboard(); }
                @Override public void onAuthenticationError(int errorCode,CharSequence errString){
                    if(errorCode!=BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON && errorCode!=BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) toast(errString.toString());
                }
            });
    }

    private TextView versionLabel(int color){
        TextView v=text("v"+BuildConfig.VERSION_NAME,9,color,false);
        v.setLetterSpacing(.04f);
        return v;
    }

    private void applySystemInsets(View view, boolean includeTop, boolean includeBottom){
        final int l=view.getPaddingLeft(),t=view.getPaddingTop(),r=view.getPaddingRight(),b=view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v,insets)->{
            int top=0,bottom=0;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                top=bars.top; bottom=bars.bottom;
            }else{
                top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t+(includeTop?top:0), r, b+(includeBottom?bottom:0));
            return insets;
        });
        view.requestApplyInsets();
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private int theme(int light,int night){return dark?night:light;}
    private GradientDrawable bg(int color, int radius) { GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private GradientDrawable borderedBg(int color,int radius,int border){GradientDrawable g=bg(color,radius);g.setStroke(dp(1),border);return g;}
    private void styleLogo(ImageView image){
        Bitmap source=((BitmapDrawable)getResources().getDrawable(id.dbpro.central.monitor.R.drawable.dbpro_central_logo)).getBitmap();
        Bitmap small=Bitmap.createScaledBitmap(source,320,320,true);Bitmap themed=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[320*320];themed.getPixels(pixels,0,320,0,0,320,320);
        small.getPixels(pixels,0,320,0,0,320,320);
        for(int i=0;i<pixels.length;i++){int px=pixels[i],r=Color.red(px),g=Color.green(px),b=Color.blue(px);boolean white=r>165&&g>165&&b>165;pixels[i]=white?(dark?Color.WHITE:Color.rgb(14,83,190)):Color.TRANSPARENT;}
        themed.setPixels(pixels,0,320,0,0,320,320);image.setImageBitmap(themed);image.clearColorFilter();
    }
    private void styleLoginLogo(ImageView image){
        Bitmap source=((BitmapDrawable)getResources().getDrawable(id.dbpro.central.monitor.R.drawable.dbpro_central_logo)).getBitmap();
        Bitmap small=Bitmap.createScaledBitmap(source,480,480,true);Bitmap themed=small.copy(Bitmap.Config.ARGB_8888,true);
        int[] pixels=new int[480*480];themed.getPixels(pixels,0,480,0,0,480,480);
        for(int i=0;i<pixels.length;i++){
            int px=pixels[i],r=Color.red(px),g=Color.green(px),b=Color.blue(px);
            boolean logo=r>185&&g>185&&b>185;
            pixels[i]=logo?Color.rgb(14,83,190):Color.WHITE;
        }
        themed.setPixels(pixels,0,480,0,0,480,480);image.setImageBitmap(themed);image.clearColorFilter();
    }
    private EditText field(String hint, boolean password) {
        EditText e=new EditText(this); e.setHint(hint); e.setTextSize(15); e.setSingleLine(); e.setPadding(dp(16),0,dp(16),0);
        e.setTextColor(0xFF0F172A);e.setHintTextColor(0xFF64748B);e.setBackground(bg(0xFFF1F5F9,12)); e.setInputType(password?129:33); e.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(54)));
        if(password){
            android.graphics.drawable.Drawable eye=getResources().getDrawable(android.R.drawable.ic_menu_view);eye.setBounds(0,0,dp(24),dp(24));e.setCompoundDrawables(null,null,eye,null);e.setCompoundDrawablePadding(dp(12));
            e.setOnTouchListener((v,event)->{if(event.getAction()==android.view.MotionEvent.ACTION_UP&&event.getX()>=e.getWidth()-e.getPaddingRight()-dp(44)){int pos=e.getSelectionStart();boolean hidden=e.getTransformationMethod() instanceof PasswordTransformationMethod;e.setTransformationMethod(hidden?HideReturnsTransformationMethod.getInstance():PasswordTransformationMethod.getInstance());e.setSelection(Math.max(0,pos));return true;}return false;});
        }
        return e;
    }
    private Button button(String title) { Button b=new Button(this); b.setText(title); b.setTextColor(Color.WHITE); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(bg(0xFF2563EB,12)); return b; }

    private void root() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setBackgroundColor(theme(0xFFF8FAFC,0xFF0B1120));
        page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20),dp(24),dp(20),dp(30)); scroll.addView(page); setContentView(scroll);
        applySystemInsets(scroll,true,true);
    }
    private void showLogin() {
        timer.removeCallbacksAndMessages(null);getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);root(); page.setBackgroundColor(Color.WHITE); Space s=new Space(this); page.addView(s,new LinearLayout.LayoutParams(1,dp(58)));
        LinearLayout loginBrand=new LinearLayout(this);loginBrand.setOrientation(LinearLayout.VERTICAL);loginBrand.setGravity(Gravity.START);
        ImageView logo=new ImageView(this); logo.setImageResource(id.dbpro.central.monitor.R.drawable.dbpro_central_logo); logo.setScaleType(ImageView.ScaleType.CENTER_CROP); logo.setContentDescription("DBpro Central");styleLoginLogo(logo);
        loginBrand.addView(logo,new LinearLayout.LayoutParams(dp(148),dp(148)));
        TextView loginVersion=versionLabel(0xFF64748B);LinearLayout.LayoutParams lvp=new LinearLayout.LayoutParams(-2,-2);lvp.leftMargin=dp(11);lvp.topMargin=dp(-4);loginBrand.addView(loginVersion,lvp);
        LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(dp(170),-2);brandParams.gravity=Gravity.CENTER_HORIZONTAL;page.addView(loginBrand,brandParams);
        Space gap=new Space(this); page.addView(gap,new LinearLayout.LayoutParams(1,dp(18)));
        TextView loginTitle=text("CENTRAL DASHBOARD",20,0xFF0F172A,true);loginTitle.setGravity(Gravity.CENTER);page.addView(loginTitle,new LinearLayout.LayoutParams(-1,-2));
        EditText email=field("Email",false), pass=field("Password",true);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(54)); fp.topMargin=dp(24); page.addView(email,fp);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(54)); pp.topMargin=dp(12); page.addView(pass,pp);
        Button login=button("Masuk"); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(54)); bp.topMargin=dp(18); page.addView(login,bp);
        if(token!=null && Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
            Button biometric=button("Masuk dengan sidik jari");biometric.setTextColor(0xFF2563EB);biometric.setBackground(borderedBg(Color.WHITE,12,0xFFBFDBFE));
            LinearLayout.LayoutParams bioParams=new LinearLayout.LayoutParams(-1,dp(50));bioParams.topMargin=dp(10);page.addView(biometric,bioParams);
            biometric.setOnClickListener(v->biometricUnlockOrDashboard());
        }
        pass.setImeOptions(EditorInfo.IME_ACTION_DONE); login.setOnClickListener(v -> doLogin(email.getText().toString(),pass.getText().toString(),login));
    }
    private void doLogin(String email,String password,Button b) {
        if(email.trim().isEmpty()||password.trim().isEmpty()){toast("Email dan password wajib diisi");return;} b.setEnabled(false); b.setText("Memeriksa…");
        new Thread(() -> { try { JSONObject q=new JSONObject().put("email",email.trim()).put("password",password); JSONObject r=request("auth/login","POST",q,null); token=r.getString("accessToken"); getPreferences(MODE_PRIVATE).edit().putString("token",token).apply(); runOnUiThread(this::showDashboard); }
        catch(Exception e){String detail=e.getMessage();runOnUiThread(()->{b.setEnabled(true);b.setText("Masuk");toast(loginError(detail));});} }).start();
    }

    private String loginError(String detail){
        if(detail==null||detail.isBlank())return "Login gagal. Silakan coba lagi.";
        try{JSONObject json=new JSONObject(detail);String message=json.optString("message",json.optString("error",""));if(!message.isBlank())return message;}catch(Exception ignored){}
        if(detail.toLowerCase().contains("timed out")||detail.toLowerCase().contains("timeout"))return "Server lambat merespons. Silakan coba lagi.";
        return "Login gagal: "+detail;
    }
    private void showDashboard() {
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setClipToPadding(false);shell.setBackgroundColor(theme(0xFFF8FAFC,0xFF0B1120));
        applySystemInsets(shell,true,true);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(2),dp(20),dp(2));header.setBackgroundColor(theme(0xFFF8FAFC,0xFF0B1120));
        TextView themeButton=text(dark?"☀":"☾",26,theme(0xFF334155,0xFFF8FAFC),false);themeButton.setGravity(Gravity.CENTER);themeButton.setContentDescription(dark?"Gunakan tema terang":"Gunakan tema gelap");header.addView(themeButton,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setGravity(Gravity.CENTER);
        ImageView mark=new ImageView(this); mark.setImageResource(id.dbpro.central.monitor.R.drawable.dbpro_central_logo); mark.setScaleType(ImageView.ScaleType.CENTER_CROP); mark.setContentDescription("DBpro Central");styleLogo(mark);
        brand.addView(mark,new LinearLayout.LayoutParams(dp(54),dp(54)));
        TextView version=versionLabel(theme(0xFF64748B,0xFF94A3B8));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-2,-2);vp.topMargin=dp(-7);brand.addView(version,vp);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(66),1));
        TextView refreshButton=text("⟳",28,theme(0xFF2563EB,0xFF60A5FA),false); refreshButton.setGravity(Gravity.CENTER); refreshButton.setContentDescription("Refresh data monitoring");header.addView(refreshButton,new LinearLayout.LayoutParams(dp(52),dp(52)));
        shell.addView(header,new LinearLayout.LayoutParams(-1,dp(70)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(theme(0xFFF8FAFC,0xFF0B1120));
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(4),dp(20),dp(30));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);
        refreshButton.setOnClickListener(v->{startRefreshSpinner(refreshButton);loadDashboard(refreshButton);});
        themeButton.setOnClickListener(v->{dark=!dark;getPreferences(MODE_PRIVATE).edit().putBoolean("dark",dark).apply();recreate();});
        LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);statusRow.setPadding(dp(15),dp(14),dp(15),dp(14));statusRow.setBackground(borderedBg(theme(0xFFEFF4FA,0xFF172033),15,theme(0xFFD8E2EF,0xFF334155)));
        LinearLayout htext=new LinearLayout(this);htext.setOrientation(LinearLayout.VERTICAL);serverName=text("DBpro Server",21,theme(0xFF0F172A,0xFFF8FAFC),true);htext.addView(serverName);updatedLabel=text("Menghubungkan…",12,theme(0xFF64748B,0xFF94A3B8),false);htext.addView(updatedLabel);statusRow.addView(htext,new LinearLayout.LayoutParams(0,-2,1));
        liveLabel=text("● LIVE",12,0xFF16A34A,true); liveLabel.setPadding(dp(11),dp(7),dp(11),dp(7));liveLabel.setBackground(bg(0xFFDCFCE7,30));statusRow.addView(liveLabel);
        LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-1,-2);statusParams.topMargin=dp(10);page.addView(statusRow,statusParams);
        cpu=new MetricCard(this,"CPU Usage",0xFF1677FF,dark);ram=new MetricCard(this,"Memory Usage",0xFFFF1677,dark);disk=new MetricCard(this,"Disk Space",0xFFF59E0B,dark);network=new MetricCard(this,"Network I/O",0xFFD946EF,dark);
        page.addView(cpu,cardParams());page.addView(ram,cardParams());page.addView(disk,cardParams());page.addView(network,cardParams());
        serviceList=sectionBox("SERVICE & CONTAINER");
        incidentList=sectionBox("RIWAYAT GANGGUAN");
        TextView logout=text("Keluar dari akun",14,0xFFDC2626,true);logout.setGravity(Gravity.CENTER);logout.setPadding(0,dp(24),0,dp(12));logout.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().clear().apply();token=null;showLogin();});page.addView(logout);
        timer.removeCallbacksAndMessages(null);loadDashboard(null);timer.postDelayed(refresh,2_000);
    }
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(246));p.topMargin=dp(12);return p;}
    private LinearLayout sectionBox(String title){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(15),dp(14),dp(15),dp(15));box.setBackground(borderedBg(theme(Color.WHITE,0xFF111827),15,theme(0xFFE2E8F0,0xFF334155)));TextView t=text(title,15,theme(0xFF0F172A,0xFFF8FAFC),true);box.addView(t);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);box.addView(list,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(18);page.addView(box,p);return list;}
    private void loadDashboard(){loadDashboard(null);}
    private void loadDashboard(TextView refreshButton){if(loading){finishRefresh(refreshButton);return;}loading=true;new Thread(()->{try{JSONObject d=request("monitoring/dashboard","GET",null,token);runOnUiThread(()->{render(d);finishRefresh(refreshButton);loading=false;});}catch(Exception e){runOnUiThread(()->{liveLabel.setText("● OFFLINE");liveLabel.setTextColor(0xFFDC2626);liveLabel.setBackground(bg(0xFFFEE2E2,30));updatedLabel.setText("Tidak dapat mengambil data");finishRefresh(refreshButton);loading=false;});}}).start();}
    private void startRefreshSpinner(TextView refreshButton){
        if(refreshAnimator!=null) refreshAnimator.cancel();
        refreshButton.setEnabled(false);
        refreshAnimator=ObjectAnimator.ofFloat(refreshButton,"rotation",0f,360f);
        refreshAnimator.setDuration(700);refreshAnimator.setRepeatCount(ValueAnimator.INFINITE);refreshAnimator.start();
    }
    private void finishRefresh(TextView refreshButton){
        if(refreshButton!=null){
            if(refreshAnimator!=null){refreshAnimator.cancel();refreshAnimator=null;}
            refreshButton.setRotation(0f);refreshButton.setText("⟳");refreshButton.setEnabled(true);
        }
    }
    private void render(JSONObject d){
        liveLabel.setText("● LIVE");liveLabel.setTextColor(0xFF16A34A);liveLabel.setBackground(bg(0xFFDCFCE7,30));serverName.setText(d.optString("serverName","DBpro Server"));updatedLabel.setText("Diperbarui " + d.optString("updatedAt","sekarang"));
        bind(cpu,d.optJSONObject("cpu"));bind(ram,d.optJSONObject("memory"));bind(disk,d.optJSONObject("disk"));bind(network,d.optJSONObject("network"));
        serviceList.removeAllViews();JSONArray ss=d.optJSONArray("services");if(ss!=null)for(int i=0;i<ss.length();i++){JSONObject o=ss.optJSONObject(i);serviceList.addView(row(o.optString("name","Service"),o.optString("status","unknown"),o.optString("detail","")));}
        incidentList.removeAllViews();JSONArray is=d.optJSONArray("incidents");if(is==null||is.length()==0)incidentList.addView(row("Tidak ada gangguan","healthy","Semua layanan normal"));else for(int i=0;i<is.length();i++){JSONObject o=is.optJSONObject(i);incidentList.addView(row(o.optString("title","Gangguan"),"incident",o.optString("time","")));}
    }
    private void bind(MetricCard c,JSONObject o){if(o==null)return;JSONArray a=o.optJSONArray("series");float[] x=new float[a==null?0:a.length()];for(int i=0;i<x.length;i++)x[i]=(float)a.optDouble(i);c.setData(o.optDouble("value"),o.optString("unit","%"),o.optDouble("max",100),o.optString("subtitle","Realtime"),x);}
    private View row(String name,String status,String detail){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(13),0,dp(8));LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text(name,14,theme(0xFF0F172A,0xFFF8FAFC),true));t.addView(text(detail,12,theme(0xFF64748B,0xFF94A3B8),false));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));boolean ok=status.equals("running")||status.equals("healthy");TextView st=text(status.toUpperCase(),10,ok?0xFF16A34A:0xFFDC2626,true);r.addView(st);return r;}
    private JSONObject request(String path,String method,JSONObject body,String bearer)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(BuildConfig.API_BASE_URL+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(30000);c.setReadTimeout(30000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);if(body!=null){c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();String raw=readUtf8(in);if(code==401&&bearer!=null){runOnUiThread(()->{getPreferences(MODE_PRIVATE).edit().clear().apply();showLogin();});throw new IOException(raw); }if(code>=400)throw new IOException(raw);return new JSONObject(raw);}
    private String readUtf8(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    static class MetricCard extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);String label,unit="%",subtitle="";int color;boolean dark;double value,maxScale=100;float[] data=new float[0];RectF box=new RectF();
        MetricCard(Context c,String l,int col,boolean night){super(c);label=l;color=col;dark=night;p.setTypeface(Typeface.create("sans",Typeface.NORMAL));setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setData(double v,String u,double max,String sub,float[] d){value=v;unit=u;maxScale=Math.max(1,max);subtitle=sub;data=d;invalidate();}
        protected void onDraw(Canvas c){super.onDraw(c);float den=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();p.setColor(dark?0xFF111827:Color.WHITE);p.setShadowLayer(8*den,0,2*den,0x140F172A);c.drawRoundRect(0,0,w,h,15*den,15*den,p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1*den);p.setColor(dark?0xFF334155:0xFFE2E8F0);c.drawRoundRect(.5f*den,.5f*den,w-.5f*den,h-.5f*den,15*den,15*den,p);p.setStyle(Paint.Style.FILL);
            p.setColor(dark?0xFF94A3B8:0xFF64748B);p.setTextSize(11*den);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(label,17*den,28*den,p);p.setColor(dark?0xFFF8FAFC:0xFF0F172A);p.setTextSize(27*den);c.drawText(String.format(Locale.US,value>=100?"%.0f%s":"%.1f%s",value,unit),17*den,62*den,p);p.setColor(0xFF94A3B8);p.setTextSize(11*den);p.setTypeface(Typeface.DEFAULT);c.drawText(subtitle,17*den,80*den,p);
            float barLeft=17*den,barRight=w-17*den,barTop=91*den,barBottom=101*den;p.setColor(dark?0xFF2A2A2A:0xFFE2E8F0);c.drawRoundRect(barLeft,barTop,barRight,barBottom,6*den,6*den,p);float barValue=(float)Math.max(0,Math.min(maxScale,value));p.setColor(label.equals("CPU Usage")?(dark?0xFFE5E7EB:0xFFCBD5E1):color);c.drawRoundRect(barLeft,barTop,barLeft+(barRight-barLeft)*barValue/(float)maxScale,barBottom,6*den,6*den,p);
            float left=54*den,right=w-17*den,top=124*den,bottom=h-28*den;box.set(left,top,right,bottom);float max=(float)maxScale;p.setTextSize(9*den);p.setTypeface(Typeface.DEFAULT);for(int i=0;i<5;i++){float y=top+(bottom-top)*i/4f;p.setColor(dark?0xFF242424:0xFFE2E8F0);p.setStrokeWidth(1*den);c.drawLine(left,y,right,y,p);p.setColor(dark?0xFF737373:0xFF94A3B8);float axis=max*(4-i)/4f;String tick=unit.trim().equals("%")?String.format(Locale.US,"%.0f%%",axis):String.format(Locale.US,"%.1f",axis);c.drawText(tick,8*den,y+3*den,p);}if(data.length<2)return;Path line=new Path(),fill=new Path();for(int i=0;i<data.length;i++){float x=left+(right-left)*i/(data.length-1f);float y=bottom-(bottom-top)*Math.min(data[i],max)/max;if(i==0){line.moveTo(x,y);fill.moveTo(x,bottom);fill.lineTo(x,y);}else{line.lineTo(x,y);fill.lineTo(x,y);}}fill.lineTo(right,bottom);fill.close();if(!label.equals("CPU Usage")){p.setShader(new LinearGradient(0,top,0,bottom,(color&0x00FFFFFF)|0x66000000,(color&0x00FFFFFF)|0x09000000,Shader.TileMode.CLAMP));c.drawPath(fill,p);p.setShader(null);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f*den);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);}
    }
}
