package com.colorcloud.movementsensor;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;

import com.colorcloud.movementsensor.BeaconManager.WifiId;
import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;
import com.colorcloud.movementsensor.MovementSensorManager.SensorEventType;

/**
 * State object encaps state and event handler. more than just a state flag.
 * when an event happens in any source, state event handler handles it
 * and direct movement manager to take the appropriate actions.
 * Movement Sensor manages all sensor sources and State object decides what to do.
 */

public class MovementSensorState {
	private static final String TAG = "MOV_Stat";
	
	MovementSensorStateIF mStation;
	MovementSensorStateIF mMonitoring;
	MovementSensorStateIF mMoving;
	MovementSensorStateIF mCurState;
	MovementSensorStateIF mPrevState;  // only useful when in monitor state.
	
	MovementSensorStateIF mCurUserState;  // state set by user
	
	private Context mContext;
    private MovementSensorManager mMSMan;
    private Handler mMSManHdl;
    
	public MovementSensorState(MovementSensorManager msman, Handler hdl) {
		mStation = new Station();
		mMoving = new Moving();
		mMonitoring = new Monitoring();
		mCurState = mStation;
		mPrevState = mCurState;
		
		mContext = msman;
		mMSMan = msman;
		mMSManHdl = hdl;
		
		if(mMSMan.mMSApp.isUserMoving()){
			mCurUserState = mMoving;
		}else{
			mCurUserState = mStation;
		}
	}
	
	public boolean isMovingState() {
		return mCurState == mMoving ? true : false;
	}
	public boolean isStationState() {
		return mCurState == mStation ? true : false;
	}
	
	public void setUserState(int n){
		if(n==0){
			mCurUserState = mStation;
		}else if(n==1){
			mCurUserState = mMoving;
		}
		MSAppLog.d(TAG, "setting user state :" + mCurUserState.toString());
		mMSMan.mLogF.writeLog("sensor ", mCurState.toString(), " user "+ mCurUserState.toString(), " set by user");
	}
	
	public void updateUI(String state){
		MSAppLog.d(TAG, "updateUI :" + state + " : " + mMSMan.mMSApp.mUI);
		if(mMSMan.mMSApp.mUI != null){
			mMSMan.mMSApp.mUI.setSensorState(state);
		}
	}
	
	public String getState() {
		if(mCurState != mMonitoring){
			return mCurState.toString();
		}else{
			return mPrevState.toString();
		}
	}
	
	public long getEnterTime() {
		return mCurState.getEnterTime();
	}
	
	/**
	 * handle event common to all state. The action for the event is non-ambiguous and decisive.
	 * @param e  SensorEventType
	 * @param args  key, value bundle of parameters.
	 */
	public void onEvent(SensorEventType e, Object args, Bundle data){
		updateUI("Stationary");
		
		// reject any event if just being brought up within a min
		if(System.currentTimeMillis() - mMSMan.mStartedTime < DateUtils.MINUTE_IN_MILLIS){
			MSAppLog.d(TAG, "event: reject event because we just being brought up " );
			return;
		}
		
		if(e == SensorEventType.MOVING){  // gps stream, car dock, etc.
			MSAppLog.d(TAG, " moving event: change to moving" );
			switchToState(mMoving, "gps_recvd");
			return;
		}
		
		if(e == SensorEventType.SENSORHUB){
			MSAppLog.d(TAG, " sensorhub onstart or onend event: change to monitoring" );
			switchToState(mMonitoring, "");
			return;
		}
		
		if( e == SensorEventType.WALKING){  // directly to station if walking.
			MSAppLog.d(TAG, " walking event: change to station" );
			switchToState(mStation, "walking");  // TODO should be station or monitoring ?
			return;
		}
		
		// handling wifi scan match with definite status. Station and Moving in town. Moving hw is handled in monitoring state.
		if(e == SensorEventType.WIFI){
			if((Integer)args == WifiId.MATCH_TYPE.STATION.getType()){
				switchToState(mStation, "wifi_scan_same");
				return;
			}
		}
		
		// fall over to each state to handle.
		mCurState.onEvent(e, args, data);
	}
	
	private void switchToState(MovementSensorStateIF tostate, String reason){
		if(mCurState == tostate){
			return;  // do nothing if state is the same.
		}
		MSAppLog.d(TAG, "switchToState: " + mCurState.toString() + " >>> " + tostate.toString());
		mCurState.onExit();
		mCurState = tostate;
		mCurState.onEnter(reason);
	}
	
	/**
	 * wifi scan diff pattern is the main method to detect non-driving.
	 */
	public class Station implements MovementSensorStateIF {
		long mEnterTime = 0;
		
		Station() {}
		
