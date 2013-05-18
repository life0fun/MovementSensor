package com.colorcloud.movementsensor;

import static com.colorcloud.movementsensor.Constants.*;

import android.app.Application;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
/**
 *<code><pre>
 * CLASS:
 *  Our application class, Getting init before anything started.
 *
 * RESPONSIBILITIES:
 *
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */
public class MovementSensorApp extends Application {

    private static final String TAG = "MSApp_App";

    private String mVersion = null;

    //private RemoteResourceManager mRemoteResourceManager;
    AppPreferences mAppPref;

    MovementSensorManager mMSMan = null;  // available after location sensor manager service started.
    MovementSensorUI mUI = null;
    
    @Override
    public void onCreate() {
        super.onCreate();
        mVersion = Utils.getVersionString(this, PACKAGE_NAME);
        mAppPref = new AppPreferences(this);
        MSAppLog.d(TAG, "MSAPP constructor");
    }

    @Override
    public void onTerminate() {
    }
    
    /**
     * setting the movement sensor manager to app
     * @param msman
     */
    public void setMSMan(MovementSensorManager msman){
    	mMSMan = msman;
    }
    
    public void setUI(MovementSensorUI ui){
    	mUI = ui;
    }
    
    /**
     * return the reference to location manager
     * @return
     */
    public MovementSensorManager getMSMan() {
        return mMSMan;
    }

    public boolean isUserMoving() {
    	if(MOVING.equals(mAppPref.getString("USER_STATE"))){
    		return true;
    	}
    	return false;
    }
    
    /**
     * out logger
     * @author e51141
     *
     */
    public static class MSAppLog {
    	public static void n(String tag, String msg){
    		return;  // null log
    	}
        public static void i(String tag, String msg) {
            if (LOG_VERBOSE) Log.i(tag, msg);
        }
        public static void e(String tag, String msg) {
            Log.e(tag, msg);
        }
        public static void d(String tag, String msg) {
            if (LOG_VERBOSE) Log.d(tag, msg);
        }
        public static void pd(String tag, String msg) {
            Log.d(tag, msg);  // always platform debug logging
        }
        public static void dbg(Context ctx, String direction, String... msg) {
            //if(!LOG_VERBOSE) return;

            ContentValues values = new ContentValues();
            StringBuilder sb = new StringBuilder();

            if (direction.equals(DEBUG_OUT)) {
                values.put(DEBUG_STATE, msg[0]);
            }

            for (String s : msg) {
                sb.append(s);
                sb.append(" : ");
            }

            values.put(DEBUG_COMPKEY, VSM_PKGNAME);
            values.put(DEBUG_COMPINSTKEY,PACKAGE_NAME);
            values.put(DEBUG_DIRECTION, direction);
            values.put(DEBUG_DATA, sb.toString());
            Log.d("MSAPP_DBG", sb.toString());
            try {
                ctx.getContentResolver().insert(DEBUG_DATA_URI, values);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * @return current app version
     */
    public String getVersion() {
        if (mVersion != null) {
            return mVersion;
        } else {
            return "";
        }
    }

    /**
     * start location manage
     */
    public void startMovementManager() {
        Intent myIntent = new Intent(this, MovementSensorManager.class);
        //Intent myIntent = new Intent(this, NoCellLocationManager.class);
        myIntent.putExtra(INTENT_PARM_STARTED_FROM_BOOT, false);
        ComponentName component = this.startService(myIntent);
        if (component != null) {
            MSAppLog.d(TAG, "Movement Sensor Services started: " + component.toShortString());
        } else {
            MSAppLog.d(TAG, "Movement Sensor Services start failed.");
        }
    }

    public static void sendMessage(Handler hdl, int what, Object obj, Bundle data) {
        MSAppLog.e(TAG, "Sending Message to " + hdl + ": msg :" + what);
        Message msg = hdl.obtainMessage();
        msg.what = what;
        if (obj != null)
            msg.obj = obj;
        if (data != null)
            msg.setData(data);
        hdl.sendMessage(msg);
    }
    
    /**
     * broadcast entering into poi intent from location manager. LOCATION_MATCHED_EVENT
     * @param poi
     */
    public static void sendVSMMovementUpdate(Context ctx, String movement) {
        Intent mvintent = new Intent(VSM_PROXY);
        StringBuilder sb = new StringBuilder();
        sb.append(VSM_MS_PARAM_SETVALUE);
        sb.append("p0="+movement+";");
        sb.append(VSM_MS_PARAM_END);
        mvintent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        MSAppLog.d(TAG, "notify VSM: "+ sb.toString());
        ctx.sendBroadcast(mvintent, PERMISSION);
    }

}
