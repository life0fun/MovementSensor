package com.colorcloud.movementsensor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.text.format.Time;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;

public class LogFile {
	public static final String TAG = "MOV_LOGF";
	public static final String LOGF = "/data/mv.log";
	
	 // logger
    FileReader mLogReader = null;
    FileWriter mLogWriter = null;
    
    Context mContext;
    
    public LogFile(MovementSensorManager msman){
    	mContext = (Context)msman;
    	try{
    		//mLogWriter = mContext.openFileOutput(LOGF, Context.MODE_APPEND);
    		mLogWriter = new FileWriter(LOGF, true);
    		mLogReader = new FileReader(LOGF);
    	}catch(Exception e){
    		MSAppLog.e(TAG, " write: log file not found: " + e.toString());
    		mLogWriter = null;
    	}
    }
    
    public void writeLog(String...strings){
    	Time now = new Time();
    	now.setToNow();
    	try{
    		mLogWriter.write(now.format("%Y-%m-%d %H:%M:%S") + " ");
    		for(String s : strings){
    			mLogWriter.write(s + " ");
    			MSAppLog.d(TAG, s);
    		}
    		mLogWriter.write("\n");
    		mLogWriter.flush();
    	}catch(Exception e){
    		MSAppLog.e(TAG, " write exception " + e.toString());
    	}
    }
    
    public byte[] readLogByte(){
    	byte[] buffer = new byte[4096];
    	try{
    		//mLogReader = mContext.openFileInput(LOGF);
    		BufferedReader br = new BufferedReader(mLogReader);
    		String s; 
    		while((s = br.readLine()) != null) { 
    			MSAppLog.d(TAG, s);
    		}
    	}catch(Exception e){
    		MSAppLog.e(TAG, " read: log file not found: " + e.toString());
    	}
    	return buffer;
    }
    
    public String readLog() {
    	File file = new File(LOGF);
    	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    	
    	int i = 0;    
    	try {
    		InputStream in = new BufferedInputStream(new FileInputStream(file));
    		i = in.read();
    		while (i != -1){
    			byteArrayOutputStream.write(i);
    			i = in.read();
    	   }
    	   in.close();
    	} catch (IOException e) {
    	}
    	return byteArrayOutputStream.toString();
    }
    
}