		public String toString() {
			if(mMSMan.mAccl.isWalking(60, 10)){  // 10 steps in 60 seconds to be really walking.
				return "Walking";
			}
			else{
				return "Stationary";
			}
		}
		public long getEnterTime() {
			return mEnterTime;
		}
		public MovementSensorStateIF getState() {
			return this;
		}
		public void onEnter(String reason) {
			// broadcast indication saying state changed to Station
			mEnterTime = System.currentTimeMillis();
			updateUI("Stationary");
			if(mPrevState != this){
				mMSMan.soundState(false);
				mMSMan.mLogF.writeLog("sensor Stationary ", " user "+mCurUserState.toString(), reason);
				MovementSensorApp.sendVSMMovementUpdate(mMSMan, "Station");
			}
			mPrevState = this;
		}
		
		public void onExit() { }
		
		public void onEvent(SensorEventType t, Object args, Bundle data) {
			switch (t){
			case SENSORHUB:
				if((Boolean)args){ 
					MSAppLog.d(TAG, "onEvent: SENSORHUB MOVE " + toString()); 
					switchToState(mMonitoring, "");
				}
				break;
			case CELLTOWER:
				if((Boolean)args){ 
					MSAppLog.d(TAG, "onEvent: CELLTOWER CHANGE " + toString()); 
					switchToState(mMonitoring, "");
				}
				break;
			case WIFI:
				int wifistate = (Integer)args;
				MSAppLog.d(TAG, "station onEvent: wifi event :" + wifistate);
				if( wifistate == WifiId.MATCH_TYPE.MONITORING.getType() || wifistate == WifiId.MATCH_TYPE.MOVING_HW.getType() || wifistate == WifiId.MATCH_TYPE.MOVING_TOWN.getType()){
					switchToState(mMonitoring, "");  // every wifi event makes into monitoring
				}
				break;
			case LOCATION:  // do not care location fix in station state.
				MSAppLog.d(TAG, "onEvent: LOCATION, not care. " + toString());
				break;
			default:
				MSAppLog.d(TAG, "onEvent: " + t + " no need to handle in current state:" + this.toString());
				break;
			}
		}
	}
	
	/**
	 * monitoring intermediate state...only care about diff timer expiration.
	 * If loc fix available, we can detect moving or station.
	 * when diff timer(2 min) expired and we do not have any location update. 
	 * This means we are moving fast and google can not locate. Driving fast definitely.
	 * If walking, google fix should be available and we should be to station.
	 */
	public class Monitoring implements MovementSensorStateIF {
		long mEnterTime = 0;
		private final long MONITOR_TIME = 1*DateUtils.MINUTE_IN_MILLIS;
		
		Monitoring() {}
		int mLocUpdates = 0;
		int mWifiMoves = 0;
		boolean mDiffTimerExpired = false;
		
		public String toString() {
			return "Monitoring";
		}
		public long getEnterTime() {
			return mEnterTime;
		}
		public MovementSensorStateIF getState() {
			return null;
		}
		
		public void onEnter(String reason) {
			mDiffTimerExpired = false;
			mLocUpdates = 0;
			mWifiMoves = 0;
			mEnterTime = System.currentTimeMillis();
			
			MSAppLog.d(TAG, "onEnter Monitoring : take snapshot and scheduling diff timer ::" + this.toString());
			mMSMan.takeSnapshot();   // take a snapshot.
			mMSMan.mLocMon.startLocationUpdate(null);
			mMSMan.scheduleDiffAlarmTimer(MONITOR_TIME);  // 1 minutes timer upon entering monitoring
		}
		
		public void onExit() { 
			MSAppLog.d(TAG, "onExit Monitoring : cancel timer and location req and start passive req");
			mMSMan.cancelAlarmTimer();
			mMSMan.mLocMon.stopLocationUpdate();   // stop location request!!
			mMSMan.mLocMon.startLocationPassiveListener();
		}
		
