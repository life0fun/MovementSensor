
package com.colorcloud.movementsensor;

import java.util.LinkedList;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;
import com.colorcloud.movementsensor.MovementSensorManager.Msg;
import com.colorcloud.movementsensor.walksensor.StepCounter;

/**
 * taking algorithm from a simple pedometer just to detect user is in walking state.
 * No need to precisely measure steps, just a state notification.
 *  
 * Detects steps and notifies all listeners (that implement StepListener).
 */
public class AcclManager implements SensorEventListener
{
    private final static String TAG = "MOV_Accl";
    private final static int MINSTEPS_MINUTE = 6;  // based on Tom's data
    private final int h = 480; 
    private final float mYOffset = 480 * 0.5f;
    private final float mScaleOrientation = - (480 * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));      // this is for orientation
    private final float mScaleWalking = - (480 * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));  // this is for walking
    
    private float   mLimit = 10;
    private float   mLastValues[] = new float[3*2];
    private float   mLastDirections[] = new float[3*2];
    private float   mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private float   mLastDiff[] = new float[3*2];
    private int     mLastMatch = -1;
    private LinkedList<Long> mStepsInAMinute = new LinkedList<Long>();  // records how many steps within a minute.
    
    private Context mContext;
    private MovementSensorManager mMSMan;
    private Handler mMSManHdl;
    SensorManager mSensorMan;
    Sensor mSensor;
    WindowManager mWindowMan;
    Display mDisplay;
    
    StepCounter mStepCounter = null;
    
    public AcclManager(MovementSensorManager msman, Handler hdl) {
    	mContext = (Context)msman;
		mMSMan = msman;
		mMSManHdl = hdl;
		
        mStepCounter = new StepCounter(new StepCounter.StepCountListener() {
            public void onStep(int totalSteps, double tot_distance_covered) {
            	if(totalSteps > 0) {
            		MSAppLog.d(TAG, "stepCounter says step: " + totalSteps);
            		onMotDetectStep(totalSteps);
            	}
            }
        });
        
        // Get an instance of the WindowManager for orientation detection
        mWindowMan = (WindowManager)mContext.getSystemService(mContext.WINDOW_SERVICE);
        mDisplay = mWindowMan.getDefaultDisplay();
        
        // get accelerometer sensor
        mSensorMan = (SensorManager)mContext.getSystemService(mContext.SENSOR_SERVICE);
        
        mSensor = mSensorMan.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMan.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
        //step counter listen to accelerometer
        //mSensorMan.registerListener(mStepCounter, mSensor, SensorManager.SENSOR_DELAY_GAME);
        
        //mSensor = mSensorMan.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        //mSensorMan.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
        //mSensorMan.registerListener(this, mSensor, 1000000000);  // get event every 1e6 microsecond, 1 second.
    }
    
    public void clean() {
    	mSensorMan.unregisterListener(this);
    	mSensorMan.unregisterListener(mStepCounter);
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor; 
        synchronized (this) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            	//MSAppLog.d(TAG, "Accl Data: X:Y:Z " + event.values[0] + ":" + event.values[1] + ":" + event.values[2]);
            	onSensorData(event);
            }
        }
    }
    
    private void onSensorData(SensorEvent event){
    	switch (mDisplay.getRotation()) {
    	case Surface.ROTATION_0:
             //mSensorX = event.values[0];
             //mSensorY = event.values[1];
             break;
         case Surface.ROTATION_90:
             //mSensorX = -event.values[1];
             //mSensorY = event.values[0];
             break;
         case Surface.ROTATION_180:
             //mSensorX = -event.values[0];
             //mSensorY = -event.values[1];
             break;
         case Surface.ROTATION_270:
             //mSensorX = event.values[1];
             //mSensorY = -event.values[0];
             break;
     	}
    	
    	detectStep(event);
    	//simpleStep(event);
    }
    
    
    private void simpleStep(SensorEvent event){
    	double s = 0;
    	for(int i=0;i<3;i++){
    		s += event.values[i]*event.values[i];
    		//MSAppLog.d(TAG, "simpleStep s = " + s );
    	}
    	double sq = Math.sqrt(s);
    	//MSAppLog.d(TAG, "simpleStep S = " + sq );
    	
    	if(sq > 1.75*9.8f) {
    		onDetectStep();
    	}
    }
    
    private void detectStep(SensorEvent event){
    	float vSum = 0;
        for (int i=0 ; i<3 ; i++) {
            //final float v = mYOffset + event.values[i] * mScaleWalking;
        	// haijin reduce sensitivie. 0.5 for p2 hw
        	final float v = mYOffset + event.values[i] * mScaleWalking * 0.5f; 
            vSum += v;
        }
        int k = 0;
        float v = vSum / 3;
        
        float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
        if (direction == - mLastDirections[k]) {
            // Direction changed
            int extType = (direction > 0 ? 0 : 1);  // minumum or maximum?
            mLastExtremes[extType][k] = mLastValues[k];
            float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

            if (diff > mLimit) {
                boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                boolean isNotContra = (mLastMatch != 1 - extType);
                
                if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                	MSAppLog.d(TAG, "step detected !");
                	onDetectStep();
                    mLastMatch = extType;
                }
                else {
                    mLastMatch = -1;
                }
            }
            mLastDiff[k] = diff;
        }
        mLastDirections[k] = direction;
        mLastValues[k] = v;
    }
    
    private int stepsWithin(long now, long window){
    	int steps = 0;
    	for(long t : mStepsInAMinute){
    		if(now - t <= window){  // last step within window
    			steps++;
    		}
    	}
    	return steps;
    }
    
    private void onDetectStep() {
    	long now = System.currentTimeMillis();
    	mStepsInAMinute.add(now);  // add one step upon one callback
    	while(mStepsInAMinute.peek() + 60000 < now){  // keep steps in last min  
    		mStepsInAMinute.remove();  // remove when outside 30 seconds window.
    	}
    	
    	int steps = stepsWithin(now, 30000);
    	MSAppLog.d(TAG, "steps within 30 seconds: " + steps);
    	if(steps >= MINSTEPS_MINUTE){  // walked 12 steps within last 30 seconds, based on tom's log
    		MovementSensorApp.sendMessage(mMSManHdl, Msg.ACCL_WALKING, Boolean.valueOf(true), null);
    	}
    }
    
    private void onMotDetectStep(int steps) {
    	long now = System.currentTimeMillis();
    	mStepsInAMinute.add(now);  // add one step upon one callback
    	while(mStepsInAMinute.peek() + 60000 < now){  // keep steps in last min  
    		mStepsInAMinute.remove();  // remove when outside 30 seconds window.
    	}
    	
    	int nsteps = stepsWithin(now, 30000);
    	MSAppLog.d(TAG, "steps within 30 seconds: " + nsteps);
    	if(steps >= MINSTEPS_MINUTE){  // walked 10 steps within last 30 seconds, based on tom's log
    		MovementSensorApp.sendMessage(mMSManHdl, Msg.ACCL_WALKING, Boolean.valueOf(true), null);
    	}
    }
    
    /**
     * if we really check walking, check with 60 seconds, 10 steps.
     * if we just check whether there exist walking steps, check 30 seconds, 5 steps.
     * @param winseconds  the time window in seconds to look. 
     * @param steps : how many steps make it positive.
     * @return true if exists steps match threshold
     */
    public boolean isWalking(long winseconds, int steps) {
    	long now = System.currentTimeMillis();
    	//int windowsteps = stepsWithin(now, 60000);  // did user ever walked in last minute ?
    	int windowsteps = stepsWithin(now, winseconds*1000);  // did user ever walked in last minute ?
    	
    	if(windowsteps >= steps)  // 10 steps by 
    		return true;
    	else
    		return false;
    }
}