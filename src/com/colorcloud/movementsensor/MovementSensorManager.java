package com.colorcloud.movementsensor;

import static com.colorcloud.movementsensor.Constants.ALARM_TIMER_DIFF_EXPIRED;
import static com.colorcloud.movementsensor.Constants.ALARM_TIMER_MOVEMENT_EXPIRED;
import static com.colorcloud.movementsensor.Constants.ALARM_TIMER_SET_TIME;
import static com.colorcloud.movementsensor.Constants.ALARM_TIMER_STARTSCAN;
import static com.colorcloud.movementsensor.Constants.mStartDuration;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.format.DateUtils;

import com.colorcloud.movementsensor.R;
import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;
import com.colorcloud.movementsensor.MovementTimerTask.TaskAction;

/**
 *<code><pre>
 * CLASS:
 *  implements background location track service mother.
 *
 * RESPONSIBILITIES:
 *  Coordinate all the components to tracking and logging location information.
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public class MovementSensorManager extends Service {

    private static final String TAG = "MOV_MSMan";
  
    private static MovementSensorManager _sinstance = null;

    public static class Msg {
        final static int START_WIFISCAN 		= 100;    	// start monitoring
        final static int STOP_WIFISCAN 		= 101;    	// stop monitoring
        final static int KEEPING_MOVEMENT_STATUS = 102;	// movement indication is edge trigger, need periodically emit movement event until state flipped  
        final static int STOPPED_MOVING      = 103;

        // msg definitely indicate moving or not
        static final int WIFI_CONNECTED			= 1001;    // must be station
        static final int CARDOCK_MOUNT			= 1002;    // must be moving
        static final int GPS_STREAM				= 1003;    // keep getting gps fix with great dist, must be driving.
        static final int CAR_CHARGER			= 1004;	   // car charger on, same as car dock.

        final static int DIFFTIMER_EVENT			= 200;
        final static int CELLTOWER_CHANGED 	    = 300;  	// get network fix
        final static int CELLTOWER_DONE 		= 301;
        final static int BEACONSCAN_RESULT		= 400;
        final static int SENSORHUB_EVENT		= 500;
        final static int LOCATION_UPDATE		= 600;
        final static int ACCL_WALKING			= 700;
        
        final static int NULL 					= 99999;
    }

    //expose bindable  APIs
    //private MovementSensorHandler mAPIHandler;
    MovementSensorState mState;
    
    public static enum SensorEventType {
        TIMER(0), SENSORHUB(1), WIFI(2), CELLTOWER(3), LOCATION(4), WALKING(5), MOVING(6), STATION(7);
        private final int event;
        SensorEventType(int event) {
            this.event = event;
        }
        public int getEvent() {
            return event;
        }
        public String toString(){
        	return "e=" + event + " :: TIMER(0), SENSORHUB(1), WIFI(2), CELLTOWER(3), LOCATION(4), WALKING(5), MOVING(6), STATION(7)";
        }
    };
    
    private  WorkHandler mWorkHandler;
    private  MessageHandler mHandler;
   
    MovementSensorApp  	mMSApp;
    long mStartedTime = 0;
  
    // the three data source for movement, sensorhub, celltower, location manager.
    SensorHubManager mSensorHub;   // sensorhub behaves as a discreted event emitter to trigger periodical timer
    BeaconManager mBeacon;
    TelephonyMonitor      mTelMon;
    LocationMonitor mLocMon;
    AcclManager mAccl;
    
    private AlarmManager mAlarmMan;
    private PendingIntent mTimerExpiredIntent = null;   // the intent give to alarm manager for broadcast.
    private TimerEventReceiver mTimerEventRecvr;
        
    private WifiScanTask mWifiTask;   // pre-defined wifi scan task.
    private List<MovementTimerTask> mTasks;	// a list of task runnable needs to be invoked upon movement indication.
    
    LogFile mLogF;

    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        _initialize();
        MSAppLog.pd(TAG, "MovementSensor onCreate() done with state to running...");
    }
    
    /**
     * @see android.app.Service#onStart(Intent,int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	_initialize();
        return START_STICKY;
    }
    

    /**
     * @see android.app.Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        MSAppLog.d(TAG, "MovementSensor onBind() called ");
        return null;
    }
    
    @Override
    public void onDestroy() {
        mTelMon.stopTelMon();     // unregister cell change first
        mLocMon.cleanup();
        mBeacon.cleanupReceivers();
        mSensorHub.stop();
        
        cancelAlarmTimer();
        mTimerEventRecvr.unregisterTimerEventHandler();
        
        _sinstance = null;
    }
    
    public Handler getMSManHandler() { return mHandler; }
    
    /**
     * singleton pattern.
     */
    public synchronized static MovementSensorManager getInstance(){
    	return _sinstance;
    }
    /**
     * singleton init factory
     */
    private void _initialize() {
    	mStartedTime = System.currentTimeMillis();
    	
        if (_sinstance != null) {
            MSAppLog.pd(TAG, "_initialize, already initialized, do nothing.");
            return;
        }
        
        mWorkHandler = new WorkHandler(TAG);
        mHandler = new MessageHandler(mWorkHandler.getLooper());
        mAlarmMan = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
      
        mMSApp = (MovementSensorApp)getApplication();
                
        mState = new MovementSensorState(this, mHandler);
        mSensorHub = new SensorHubManager(this, mHandler);
        mTelMon = new TelephonyMonitor(this, mHandler);
        mBeacon = new BeaconManager(this, mHandler);
        mLocMon = new LocationMonitor(this, mHandler);
        mAccl = new AcclManager(this, mHandler); // acceleromter can not detect speed, it is for velocity.
        
        mTasks = new ArrayList<MovementTimerTask>();

        mTimerEventRecvr = new TimerEventReceiver(this, mHandler);
        mTimerEventRecvr.registerTimerEventHandler();   // register timer bcast event recver and handler.
        
        initWifiScanTask();  // create pre-defined wifi scan task to be invoked upon movement sensor indication.
        addWifiScanTask();
        
        mLogF = new LogFile(this);
        
        _sinstance = this;
        
        mMSApp.setMSMan(this);
        
        MSAppLog.pd(TAG, "_initialize, done...");
    }
    

    /**
     * for now, when start moving, we start all the periodical activities, wifi scan, lte scan, etc.
     * when stop moving, we shutdown all the tasks to save power.
     * Movement is edge-trigger, hence the first time this got called is from movement listener.
     * Later, we set periodical timer to emit movement events until the state is flipped or max events reached. 
     */
    void handleMovement(boolean moving, boolean fromsensorhub){
    	long minfreq = Long.MAX_VALUE;
    	boolean periodicaltask = false;
    	
    	MSAppLog.d(TAG, "handleMovement : moving:" + moving + " . Event from sensorhub or from periodical timer ?" + fromsensorhub);
    	for(MovementTimerTask t : mTasks){
    		if(t.mFreq < minfreq){
    			minfreq = t.mFreq;
    		}
    		
    		if(fromsensorhub){  // movement 
    			MSAppLog.d(TAG, "handleMovement : movement event from sensor hub edge trigger, invoke registered tasks");
    			t.resetTimeUponNewMovement(moving, mStartDuration);  // sensor hub cycle is in  seconds
    			t.invokeMovementAction(moving);   // invoke one immediately upon sensor hub indication.
    			periodicaltask = true;
    		}else if(t.isCycleMatured(System.currentTimeMillis())){   // if it is from periodical timer, check mature first.
    			MSAppLog.d(TAG, "handleMovement : movement event from periodical timer and task cycle matured, invoke registered tasks");
    			t.invokeMovementAction(moving);
    			periodicaltask = true;
    		}else{
    			MSAppLog.d(TAG, "handleMovement : periodical timer indication cycle not matured, NULL");
    		}
    	}
    	
    	// if we have repeative task, schedule the timer with min freq
    	if(periodicaltask){
    		MSAppLog.d(TAG, "handleMovement : re-schedule periodical timer to emit movement events:" + minfreq);
    		scheduleMovementAlarmTimer(minfreq*DateUtils.MINUTE_IN_MILLIS);  // in mill
    	}else{
    		// no periodical task, cancel the timer
    		MSAppLog.d(TAG, "handleMovement : no repeative task, cancel timer");
    		cancelAlarmTimer();
    	}
    }
    
    class WifiScanTask {
    	MovementTimerTask mWifiScanTask;
        public int mWifiScanTaskFreq = 5;   // in minutes....all task repeat limits = 4, so total 20 minutes.
            	
        // the callback registered to movement task, which got called upon event happened.
        MovementListener mWifiScanListener = new MovementListener(){
    		public void onMoving(boolean move, long minutes){
    		  	MSAppLog.d(TAG, "WifiScanTask MovementListener: Device keeps moving for :" + minutes + " : start wifi scan...");
    			if(minutes <= mWifiScanTaskFreq*4){
    				startBeaconScan();
    			}
    		};
    	};
    	
		public WifiScanTask(Handler hdl){
			mWifiScanTask = new MovementTimerTask("MovementSensor-WifiScan", hdl, TaskAction.PERIODICAL_CALLBACK, mWifiScanTaskFreq, null, mWifiScanListener);
		}
    }
    
    /**
     * should be encapsulated in individual user of the movement sensor.
     */
    void initWifiScanTask(){
    	mWifiTask = new WifiScanTask(mHandler);
    }
    
    
    void addWifiScanTask(){
    	synchronized(mTasks){
    		MSAppLog.d(TAG, "addWifiScanTask :");
    		mTasks.add(mWifiTask.mWifiScanTask);
    	}
    }
    
    void removeWifiScanTask(){
    	synchronized(mTasks){
    		mTasks.remove(mWifiTask.mWifiScanTask);
    	}
    }
       
    /**
     * ask each data source to take a snapshot, called only when entering monitoring state.
     */
    public void takeSnapshot() {
    	MSAppLog.d(TAG, " taking a snapshot of current state ");
    	//mTelMon.snapshot();
    	mBeacon.snapshot();   // only care wifi
    	//mLocMon.snapshot();
    }
    
    /**
     * message handler looper to handle all the msg sent to location manager.
     */
    final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }

    /**
     * the main message process loop.
     * @param msg
     */
    private void processMessage(android.os.Message msg) {
        switch (msg.what) {
        case Msg.NULL:
            MSAppLog.d(TAG, "processMessage() : MSG_NULL");
            break;
        case Msg.CELLTOWER_CHANGED:   // from tel mon when cell tower changed
        	MSAppLog.d(TAG, "processMessage() : CELLTOWER_CHANGED");
            mState.onEvent(SensorEventType.CELLTOWER, msg.obj, null);
            break;
        case Msg.SENSORHUB_EVENT:
        	MSAppLog.d(TAG, "processMessage() : SENSORHUB_EVENT");
        	mState.onEvent(SensorEventType.SENSORHUB, msg.obj, null);
        	break;
        case Msg.BEACONSCAN_RESULT:   // from tel mon when cell tower changed
        	MSAppLog.d(TAG, "processMessage() : BEACONSCAN_RESULT");
            mState.onEvent(SensorEventType.WIFI, msg.obj, null);
            break;
        case Msg.ACCL_WALKING:
        	MSAppLog.d(TAG, "processMessage() : ACCL_WALKING...start a scan");
        	//mBeacon.startBeaconScan();  
            mState.onEvent(SensorEventType.WALKING, msg.obj, null);
            break;
        case Msg.LOCATION_UPDATE:
        	MSAppLog.d(TAG, "processMessage() : LOCATION_UPDATE");
        	mState.onEvent(SensorEventType.LOCATION, msg.obj, null);
        	break;
        case Msg.DIFFTIMER_EVENT:
        	MSAppLog.d(TAG, "processMessage() : DIFFTIMER_EVENT");
        	mState.onEvent(SensorEventType.TIMER, msg.obj, null);
        	break;
        
        case Msg.GPS_STREAM:
        	MSAppLog.d(TAG, "processMessage() : GPS_STREAM");
        	mState.onEvent(SensorEventType.MOVING, null, null);
        	break;
        case Msg.KEEPING_MOVEMENT_STATUS:
        	MSAppLog.d(TAG, "processMessage() : KEEPING_MOVEMENT_STATUS:");
        	//handleMovement(mMoving, false);
        	break;
        case Msg.START_WIFISCAN:
            MSAppLog.pd(TAG, " START_WIFISCAN msg recved, start wifi scan");
            startBeaconScan();
            break;
        case Msg.STOP_WIFISCAN:
            setPendingIntent(null);  // nullify mTimerExpiredIntent so bouncing cell map can be cleared.
            stopLocationMonitor();   // stop after getting location fix.
            mHandler.removeMessages(Msg.START_WIFISCAN);
            break;
        default:
            MSAppLog.d(TAG, "processMessage() : unknown msg::" + msg.what);
            break;
        }
    }

    /**
     * cell tower changed event comes from telmon, means user is moving, start location discovery and detection.
     * Update previous location times, and schedule 15 min timer to track the new location.
     * If the cell tower belongs to any meaningful location, start active detection logic.
     */
    private void handleCelltowerChange() {
     	if (isNewCellTower(mTelMon.getValueJSONString())) {
     		//MSAppLog.pd(TAG, "handleCelltowerChange...");
     	}
    }

    /**
     * We have another filter here to filter out bouncing cells...not used for now.
     * @param curcell current cell tower key value string
     * @return true, always think cell change event is valid in this module.
     */
    private boolean isNewCellTower(String curcell) {
    	return true;
    }

    public void startBeaconScan(){
    	mBeacon.startBeaconScan(); // scan and store beacon's around POI.
    }

    /**
     * cancel the existing on-going 15 minute timer, if any...in order for scheduling a new one.
     */
    void cancelAlarmTimer() {
        mAlarmMan.cancel(mTimerExpiredIntent);
        if (mTimerExpiredIntent != null) {
            MSAppLog.d(TAG, "cancelAlarmTimer : canceling existing timer" + mTimerExpiredIntent.toString());
            mTimerExpiredIntent.cancel();
            mTimerExpiredIntent = null;
        }
    }
    
    /**
     * set up a diff timer pending intent to alarm manager to fire upon di
     */
    void scheduleDiffAlarmTimer(long delay) {
        // first, cancel all the pending alarm
        cancelAlarmTimer();

        long nowtime = System.currentTimeMillis();
        // schedule a new alarm with new pending intent
        Intent timeoutIntent = new Intent(ALARM_TIMER_DIFF_EXPIRED); // create intent to record that the user kept moving for some time.
        timeoutIntent.putExtra(ALARM_TIMER_SET_TIME, nowtime );
        setPendingIntent(timeoutIntent);

        //mAlarmMan.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()+delay, mTimerExpiredIntent);
        mAlarmMan.set(AlarmManager.RTC_WAKEUP, nowtime+delay, mTimerExpiredIntent);
        MSAppLog.pd(TAG, "scheduleDiffAlarmTimer : diff timer " + delay/60000 + " min later from : " + nowtime);
    }

    /**
     * set up a pending intent to alarm manager to fire upon cycle expired.
     */
    void scheduleMovementAlarmTimer(long cycle) {
        // first, cancel all the pending alarm
        cancelAlarmTimer();

        long nowtime = System.currentTimeMillis();
        // schedule a new alarm with new pending intent
        Intent timeoutIntent = new Intent(ALARM_TIMER_MOVEMENT_EXPIRED); // create intent to record that the user kept moving for some time.
        timeoutIntent.putExtra(ALARM_TIMER_SET_TIME, nowtime );
        setPendingIntent(timeoutIntent);

        //mAlarmMan.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()+delay, mTimerExpiredIntent);
        mAlarmMan.setInexactRepeating(AlarmManager.RTC_WAKEUP, nowtime+cycle, cycle, mTimerExpiredIntent);
        MSAppLog.pd(TAG, "scheduleMovementAlarmTimer : repeative movement timer: " + cycle/60000 + " min later from : " + nowtime);
    }
    
    /**
     * if null pending intent, means no pending 15 min timer schedule
     */
    public PendingIntent getPendingIntent() {
        return mTimerExpiredIntent;
    }

    /**
     * cancel the current timer and re-schedule a new one. Vs. do nothing if already one exists.
     * wrap the intent into pendingintent so alarmer can fire it.
     */
    private final void setPendingIntent(Intent intent) {
        if (intent != null) {
        	// Retrieve a PendingIntent that will perform a broadcast, like calling Context.sendBroadcast(). send() will exec the action.
            mTimerExpiredIntent = PendingIntent.getBroadcast(this, 0, intent,  PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            MSAppLog.pd(TAG, "setPendingIntent : setting with intent :" + intent.toString());
        } else {
            mTimerExpiredIntent = null;
            MSAppLog.d(TAG, "setPendingIntent : nullify the pending intent :");
        }
    }
    
    /**
     * the timer expired bcast event recver and handler.
     */
    final static private class TimerEventReceiver extends BroadcastReceiver {
        private final Context mContext;
        private final Handler mHandler;
        private boolean mStarted = false;

        TimerEventReceiver(final Context ctx, final Handler handler) {
            this.mContext = ctx;
            this.mHandler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            MSAppLog.d(TAG, "TimerEventReceiver : onReceiver : Got timer expired Event :" + action);
            Message msg = mHandler.obtainMessage();

            if(ALARM_TIMER_DIFF_EXPIRED.equals(action)){
            	msg.what = Msg.DIFFTIMER_EVENT;
            	MSAppLog.d(TAG, "TimerEventReceiver : recved diff timer, post diff timer event to get a diff");
            }else if (ALARM_TIMER_MOVEMENT_EXPIRED.equals(action)){
            	msg.what = Msg.KEEPING_MOVEMENT_STATUS;   // 
            	MSAppLog.d(TAG, "TimerEventReceiver : recved periodical movement timer, post movement event to handle movement");
            }else if (ALARM_TIMER_STARTSCAN.equals(action)) {
                msg.what = Msg.START_WIFISCAN;
                MSAppLog.d(TAG, "TimerEventReceiver : movement timer expired, start scan");
            }
            mHandler.sendMessage(msg);
        }

        final void registerTimerEventHandler() {
            if (!mStarted) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(ALARM_TIMER_DIFF_EXPIRED);
                filter.addAction(ALARM_TIMER_MOVEMENT_EXPIRED);
                filter.addAction(ALARM_TIMER_STARTSCAN);
                mContext.registerReceiver(this, filter);
                mStarted = true;
                MSAppLog.d(TAG, "TimerEventReceiver : started and registered");
            }
        }

        final void unregisterTimerEventHandler() {
            if (mStarted) {
                mContext.unregisterReceiver(this);
                mStarted = false;
                MSAppLog.d(TAG, "TimerEventReceiver : stoped and unregistered");
            }
        }
    }

    /**
     * stop location update after location discovered.
     */
    private void stopLocationMonitor() {
        MSAppLog.d(TAG, "LSMan Stop listening location update...");
    }
   
    /**
     * check whether we have good data connectivity, drop location update when data connectivity is bad as
     * google will give last known location when data connectivity is not good
     * Data connection: either Telephony data connection or Wifi Connection.
     * @return true if data connectivity good, false else
     */
    public boolean isDataConnectionGood() {
        return ( mTelMon.isDataConnectionGood() || mBeacon.isWifiConnected());
    }
    
    /**
     * return the phone's device Id
     * @return string of phone device id
     */
    public String getPhoneDeviceId() {
        return mTelMon.getPhoneDeviceId();
    }
    
    /**
     * playback a voice to notify the user about the movement status
     */
    public void soundState(){
    	if(mState.isMovingState()){
    		soundState(true);
    	}else{
    		soundState(false);
    	}
    }
    
    public void soundState(boolean driving){
    	int resid = R.raw.station;
    	if(driving){
    		resid = R.raw.driving;
    	}
    	MediaPlayer mediaPlayer = MediaPlayer.create(this, resid);
    	mediaPlayer.start(); // no need to call prepare(); create() does that for you
    }
}
