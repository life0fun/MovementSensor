package com.colorcloud.movementsensor;


import static com.colorcloud.movementsensor.Constants.LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS;
import static com.colorcloud.movementsensor.Constants.LOCATION_DETECTING_UPDATE_MAX_DIST_METERS;

import java.util.concurrent.atomic.AtomicInteger;

import android.app.PendingIntent;
import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;

/**
 *<code><pre>
 * CLASS:
 *  Wrapper of all underlying location tracking into this location monitor
 *  The side effect of location request is: wifi scan and cell tower event will also happen periodically.
 *
 * RESPONSIBILITIES:
 *  Location tracking and reporting
 *
 * COLABORATORS:
 *	Telephony, GPS, WiFi
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public final class LocationMonitor {

    private static final String TAG = "MOV_LocMon";
    final float MPH2MPM = (20*1609)/(60*60);   // 20 mile per hour convert to meter per seconds=(8)

    private Context mContext;
    private MovementSensorManager mMSMan;
    private AtomicInteger mLocationState;
    private Handler mMSManHdl;
    private LocationManager mLocationManager;

    private LocationProvider mGpsProvider;
    private LocationProvider mNetworkProvider;
    private LocationProvider mPassiveProvider;

    private MyLocationListener mLocListener;
    private MyGpsStatusListener mGpsStatusListener;
    private AtomicInteger mGpsEnabled;

    //private GeoPoint mPoint;
    private Location mLoc = null;
    private Location mLastLoc = null;
    private Location mGpsLastLoc = null;
    private Location mSnapshotLoc = null;    // location snapshoted.
    private boolean mLocFixRcvd = false;
    
    @SuppressWarnings("unused")
    private LocationMonitor() {} // hide default constructor

    /**
     * Constructor called from Location Manager
     * @param ctx
     * @param hdl
     */
    public LocationMonitor(final MovementSensorManager msman, final Handler hdl) {
        mContext = msman;
        mMSMan = msman;
        mLocationState = new AtomicInteger();
        mLocationState.set(0);
        mGpsEnabled = new AtomicInteger();   // default is 0

        mMSManHdl = hdl;
        mLocationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        mNetworkProvider = mLocationManager.getProvider(LocationManager.NETWORK_PROVIDER);
        mGpsProvider = mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
        mPassiveProvider = mLocationManager.getProvider(LocationManager.PASSIVE_PROVIDER);

        mLocListener = new MyLocationListener();
        mGpsStatusListener = new MyGpsStatusListener();
        mLocationManager.addGpsStatusListener(mGpsStatusListener);  // always on GPS status listener
        
        startLocationPassiveListener();  // register passive location listening.
    }

    /**
     * Destructor, clean up all the listeners.
     */
    public void cleanup() {
        stopLocationUpdate();
        mLocationManager.removeGpsStatusListener(mGpsStatusListener);
    }

    /**
     * take a snapshot of current location. Also start periodical location update so we can diff.
     */
    public void snapshot() {
    	mSnapshotLoc = null;
    	mLocFixRcvd = false;
    	requestCurrentLocation();
    }
    
    /**
     * diff the current location with the snapshot location
     * tri-state: 
     *   0: not able to calc due to coarse accuracy
     *   1: can calc, station
     *   2: can calc, moving
     *  
     */
    public int diff() {
    	if(mSnapshotLoc == null){
    		MSAppLog.d(TAG, "failed to get snapshot 2 minutes ago : reset location request");
    		stopLocationUpdate();
    		startLocationPassiveListener();
    		return 0;
    	}
    	
    	if(locDiff(mSnapshotLoc, mLoc) == 2){   // comparing snapshot ?
    		MSAppLog.d(TAG, "locDiff: Yes, move to moving state");
    		return 2;  // moving
    	}
    	else if(locDiff(mSnapshotLoc, mLoc) == 1){
    		MSAppLog.d(TAG, "locDiff: o diff between snapshot loc and cur loc.");
    		return 1;
    	}
    	else{
    		MSAppLog.d(TAG, "locDiff: unknown due to coarse accuracy");
    		return 0;
    	}
    }
    
    /**
     * @return true if network provider enabled, false otherwise
     */
    public boolean isNetworkProviderEnabled() {
        if (mNetworkProvider == null || false == mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            MSAppLog.d(TAG, "isNetworkProviderEnabled : NETWORK_PROVIDER not enabled");
            return false;
        }
        MSAppLog.d(TAG, "isNetworkProviderEnabled : NETWORK_PROVIDER enabled...");
        return true;
    }

    /**
     * @return true if GPS provider enabled, false otherwise
     */
    public boolean isGpsProviderEnabled() {
        if (mGpsProvider == null || false == mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            MSAppLog.d(TAG, "isGpsProviderEnabled : GPS_PROVIDER not enabled");
            return false;
        }
        MSAppLog.d(TAG, "isGpsProviderEnabled : GPS_PROVIDER enabled..");
        return true;
    }


    /**
     * Use location network provider. with GPS Flyweight.
     * @param minTime, the possible time notification can rest, required by google api.
     * @param minDistance, the delta before notification
     * @param intent, the pending intent to be fired once location fix available.
     * @return true of successfully started location tracking. false otherwise.
     */
    public void startLocationPassiveListener() {
    	if(mPassiveProvider != null){
        	mLocationManager.requestLocationUpdates(mPassiveProvider.getName(),
        			LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS, 
        			LOCATION_DETECTING_UPDATE_MAX_DIST_METERS, 
                    mLocListener,
                    mMSManHdl.getLooper()
                   );
        	MSAppLog.d(TAG, "startLocationPassiveListener : using passive provider.");
        }
    }
    
    /**
     * Start frequent location update every one minute. Given the user is driving fast, we need one minute updates.
     * The side effect of location request is: wifi scan and cell tower event will also happen periodically.
     * @param intent : pending intent upon location fix available. Not used for now.
     * @return
     */
    public boolean startLocationUpdate(PendingIntent intent) {
        boolean ret = false;

        if (false == isNetworkProviderEnabled() && false == isGpsProviderEnabled()) {
            MSAppLog.d(TAG, "startLocationUpdate : NETWORK_PROVIDER and GPS not enabled...do nothing.");
            return ret;
        }

        if (mLocationState.get() == 1) {  // bail out if we are already monitoring.
            MSAppLog.d(TAG, "startLocationUpdate :: already in monitoring state...");
            return ret;  // already started
        }
        mLocationState.set(1);
        
        // let's request as many updates as possible during monitoring state.
        if (mNetworkProvider != null && mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            if (intent != null) {
                mLocationManager.requestLocationUpdates(mNetworkProvider.getName(),
                										0,
                										0,
                										intent
                										);
            }

            mLocationManager.requestLocationUpdates(mNetworkProvider.getName(),
                                                    0,
                                                    0,
                                                    mLocListener,
                                                    mMSManHdl.getLooper()
                                                   );
        }else if (mGpsProvider != null && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mLocationManager.requestLocationUpdates(mGpsProvider.getName(),
            										0,
            										0,
                                                    mLocListener
                                                    //mCallerHandler.getLooper()
                                                   );
            MSAppLog.i(TAG, "startLocationUpdate :: taking a measurement with GPS provider");
        }
                
        MSAppLog.d(TAG, "startLocationUpdate : using passive provider.");
        return true;
    }
   
    /**
     * stop location track and remove listener.
     */
    public void stopLocationUpdate() {
        mLocationState.set(0);    // reset state flag upon stopping.
        mLocationManager.removeUpdates(mLocListener);  // no harm if you remove a listener even though it has not been registered.
        mLoc = null;
        mLastLoc = null;
        mSnapshotLoc = null;
        MSAppLog.d(TAG, "stopLocationUpdate :: stop request location update after removed listener...");
    }

    /**
     * even GPS is enabled, can not use it if inside a building where no gps signal.
     */
    void requestCurrentLocation() {
    	if (false == isNetworkProviderEnabled() && false == isGpsProviderEnabled()) {
            MSAppLog.d(TAG, "requestCurrentLocation : NETWORK_PROVIDER and GPS not enabled...do nothing.");
            return;
        }
    	// always use network provider first.
    	if (mNetworkProvider != null && mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
    		mLocationManager.requestSingleUpdate(mNetworkProvider.getName(), mLocListener, mMSManHdl.getLooper());
    		MSAppLog.d(TAG, "requestCurrentLocation : using NETWORK_PROVIDER.");
    	}else if(mGpsProvider != null && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
    		mLocationManager.requestSingleUpdate(mGpsProvider.getName(), mLocListener, mMSManHdl.getLooper()); 
    		MSAppLog.d(TAG, "requestCurrentLocation : using GPS_PROVIDER.");
    	}    	
    }
    
    /**
     * if you are driving, you are not likely to get good fix. Hence, depending on loc fix to detect driving 
     * wont be work by theory. For station detection, we have walking sensor.
     */
    private class MyLocationListener implements LocationListener {
        public void onLocationChanged (Location location) {
         	MSAppLog.pd(TAG, "onLocationChanged() :" + location.toString());
         	mLocFixRcvd = true;  // set the loc fix recvd.
         	
         	// I need to drop loc fix with coarse accuracy !!!
         	if(location.hasAccuracy() && location.getAccuracy() < 100){
         		mLastLoc = mLoc;
                mLoc = location;        // always use the latest measurement
                if(mSnapshotLoc == null){
                	mSnapshotLoc = mLoc;
                	MSAppLog.d(TAG, "onLocationChanged() : set snapshot data: " + mSnapshotLoc.getTime());
                }
                
                // drop stale location fix that carries the same timestamp
                if(mLastLoc == null || mLoc.getTime() - mLastLoc.getTime() < 1000){  // 1000 milli
                	MSAppLog.d(TAG, "onLocationChanged: drop location fix when two consecutive updates: curloc tm:" + mLoc.getTime());
                	return;
                }
         	}
        	
            if(LocationManager.GPS_PROVIDER.equals(location.getProvider()) &&
            	Utils.withinAMinute(mLastLoc.getTime(), mLoc.getTime()) &&
            	locDiff(mLastLoc, mLoc) == 2)
            { // has accuracy, and speed is good.
            	MSAppLog.d(TAG, "onLocationChanged: location changed, GPS streaming while driving. ");
            	// no gps stream based on loc diff.
            	MovementSensorApp.sendMessage(mMSManHdl, MovementSensorManager.Msg.GPS_STREAM, null, null);
            }
            else if(locDiff(mLastLoc, mLoc) == 2){  // moving
            	MovementSensorApp.sendMessage(mMSManHdl, MovementSensorManager.Msg.LOCATION_UPDATE, Boolean.valueOf(true), null);
            }
            else if(locDiff(mLastLoc, mLoc) == 1){  // station
            	MovementSensorApp.sendMessage(mMSManHdl, MovementSensorManager.Msg.LOCATION_UPDATE, Boolean.valueOf(false), null);
            }
            else{  // drop bad fixes
            	MSAppLog.d(TAG, "onLocationChanged: coarse fix...do nothing");
            	MovementSensorApp.sendMessage(mMSManHdl, MovementSensorManager.Msg.LOCATION_UPDATE, Boolean.valueOf(false), null);
            }
        }

        public void onProviderDisabled (String provider) {   }
        public void onProviderEnabled (String provider) { }
        public void onStatusChanged (String provider, int status, Bundle extras) { }
    }
    
    /**
     * take the distance diff between two locs
     * @param preloc
     * @param curloc
     * @return 0: can not compare due to coarse accuracy.
     *         1: station, fix no change
     *         2: moving, fix big change 
     */
    int locDiff(Location preloc, Location curloc){
    	if(preloc == null || curloc == null)
    		return 0;
    	
    	if(preloc.hasAccuracy() && curloc.hasAccuracy() && preloc.getAccuracy() > 100 && curloc.getAccuracy() > 100){
    		return 0;
    	}
    	
    	long seconds = (curloc.getTime() - preloc.getTime())/ DateUtils.SECOND_IN_MILLIS;
    	float dist = curloc.distanceTo(preloc);
    	MSAppLog.d(TAG, " locDiff : dist : seconds = " + dist + " : " + seconds);
    	
    	int ret = 1;  // station
    	if(dist > 200 && seconds <= 5 && curloc.getTime() > preloc.getTime()){
    		ret = 0;  // unknown
    	}else if(dist/seconds > (80*1609/3600)) {  // cant be more than 80 mph, 35m/s
    		ret = 0;    // unknown, un-realistic huge distance.
    	}else if(dist > 100 && seconds > 10 && dist/seconds >= MPH2MPM) { // 20 miles per hour
    		ret = 2;   // moving
    	}
    	return ret;  // default is 1, station.
    }
    
    private class MyGpsStatusListener implements GpsStatus.Listener {
        // GPS enabled callback
        public void onGpsStatusChanged(int event) {
            switch (event) {
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                mGpsEnabled.set(1);
                // reset before setting new loc
                mLoc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);  // take the fix that is available
                MSAppLog.d(TAG, "onGpsStatusChanged: warmup upon First fix available: " + mLoc);
                // now fake a polling time expire to really get the, this should happen only once upon GPS turned on!
                // sendNotification(LocationSensorManager.Msg.START_MONITORING); // Ugly, that is why when gps enabled, we got many locations.
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                mGpsEnabled.set(0);
                MSAppLog.d(TAG, "onGpsStatusChanged: Gps disabled");
                break;
            default:
                break;
            }
            return;
        }
    }
}
