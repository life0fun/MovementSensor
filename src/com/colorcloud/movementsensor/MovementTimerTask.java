package com.colorcloud.movementsensor;

import static com.colorcloud.movementsensor.Constants.LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS;

import java.util.HashMap;
import java.util.Map;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateUtils;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;

/**
 *<code><pre>
 * CLASS:
 *  Timer task is a stand alone task that performs periodical jobs.
 *    1. poll the wifi passive scan result before async notification from wifi is available.
 *
 * RESPONSIBILITIES:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public class MovementTimerTask {
	private static final String TAG = "MOV_TMTSK";
	public static final int TASK_REPEAT_LIMITS = 4;  // not per task configurable for now.
    
    public static enum TaskAction{
    	PERIODICAL_WIFI(0), PERIODICAL_BCAST(1), PERIODICAL_CALLBACK(2);
        private final int type;
        TaskAction(int type) {
            this.type = type;
        }
        public int getType() {
            return type;
        }
    }
    
    private Context mContext;
    private MovementSensorManager mMSMan;
    
    String mTaskName;	  // what is the task name
    Handler mHdl;		   // the caller's handler
    TaskAction mAction;    // the action of timer task
    PendingIntent mIntent; // the intent to send out when timer expired
    MovementListener mListener; // the callback listener, the caller registere this listener, got called upon event.
    long mFreq;			  // the cycle in minutes
    long mCounts = 0;
    long mLastActionTime = 0;
    long mMovementStartTime = 0;
    
    long mLimits = 5;   // XXX only repeat for 5 times.
    
    /**
     * Constructor
     */
    public MovementTimerTask(String taskname, Handler hdl, TaskAction action, long freq, PendingIntent intent, MovementListener listener) {
        mContext = (Context)MovementSensorManager.getInstance();
        mTaskName = taskname;
        mHdl = hdl;
        mAction = action;
        mFreq = freq;
        mIntent = intent;
        mListener = listener;
        mLastActionTime = 0;
        mMovementStartTime = 0;
    }

    /**
     * reset all times upon movement indication from sensor hub.
     */
    public void resetTimeUponNewMovement(boolean moving, long delay){
    	mMovementStartTime = System.currentTimeMillis() - delay;
    	mCounts = 0;
    }
    
    public long getDurationInMin(boolean move){
    	return (System.currentTimeMillis() - mMovementStartTime)/DateUtils.MINUTE_IN_MILLIS;
    }
    
    /**
     * check whether cycle matured. not mature if limit reached.
     */
    public boolean isCycleMatured(long now){
    	boolean ret = true;  // let's return true for now, given we only have one task.
    	if(mCounts > TASK_REPEAT_LIMITS){
    		MSAppLog.d(TAG, "isCycleMatured: counts reach limits: " + mCounts);
    		return false;
    	}
    	
    	if((now - mLastActionTime)/DateUtils.MINUTE_IN_MILLIS + 1 > mFreq){
    		ret = true;
    	}
    	MSAppLog.d(TAG, "isCycleMatured:" + now + " lastActionTime:" + mLastActionTime + " Freq:" + mFreq);
    	return ret;
    }
    
    public void invokeMovementAction(boolean move){
    	mLastActionTime = System.currentTimeMillis();  // update last action time
    	mCounts++;
    	
    	switch(mAction){
    	case PERIODICAL_WIFI:
    		MSAppLog.e(TAG, "invokeMovementAction:" + mAction + " MOV:" + move + " counts:" + mCounts +" Action: start wifi");
    		MovementSensorManager.getInstance().startBeaconScan();
    		break;
    	case PERIODICAL_BCAST:
    		MSAppLog.d(TAG, "invokeMovementAction:" + mAction + " MOV:" + move +  " counts:" + mCounts + " Action: bcast pending intent.");
    		try{
    			mIntent.send();
    		}catch(Exception e){
    			MSAppLog.e(TAG, "invokeMovementAction:" + e.toString());
    		}
    		break;
    	case PERIODICAL_CALLBACK:
    		MSAppLog.d(TAG, "invokeMovementAction:" + mAction + " MOV:" + move +  " counts:" + mCounts + " Action: call back.");
    		mListener.onMoving(move, getDurationInMin(move));
    		break;
    	default:
    		MSAppLog.d(TAG, "invokeMovementAction: default: " + mAction + " MOV:" + move);
    		break;
    	}
    }
    
    /**
     * TDD drives out roles and IF between collaborators.
     */
    public static class Test {
        /**
         * start the timer task, return the job Id
         * @param cycle, the periodical cycle
         * @return the job id
         */
        public static void startPeriodicalPolling() {
        }
        /**
         * stop the on-going periodical job
         * @param jobId
         */
        public static void stopPeriodicalPolling(int jobId) {
        }
    }
}