		public void onEvent(SensorEventType t, Object args, Bundle data) {
			switch (t){
			case TIMER:
				mDiffTimerExpired = true;
				MSAppLog.d(TAG, "monitor onEvent:  timer event:  do a wifi scan and let wifi decide");
				
				if(!mMSMan.mBeacon.diff()){
					mWifiMoves = 0;  // reset the count of diffs
				}else{
					mMSMan.startBeaconScan();
				}
				break;
			
			case LOCATION:  // monitor state, cant rely on loc fix to detect moving as loc fix wont be good if you are moving.
				mLocUpdates++;  // ok, we can get location fix, not driving fast.
				MSAppLog.d(TAG, "monitor onEvent: location fix: counts: " + mLocUpdates);
				break;
			case WIFI:   // general mov in-town and station are handled in top...not here in monitoring.
				if(!mDiffTimerExpired){
					MSAppLog.d(TAG, " monitor onEvent wifi :: diff timer not fired....wait for at least 1 min");
					//break;
				}
				
				int wifistate = (Integer)args; 
				
				// 1. wifi match type is diff, not in walking, must be driving.
				// 2. if walking, move back to station.
				// 2. wifi diff, if timer expired, and we do not have location fix, we must be moving fast.
				if(wifistate == WifiId.MATCH_TYPE.RESET_MONITOR.getType()){
					mWifiMoves = 0;  // reset wifi diff count....
				}else if(mDiffTimerExpired && mLocUpdates == 0 && mMSMan.mLocMon.isNetworkProviderEnabled()){ 
					if(wifistate == WifiId.MATCH_TYPE.MOVING_HW.getType()){ 
						MSAppLog.d(TAG, "onEvent: Monitoring...WIFI moving hw and could not get loc fix, definitely driving fast! ");
						switchToState(mMoving, "no_location_fix");
					}
				}else if(wifistate == WifiId.MATCH_TYPE.MOVING_HW.getType()){   // wifi hw come here only when 2 minute all wifi hw
					MSAppLog.d(TAG, "onEvent: Monitoring...WIFI moving hw...set to moving");
					if(!mMSMan.mAccl.isWalking(30, 5)){   // just a sanity check before switching to moving. use half value.
						switchToState(mMoving, "no_wifi_scan");
					}
				}else if(wifistate == WifiId.MATCH_TYPE.MOVING_TOWN.getType()){   // always need to check walking before assert moving
					if(mDiffTimerExpired && !mMSMan.mAccl.isWalking(30, 3)){
						MSAppLog.d(TAG, "onEvent: Monitoring...moving town and not walking, must be driving");
						switchToState(mMoving, "wifi_diff_not_walking");
					}
				}else if(wifistate == WifiId.MATCH_TYPE.MONITORING.getType()){  // watch moving !
					mWifiMoves++;  // monitor means watch for moving
					if(mDiffTimerExpired && !mMSMan.mBeacon.diff()) {  // not completely leave the snapshot wifis
						mWifiMoves = 0;  // reset wifi diff count....
						MSAppLog.d(TAG, "onEvent: Monitoring...WIFI monitoring but last 3 scans are station, reset wifi diff count");
					}else if(mDiffTimerExpired && mWifiMoves >= 3 && !mMSMan.mAccl.isWalking(60, 10)){  // make a big check when monitoring
						// one thrashing will cause two errors, hence diff threshold must be great than 3
						MSAppLog.d(TAG, "onEvent: Monitoring...WIFI monitoring and not walking, must be driving");
						switchToState(mMoving, "wifi_diff_3_times");
					}
				}else{
					mMSMan.startBeaconScan();  // issue a scan
				}
				break;
			default:
				MSAppLog.d(TAG, "onEvent: " + t + " : XXX not interested in current state:" + this.toString());
				break;
			}
		}
	}
	
	/**
	 * Rely on wifi scan match to move back to station mode. when you move to a urban place, you
	 * see a constant # of wifi, and that the KPI to move to station.
	 * SensorHub and no change in location fix will move to monitoring, where loc fix and wifi 
	 * will detect station.
	 */
	public class Moving implements MovementSensorStateIF {
		long mEnterTime = 0;
		
		Moving() {}
		
		public String toString() {
			return "Moving";
		}
		public long getEnterTime() {
			return mEnterTime;
		}
		public MovementSensorStateIF getState() {
			return null;
		}
		
		public void onEnter(String reason) {
			// broadcast indication saying state changed to Moving
			mEnterTime = System.currentTimeMillis();
			updateUI("Moving");
			if(mPrevState != this){
				mMSMan.soundState(true);
				mMSMan.mLogF.writeLog("sensor Moving", "user " + mCurUserState.toString(), reason);
				MovementSensorApp.sendVSMMovementUpdate(mMSMan, "Moving");
			}
			mPrevState = this;
		}
		
		public void onExit() { }
		
		public void onEvent(SensorEventType t, Object args, Bundle data) {
			switch (t){
			case SENSORHUB:
				if(!(Boolean)args){ 
					MSAppLog.d(TAG, "onEvent: SENSORHUB onEnd Move...moving => monitoring"); 
					switchToState(mMonitoring, "");
				}
				break;
			case WALKING:
				MSAppLog.d(TAG, "onEvent: Accel says Walking....moving => station");
				switchToState(mStation, "walking");
				break;
			case LOCATION:    // in moving state, not likely to get good fixes, so it is fundamentally wrong to relies on  google fix. 
				// out of moving state should be detect by walking sensor.
				break;
			case CELLTOWER:
			case WIFI:
				MSAppLog.d(TAG, toString() + " onEvent:" + t.toString()); 
				break;
			default:
				MSAppLog.d(TAG, "onEvent: " + t + " no need to handle in current state:" + this.toString());
				break;
			}
		}
	}
}
