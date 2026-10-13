package com.forma.probe;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
public class UpgradeProbe extends Instrumentation {
 private String phase;
 public void onCreate(Bundle args) { super.onCreate(args);phase=args.getString("phase");start(); }
 public void onStart() { Bundle result=new Bundle();try {
  Context c=getTargetContext(); File f=new File(c.getFilesDir(),"ota-migration-probe.txt");
  if("write".equals(phase)) { try(FileOutputStream out=new FileOutputStream(f)) {out.write("local-records-survive".getBytes("UTF-8"));}
   if(!c.getSharedPreferences("ota-migration-probe",0).edit().putInt("hydration",1750).commit())throw new Exception("Write failed");
  } else { byte[] bytes=new byte[(int)f.length()];try(FileInputStream in=new FileInputStream(f)) {if(in.read(bytes)!=bytes.length)throw new Exception("Read failed");}
   if(!"local-records-survive".equals(new String(bytes,"UTF-8")) || c.getSharedPreferences("ota-migration-probe",0).getInt("hydration",0)!=1750)throw new Exception("Records changed");
   f.delete();c.getSharedPreferences("ota-migration-probe",0).edit().clear().commit();
  } result.putString("probe","PASS: "+phase); finish(-1,result);
 }catch(Exception e){result.putString("probe","FAIL: "+e);finish(0,result);} }
}