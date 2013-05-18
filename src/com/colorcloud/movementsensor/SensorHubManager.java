package com.colorcloud.movementsensor;

import static com.colorcloud.movementsensor.Constants.LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;
import com.colorcloud.movementsensor.MovementSensorManager.Msg;
//import com.colorcloud.sensorhub.SensorHub;

public class SensorHubManager {
	private static final String TAG = "MOV_Hub";
    final static long mStartDuration = 1*DateUtils.MINUTE_IN_MILLIS;   // 3 minute movement to trigger to cover 5 min scan interval.
    final static long mEndDuration = 1*DateUtils.MINUTE_IN_MILLIS;     // 1 min for stationary for stop periodical wifi scan.
    
	//SensorHub mSensorHub;    // sensorhub behaves as a discreted event emitter to trigger periodical timer
	boolean mState = false;  // true - moving,  false = station.

	private Context mContext;
    private MovementSensorManager mMSMan;
    private Handler mMSManHdl;
    
    //SensorHub.MovementListener mMovementListener;
    
    public SensorHubManager(MovementSensorManager msman, Handler hdl) {
    	mContext = msman;
		mMSMan = msman;
		mMSManHdl = hdl;
		
    	try{
//    		mSensorHub = (SensorHub)msman.getSystemService(Context.SENSOR_HUB_SERVICE);  // init sensor hub
//         	initSensorHubListener();
//         	mSensorHub.registerMovementListener(mMovementListener, (int)(mStartDuration/1000), (int)(mEndDuration/1000));  // sensor hub cycle in seconds
//         	MSAppLog.pd(TAG, "SENSOR_HUB_SERVICE: registered movement sensor with interval:" + mStartDuration + " : " + mEndDuration);
    	}catch(Error e){
         	MSAppLog.e(TAG, "SENSOR_HUB_SERVICE: does not exist...stop the service.");
//         	mSensorHub = null;
        }
    }
    
    public void stop(){
//    	mSensorHub.unregisterMovementListener(mMovementListener);
    }
    
    void initSensorHubListener(){
//    	mMovementListener = new SensorHub.MovementListener() {
//            public void onStartMovement() {
//            	mState = true;
//                MSAppLog.d(TAG, "SensorHub says onStartMovement!....start wifi scan timer every :" + LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS);
//                MovementSensorApp.sendMessage(mMSManHdl, Msg.SENSORHUB_EVENT, Boolean.valueOf(true), null);
//            }
//            public void onEndMovement() {
//            	mState = false;
//                MSAppLog.d(TAG, "SensorHub says onEndMovement!....stop wifi scan timer.");
//                MovementSensorApp.sendMessage(mMSManHdl, Msg.SENSORHUB_EVENT, Boolean.valueOf(false), null);
//            }
//        };
    }
}
