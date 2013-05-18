
package com.colorcloud.movementsensor;

import static com.colorcloud.movementsensor.Constants.LS_JSON_WIFIBSSID;
import static com.colorcloud.movementsensor.Constants.LS_JSON_WIFISSID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.text.format.DateUtils;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;
import com.colorcloud.movementsensor.MovementSensorManager.Msg;


/**
 *<code><pre>
 * CLASS:
 *  implements Beacon(wifi and bt) scan logic for better location accuracy and better detection.
 *
 * RESPONSIBILITIES:
 *  turn on wifi and initiate wifi scan if possible.
 *
 * COLABORATORS:
 *   Location sensor manager and location detection.
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */
public final class BeaconManager {
    public static final String TAG = "MOV_Beacon";
    private static final int WIFI_MIN_STRENGTH = -85;  // -80 dbm
    
    private static final IntentFilter mWifiStautsFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
    private static final IntentFilter mWifiScanResultFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    private static final IntentFilter mBTDeviceFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
    private static final IntentFilter mBTScanDoneFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

    private MovementSensorManager mMSMan;
    private Handler mMSManHdl;

    private AtomicInteger mWifiConnState;   // wifi connection state, disconnected(0), connected(1),
    private boolean mWifiScanInitedByMe = false;  // when wifi scan result came by, only take the one inited by me.

    WifiId mWifiId;                 // contains latest scanned wifi bssid and ssid and encapsulated id comparation logic there.
    WifiManager mWifiMan;       // Wifi manager
    protected List<ScanResult> mAPs;
    private enum WifiState { UNINITIALIZED, DISABLE, ENABLE }   // my wifi status
    private boolean mWifiOnByMe = false;

    private BluetoothAdapter mBTAdapter;  // bluetooth adapter.

  
    @SuppressWarnings("unused")
    private BeaconManager() {} // hide default constructor

    /**
     * constructor, remember the reference to location sensor manager and telephone monitor.
     * @param msman
     * @param telmon
     */
    public BeaconManager(MovementSensorManager msman, Handler hdl) {
        mMSMan = msman;
        mMSManHdl = hdl;
        mWifiMan = (WifiManager)mMSMan.getSystemService(Context.WIFI_SERVICE); // should always have bt

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();

        mWifiConnState = new AtomicInteger();
        mWifiConnState.set(0);  // init to disconnected
        mWifiId = new WifiId();  // inited inside constructor.

        mWifiStautsFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mMSMan.registerReceiver(mWifiStatusReceiver, mWifiStautsFilter);
        registerBeaconReceivers();  // always registered listen to wifi scan.
    }

    /**
     *  the wifi scan receiver to get a list of wifi scan mac addr set.
     */
    private BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {  // scan will happen every 5 min.
                if(getWifiScanResultJson()){    // populate the addr set from scan result
                	doneBeaconScan();  // notify the scan result
                }
            }
        }
    };

    /**
     * Wifi status change listener to reset flag to indicate whether wifi turned on by me.
     */
    private BroadcastReceiver mWifiStatusReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {  // Wifi Enabled Disabled
                // all I care is on....if anybody turn on wifi..cancel my pending action for me to always off wifi.
                // ignore state change caused by me ! (on while saved is disable, off when
                if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WifiManager.WIFI_STATE_ENABLED) {
                    MSAppLog.pd(TAG, "mWifiStatusReceiver: get wifi state enable...don't care.");
                } else if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WifiManager.WIFI_STATE_DISABLED) {
                    mWifiOnByMe = false;
                    MSAppLog.pd(TAG, "mWifiStatusReceiver: get wifi state disabled...clear and disable myself also!");
                } else if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WifiManager.WIFI_STATE_DISABLING) {
                    MSAppLog.pd(TAG, "mWifiStatusReceiver: get wifi state disabling...wait for disabled!");
                }
            }
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) { // Wifi connection
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    if (networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                        if (intent.getStringExtra(WifiManager.EXTRA_BSSID) != null) {
                            mWifiConnState.set(1);
                            MSAppLog.pd(TAG, "mWifiStatusReceiver : Wifi connection state changed...connected!" + intent.getStringExtra(WifiManager.EXTRA_BSSID));
                            notifyWifiConnected(intent.getStringExtra(WifiManager.EXTRA_BSSID));
                        }
                    }
                    if (networkInfo.getDetailedState() == NetworkInfo.DetailedState.DISCONNECTED) {
                        mWifiConnState.set(0);
                        MSAppLog.pd(TAG, "mWifiStatusReceiver : Wifi connection state changed...disconnected!");
                        notifyWifiConnected(null);   // send null to indicate wifi disconnected.
                    }
                }
            }
        }
    };

    /**
     * For _EACH_ device, _ONE_ ACTION_FOUND Intent will be broadcast
     */
    private BroadcastReceiver mBTScanReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    MSAppLog.d(TAG, "mBTScanReceiver onRecive() Discovered device :" + device.getName() + ":" + device.getAddress());
                }
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                MSAppLog.d(TAG, "mBTScanReceiver DISCOVERY_FINISHED");
                unregisterBeaconReceivers(mBTScanReceiver);  // un-register this listener
                //doneBeaconScan(mBTScanReceiver);
            }
        }
    };

    /**
     * register listener every time we start a scan, and un-register once scan done.
     */
    private void registerBeaconReceivers() {
        // all async device scan listeners
        mMSMan.registerReceiver(mWifiScanReceiver, mWifiScanResultFilter);  // register receiver, harmless if already registered
        //mLSMan.registerReceiver(mBTScanReceiver, mBTDeviceFilter);
        //mLSMan.registerReceiver(mBTScanReceiver, mBTScanDoneFilter);
    }

    /**
     * remove the registered listener.
     */
    private void unregisterBeaconReceivers(BroadcastReceiver recv) {
        try {
            mMSMan.unregisterReceiver(recv);
        } catch (Exception e) {
            MSAppLog.d(TAG, e.toString());
        }
    }

    /**
     * clean up all registered listeners upon cleanup, when object is being destroyed.
     */
    public void cleanupReceivers() {
        try {
            mMSMan.unregisterReceiver(mWifiStatusReceiver); // wifi on off status
            mMSMan.unregisterReceiver(mWifiScanReceiver);   // wouldn't hurt if unregister two more times
        } catch (Exception e) {
            MSAppLog.d(TAG, e.toString());
        }
    }

    /**
     * inner class that encapsulates wifi bssid and ssid and the logic of comparing bssid and ssid
     */
    static class WifiId {
    	private static final int WIFI_SCANS = 9*3;  // max 9 scans...no overflow  
    	
        Map<String, String> mWifiMap;  // the wifi map of the latest round of scan result. Key is bssid, value is ssid.
        Map<String, String> mLastWifiMap;  // the wifi map of the prev
        Map<String, String> mSnapshotWifiMap;  // the wifi map when snapshot is taken
        JSONArray mWifiSSID;    // all the ssid of the surrounding wifis
        String mWifiConnMac;    // the connected wifi bssid, you can only have one connected wifi at any time.


        // wifi scan match between consecutive scans, standard deviation to find trend. only look at 10 wifi scans.
    	List<Integer> mWifiScanMatch = new ArrayList<Integer>(3*WIFI_SCANS);  // [scan_cnt, match_cnt, match_type]
    	List<Long> mWifiScanTime = new ArrayList<Long>(WIFI_SCANS);
    	
        public WifiId() {
            mWifiMap = null;
            mLastWifiMap = null;
            mSnapshotWifiMap = null;
            
            mWifiSSID = null;
            mWifiConnMac = null;
            
            mWifiScanMatch.clear();
            mWifiScanTime.clear();
        }
        
        /**
         * pass in a set of bssid, find out whether there is any match of passed in bssid in the latest scanned wifi.
         * @param bssid  the bssid arg normally from db poi information.
         * @return true if there are any matches, false otherwise.
         */
        private boolean matchBssid(Set<String> bssidset) {
            return Utils.fuzzyMatchSets(bssidset, mWifiMap.keySet(), true);
        }

        /**
         * pass in a set of ssid, which is poi's bag ssid, and match them against the latest scanned ssids.
         * @param ssidset  the bag ssid set from poi
         * @return true if there are any matches, false otherwise.
         */
        private boolean matchSsid(Set<String> ssidset) {
            return Utils.fuzzyMatchSetInMapValue(ssidset, mWifiMap) > 0 ? true : false;
        }
        
    	/**
    	 * recent defined as last 2 minutes, or last 10 scans, whichever first.
    	 * @return index of the first scan of 2 min ago, guaranteed to be an array index.
    	 */
    	private int getStartIdxOfRecentScan(long now, long winsize){
    		int i;
    		for(i=mWifiScanTime.size()-1; i>=1; i--){
    			if(now-mWifiScanTime.get(i) >= winsize)  // uses 2 min window normally
    				break;
    		}
    		return i;
    	}
    	
    	/**
    	 * see expl in core IP algorithm.
    	 * 1. scan >= 2, >50% match with match count > 2, STATION
    	 * 2. scan < 2, MOVING_HW, use only when google fix not available.
    	 * 3. scan > 2, <50% match, MOVING_TOWN
    	 */
    	static enum MATCH_TYPE {
    		// monitor means watch for moving
    		MONITORING(0), MOVING_HW(1), MOVING_TOWN(10), STATION(1000), UNKNOWN(100000),  // 4 states
    		// following are action code
    		RESET_MONITOR(-1);
    		private final int type;
    		MATCH_TYPE(int type){ this.type = type;}
    		public int getType(){ return type;}
    	}
    	
    	/**
    	 * should I drop this scan due to too close
    	 */
    	boolean dropConsecutiveScans(int scancnt) {
    		boolean ret = false;
    		long now = System.currentTimeMillis();
    		int size = mWifiScanTime.size();
    		if(size <= 1 || mSnapshotWifiMap == null) {
    			return false;   // no drop if just started, or pending to get snap shot.
    		}
    		
    		int prevcnt = mWifiScanMatch.get(3*(size-1));
    		int diff = Math.abs(scancnt - prevcnt);

    		// ignore two wifis within 10 seconds with less than 20% diff
        	if(diff <= (int)(Math.max(scancnt, prevcnt)*0.2) &&   // 4-0, 5-1, 10-2 
        	   now - mWifiScanTime.get(size-1) < 10*1000)  // this 10 sec, really deps on when scan got the cpu.
        	{
             		ret = true;   // the new scan are meaningless, just update scan timestamp and bail out.
        	}
        	MSAppLog.d(TAG, "dropConsecutiveScans: prev:scan:" + prevcnt + " " + scancnt + " drop:" + ret);
        	return ret;
    	}
    	
    	/**
         * cur wifi map already cached as last wifi map inside get scan result callback.
         * calc diff count and update data set
         */
        private boolean updateScanMatch(){
        	long now = System.currentTimeMillis();
        	
        	int scancnt = mWifiMap.size();
        	int prevscancnt = 0;
        	int matchcnt = 0;
        	if(mLastWifiMap != null){
        		prevscancnt = mLastWifiMap.size();
        		matchcnt = Utils.getMatchCount(mWifiMap.keySet(), mLastWifiMap.keySet());
        	}
        	int matchtype = MATCH_TYPE.UNKNOWN.getType();
        	int maxscans = Math.max(scancnt, prevscancnt);
        	
        	if(mWifiScanMatch.size() == 3*WIFI_SCANS){
        		mWifiScanMatch.remove(0);  // after remove the head, the next becomes the new head.
        		mWifiScanMatch.remove(0);
        		mWifiScanMatch.remove(0);
        		mWifiScanTime.remove(0);
        	}
        	
        	// finally, add match type, Note match count is this scan to prev scan.
        	if(matchcnt > 0 && (matchcnt == maxscans || maxscans <= 2)){    // after -80 dbm, only scan 1 or 2.
        		matchtype = MATCH_TYPE.STATION.getType();
        	}else if(matchcnt >= 2 && matchcnt >= (int)(maxscans*0.8)){   // station more than 80% match, incl 3,2
        		matchtype = MATCH_TYPE.STATION.getType(); 
        	}else if(maxscans >= 4 && matchcnt <= (int)(maxscans*0.7) && matchcnt > 0){  // 4-2 is moving in town.
        		if(matchcnt >= 6){
        			// I am sure you are not in a car if you have huge # of matches
        			matchtype = MATCH_TYPE.STATION.getType();  // XXX set to unknown or station ?
        		}else{
        			matchtype = MATCH_TYPE.MOVING_TOWN.getType(); // 4-2 is moving in town
        		}
        	}else if(maxscans >= 3 && matchcnt < (int)(maxscans*0.7) && matchcnt > 0){
        		matchtype = MATCH_TYPE.MOVING_HW.getType(); // 3-1 is moving in hw, 
        	}
        	else if(matchcnt == 0 || scancnt == 0){  // currently scan nothing, moving hw.
        		matchtype = MATCH_TYPE.MOVING_HW.getType(); // if no match, must be driving or walking.
        	}else{
        		matchtype = MATCH_TYPE.UNKNOWN.getType();    // 2-1 should be station, not sure.
        	}
        
        	mWifiScanTime.add(now);			  // autoboxing
        	mWifiScanMatch.add(scancnt); 	  // first, add # of wifi scanned, autoboxing
        	mWifiScanMatch.add(matchcnt);  	  // second,add # of scan matches, autoboxing
        	mWifiScanMatch.add(matchtype); // assure by availability of google fix
        	MSAppLog.d(TAG, "updateScanMatch: prevscan:scan:match:type " + prevscancnt + " " + scancnt + " " + matchcnt + " " + matchtype);
        	return true;
        }
        
        
        /**
         * core Algorithm for motion detection.
         * 1. for each wifi scan, record diff between this scan and prev scan.
         * 2. assuming scan happens every 1 min, as we have pending location request every one minute.
         * 3. for last 3 scans, if there is no diff(<50%)  with reasonable scan numbers, then you are station or walking.
         * 4. for last 3 scans, if there is no diff, but scan # is low, and google can not get a fix. then you are moving fast(driving).
         * 5. for last 3 scans, if there is huge diff(>50%), with huge scan #(> 8), then you are moving fast in town.
         * 
         * Need to ensure checking the last 3 minutes result.
         *  
         * @return:
         *  1. monitor state: last one deviates from the main stream.
         *  2. unknown, not station, not moving, no need monitoring, wait more scans to decide. 
         */
        private int scanMatchDeviation(){
        	int rettype = MATCH_TYPE.UNKNOWN.getType();  // in-decision
        	int typesum = 0;
        	
        	int matchtype_start = 3*getStartIdxOfRecentScan(System.currentTimeMillis(), 2*DateUtils.MINUTE_IN_MILLIS)+2;  // start = [3*i(scan), 3*i+1(match), 3*i+2(type)]
        	int matchtype_end = mWifiScanMatch.size()-1;
        	int scans = 0;
        	
        	// ensure that at least more than 3 scans.
        	if(matchtype_end < 3*3 || (matchtype_end-matchtype_start) < 2*3){  // need 3 scans, (8-2)/3 + 1 >= 3
        		MSAppLog.d(TAG, "scanMatchDeviation: not enought scans. start:end " + matchtype_start + " " + matchtype_end);
        		return rettype;
        	}
        	
        	for(int i=matchtype_start;i<=matchtype_end;i+=3){ // use = as end is an index.
        		typesum += mWifiScanMatch.get(i);
        		scans++;
        		MSAppLog.d(TAG, "scanMatchDeviation: scan:match:type:typesum " + mWifiScanMatch.get(i-2) + " " + mWifiScanMatch.get(i-1) + " " + mWifiScanMatch.get(i) + " " + typesum);
        	}
        	
        	// case 1, found the dominant type. This is based on last two min history, so stale stuff can sneak in.
        	int sigma = (int)(scans*0.8);   // deviation, 80% good is enough. 6-4, 7-5, ...
        	int tmpsum = typesum;
        	if(tmpsum/MATCH_TYPE.UNKNOWN.getType() >= sigma){   
        		rettype = MATCH_TYPE.UNKNOWN.getType();
        	}
        	tmpsum = tmpsum%MATCH_TYPE.UNKNOWN.getType();  // you changed typesum, Dude!!!
        	if(tmpsum/MATCH_TYPE.STATION.getType() >= sigma){
        		rettype = MATCH_TYPE.STATION.getType();
        	}
        	tmpsum = tmpsum%MATCH_TYPE.STATION.getType();
        	if(tmpsum/MATCH_TYPE.MOVING_TOWN.getType() >= sigma){
        		rettype = MATCH_TYPE.MOVING_TOWN.getType();
        	}
        	tmpsum = tmpsum%MATCH_TYPE.MOVING_TOWN.getType();
        	if(tmpsum/MATCH_TYPE.MOVING_HW.getType() >= sigma){
        		rettype = MATCH_TYPE.MOVING_HW.getType();
        	}
        	
        	// differentiate on detecting moving and station. Station can handle by walking quickly.
        	// detect moving eagerly. station can only be returned when last 5 minute all station.
        	
        	// for detect moving eagerly, check eagerly not station condition.
        	if(isLast3ScansSame(matchtype_end, typesum, 2*DateUtils.MINUTE_IN_MILLIS)  // last 3 scan the same.
        		&& mWifiScanMatch.get(matchtype_end) != MATCH_TYPE.STATION.getType())  // not station
        	{  // last 3 the same and more than 2 minutes, but not station.
        		rettype = mWifiScanMatch.get(matchtype_end);
        		MSAppLog.d(TAG, "scanMatchDeviation: last 3 scans the same: " + rettype);  // moving hw should fall into here.
        		return rettype;
        	}
        	
        	MSAppLog.d(TAG, "scanMatchDeviation: rettype and last2scans: " + rettype + ":" + mWifiScanMatch.get(matchtype_end) + mWifiScanMatch.get(matchtype_end-3));
        	// last scan diffs from majority, put into monitor
        	if(rettype != mWifiScanMatch.get(matchtype_end)){
        		if(mWifiScanMatch.get(matchtype_end) == mWifiScanMatch.get(matchtype_end-3) && 
        		   mWifiScanMatch.get(matchtype_end) == MATCH_TYPE.STATION.getType()){
        			MSAppLog.d(TAG, "scanMatchDeviation: last 2 scans are station, wait more for either station or monitor...");
        			rettype = MATCH_TYPE.UNKNOWN.getType();   // last 2 scans are station, set to unknown, wait more.
        		}else if(mWifiScanMatch.get(matchtype_end) != MATCH_TYPE.STATION.getType()){  // monitor is for moving detect quickly.
        			// if last scan type diff than the majority, edge, trigger monitoring,
        			MSAppLog.d(TAG, "scanMatchDeviation: last scan type diff from the main, set to monitoring: " + rettype);
        			rettype = MATCH_TYPE.MONITORING.getType();  // XXX two of this will trigger moving if not walking
        		}
        	}
        	
        	// all scans of last 4, or last 2 min are moving hw, detect it
        	if(lastXScansType(4, 90*1000) == MATCH_TYPE.MOVING_HW.getType()){
        		rettype = MATCH_TYPE.MOVING_HW.getType();
        		MSAppLog.d(TAG, "scanMatchDeviation: last 5 scans are moving hw, set to movign: " + rettype);
        	}
        	
        	// for station detection, to avoid thrashing, need to make sure all the scan type of last 3 min the same.
        	// condition from less strick to more strict.
        	if(lastXScansType(6, 40*1000) == MATCH_TYPE.STATION.getType() || rettype == MATCH_TYPE.STATION.getType()){  // all scans of last 6 or last 2 min
        		if(rettype == MATCH_TYPE.STATION.getType() && lastXMinScanType(3*DateUtils.MINUTE_IN_MILLIS)){ 
        			MSAppLog.d(TAG, "scanMatchDeviation: all 3 min are station, set to station.");
        			rettype = MATCH_TYPE.STATION.getType();   // the only place station got set. last 3 min all station.
        		}else if(lastXScansType(6, 40*1000) == MATCH_TYPE.STATION.getType()){
        			MSAppLog.d(TAG, "scanMatchDeviation: Not all 3 min are station, only last 6 scan, set to clear monitor flag.");
        			rettype = MATCH_TYPE.RESET_MONITOR.getType();  // not all 3 min station, only last 6 scan all station, reset.
        		}else{
        			MSAppLog.d(TAG, "scanMatchDeviation: Not all 3 min are station and not last 6 scan, only major station. unknown.");
        			rettype = MATCH_TYPE.UNKNOWN.getType();  // only majority is station, but last 6 not unanymous station, 
        		}
        	}
        	
        	MSAppLog.d(TAG, "scanMatchDeviation: deviation scan type: " + rettype);
        	return rettype;  // unknow by default.
        }
        

        private int getStartIdxOfMatchType(long winsize){
        	// start = [3*i(scan), 3*i+1(match), 3*i+2(type)]
        	int matchtype_start = 3*getStartIdxOfRecentScan(System.currentTimeMillis(), winsize)+2;
        	return matchtype_start;
        }
        
        /**
         * for detecting moving early. check whether the last 3 scans are the same. 
         * Either all of them are station, or all are the same in 2 minutes.
         */
        private boolean isLast3ScansSame(int matchtype_end, int typesum, long tmsize) {
        	boolean ret = false;
        	if(mWifiScanMatch.get(matchtype_end) == mWifiScanMatch.get(matchtype_end-3) 
        	   && mWifiScanMatch.get(matchtype_end) == mWifiScanMatch.get(matchtype_end-6)
        	)
        	{
        		if (mWifiScanTime.get(matchtype_end/3) - mWifiScanTime.get((matchtype_end-6)/3) >= tmsize){
        			ret = true;
        		}
        	}
        	MSAppLog.d(TAG, "isLast3ScansSame : ret=" + ret + ":" + mWifiScanMatch.get(matchtype_end) + ":" + mWifiScanMatch.get(matchtype_end-3) + ":" + mWifiScanMatch.get(matchtype_end-6));
        	return ret;
        }
        
        private int lastXScansType(int scansize, long winsize){
        	int typesum = 0;
        	int ret = MATCH_TYPE.UNKNOWN.getType();  // default to unknown
        	
        	// either last n scans, or last n scans within 2 minutes, whichever comes first.
        	int matchtype_end = mWifiScanMatch.size()-1;
        	int matchtype_start = matchtype_end - (scansize-1)*3;  // 8-(2-1)*3 = 5 = [5,8] 
        	int matchtype_start_2 = getStartIdxOfMatchType(2*DateUtils.MINUTE_IN_MILLIS);  // 2 minutes
        	matchtype_start = matchtype_start > matchtype_start_2 ? matchtype_start : matchtype_start_2;
        	
        	// ensure that at least more than 3 scans.
        	if(matchtype_start < 0){
        		MSAppLog.d(TAG, "lastXScansType: not enought scans. start:end " + matchtype_start + " " + matchtype_end);
        		return ret;
        	}
        	
        	// has to be at least 60 seconds.
        	if(mWifiScanTime.get(matchtype_end/3) - mWifiScanTime.get(matchtype_start/3) < winsize){
        		MSAppLog.d(TAG, "lastXScansType: not enought scans within winsize:start:end " + winsize + " " + matchtype_start + " " + matchtype_end);
        		return ret;
        	}
        	
        	for(int i = matchtype_end; i >= matchtype_start;i-=3){  // use >= as end is an index and count down.
        		typesum += mWifiScanMatch.get(i);
        	}
        	
        	// not unknown, 
        	if(typesum >= MATCH_TYPE.UNKNOWN.getType()){
        		return ret;
        	}
        	
        	// check station
        	if(0 == typesum % MATCH_TYPE.STATION.getType()){
        		ret = MATCH_TYPE.STATION.getType();
        	}else if( typesum < MATCH_TYPE.STATION.getType() && 0 == typesum % MATCH_TYPE.MOVING_TOWN.getType()) {
        		ret = MATCH_TYPE.MOVING_TOWN.getType();
        	}else if(scansize == typesum){   // all scan types are moving hw
        		ret = MATCH_TYPE.MOVING_HW.getType();
        	}
        	MSAppLog.d(TAG, "lastXScansType: start:end " + matchtype_start + " " + matchtype_end + "typesum:ret " + typesum + ":" + ret);
        	return ret;
        }
        
        // all the scans in last 3 minutes are station
        private boolean lastXMinScanType(long winsizw){
        	int typesum = 0;
        	int scans = 0;
        	int matchtype_start = 3*getStartIdxOfRecentScan(System.currentTimeMillis(), winsizw)+2;  // start = [3*i(scan), 3*i+1(match), 3*i+2(type)]
        	int matchtype_end = mWifiScanMatch.size()-1;
        	int ret = MATCH_TYPE.UNKNOWN.getType();  // default to unknown
        	
        	// ensure that at least more than 3 scans.
        	if(matchtype_end < 3*3 || (matchtype_end-matchtype_start) < 2*3){  // need 3 scans, (8-2)/3 + 1 >= 3
        		MSAppLog.d(TAG, "lastXMinScanType: not enought scans. start:end " + matchtype_start + " " + matchtype_end);
        		return false;
        	}
        	
        	for(int i=matchtype_start;i<=matchtype_end;i+=3){  // use = as end is an index.
        		typesum += mWifiScanMatch.get(i);
        		scans++;
        	}
        	
        	MSAppLog.d(TAG, "lastXMinScanType: scans:typesum " + scans + ":" + typesum);
        	// all are station, no unknown, no moving.
        	if(typesum < MATCH_TYPE.UNKNOWN.getType() && (0 == typesum % MATCH_TYPE.STATION.getType())) {
        		return true;
        	}
        	return false;
        }
    }
    
    
    
    /**
     * initiate a beacon scan to devices surround this poi
     * called from LSMan after 15 min when POI location is saved...or upon leave POI.
     * output: update the db table with scanned MAC addr
     * send BEACONSCAN_RESULT to LSMan
     */
    public boolean startBeaconScan() {
        //setWifi(true);   // enable wifi before scan! XXX do not enable wifi in any case.
        mWifiMan.startScan();

        // need user's permission to enable bluetooth
        // if (mBTAdapter != null)
        //    mBTAdapter.startDiscovery();  // no effect if bt not enabled by user permission.

        MSAppLog.d(TAG, "startBeaconScan...to take a snapshot.");
        return true;
    }

    /**
     * a wifi scan available, process it.
     */
    private void doneBeaconScan() {
        if (!mWifiScanInitedByMe) {
            mWifiScanInitedByMe = true;
            //scanresult = mWifiMan.startScan();  // will get another scan result back
            //MSAppLog.d(TAG, "doneBeaconScan: start my own scan return:" + scanresult);
        } else {
            mWifiScanInitedByMe = false;
        }
        
        
        if(!mWifiId.updateScanMatch())
        	return;
        
        // only do deviation when scans are fresh scans.
        int type = mWifiId.scanMatchDeviation();
        // filter out unknown type, even no need to monitor.
        if(type != WifiId.MATCH_TYPE.UNKNOWN.getType()){
        	MovementSensorApp.sendMessage(mMSManHdl, Msg.BEACONSCAN_RESULT, Integer.valueOf(type), null);
        }
    }

    /**
     * notify detection the connected wifi's bssid, at this moment, must be station.
     */
    private void notifyWifiConnected(String bssid) {
        mWifiId.mWifiConnMac = bssid;
        MSAppLog.d(TAG, "notifyWifiConnected: " + mWifiId.mWifiConnMac);
        MovementSensorApp.sendMessage(mMSManHdl, Msg.WIFI_CONNECTED, null, null);
    }

    /**
     * all we care is list of ssids...json is overkill, collect all ssids into address set.
     */
    synchronized boolean getWifiScanResultJson() {
        mAPs = mWifiMan.getScanResults();  // if wifi is off, this could return null
        Map<String, String> scanmap = new HashMap<String, String>();  // create map here.
        if (null != mAPs && mAPs.size() > 0) {
            try {
                for (ScanResult entry : mAPs) {
                	if(entry.level < WIFI_MIN_STRENGTH){ 
                		MSAppLog.d(TAG, "getWifiScanJson: reject weak :" + entry.BSSID + " :" + entry.level);
                		continue;  // do not count in wifi's that are min
                	}
                    scanmap.put(entry.BSSID, entry.SSID);   // put into map.
                    MSAppLog.d(TAG, "getWifiScanJson: " + entry.BSSID + " :" + entry.level);
                }
            } catch (Exception e) {
                MSAppLog.e(TAG, "getWifiJson Exception:" + e.toString());
                scanmap.clear();
            }
        }

        if(mWifiId.dropConsecutiveScans(scanmap.size())) {
        	MSAppLog.d(TAG, "getWifiScanResultJson: drop consecutive similar scans: " + mAPs.size());
        	return false;
        }
        
        if(mWifiId.mWifiMap != null){
        	mWifiId.mLastWifiMap = new HashMap<String, String>(mWifiId.mWifiMap);  // deep copy, clone.
        }
        mWifiId.mWifiMap = scanmap;
        
        /*
        mWifiId.mWifiMap.clear();
        mWifiId.mWifiSSID = new JSONArray();  // this is used to update into db

        if (null != mAPs && mAPs.size() > 0) {
            try {
                for (ScanResult entry : mAPs) {
                	if(entry.level < WIFI_MIN_STRENGTH && mWifiId.mWifiMap.size() > 0){ 
                		MSAppLog.d(TAG, "getWifiScanJson: reject weak :" + entry.BSSID + " :" + entry.level);
                		continue;  // do not count in wifi's that are min, but we need at least one wifi
                	}
                    JSONObject entryjson = new JSONObject();
                    entryjson.put(LS_JSON_WIFISSID, entry.SSID);   // should never be empty
                    entryjson.put(LS_JSON_WIFIBSSID, entry.BSSID);
                    mWifiId.mWifiSSID.put(entryjson);
                    mWifiId.mWifiMap.put(entry.BSSID, entry.SSID);   // put into map.

                    MSAppLog.d(TAG, "getWifiScanJson: " + entry.BSSID + " :" + entry.level);
                }
            } catch (JSONException e) {
                MSAppLog.e(TAG, "getWifiJson Exception:" + e.toString());
                mWifiId.mWifiSSID = null;
                mWifiId.mWifiMap.clear();
            }
        }
        */
        
        // cache to snapshot wifi map 
        if(mWifiId.mSnapshotWifiMap == null){
        	mWifiId.mSnapshotWifiMap = new HashMap<String, String>(mWifiId.mWifiMap);
        }
        
        MSAppLog.d(TAG, "getWifiScanResultJson: scan size: " + mWifiId.mWifiMap.size());
        return true;
    }
    
    /**
     * take a snapshot of current wifi
     */
    public void snapshot(){
    	mWifiId.mSnapshotWifiMap = null;   // clear snapshot only when entering 
    	startBeaconScan();   // start beacon scan.
    }
    
    /**
     * diff the cur scan to the prev snapshot
     * only return true if current wifi scan is completely different than snapshot.
     */
    public boolean diff(){
    	MSAppLog.d(TAG, "diff snapshot and current ");
    	if(mWifiId.mSnapshotWifiMap.size() == 0){
    		return false;   // can not compare, false
    	}
    	
    	int matches =  mapDiff(mWifiId.mSnapshotWifiMap, mWifiId.mWifiMap);
    	MSAppLog.d(TAG, "diff: match count:" + matches +  " snapshot:cur:" + mWifiId.mSnapshotWifiMap.size() + ":" + mWifiId.mWifiMap.size());
    	
    	if(matches > 0){
    		return false;
    	}else{
    		return true;
    	}
    }
    
    /**
     * diff the current wifi with the prev snapshot
     * @return true if diffs from fuzzy match, false otherwise
     */
    private int mapDiff(Map<String, String> cur, Map<String, String> prev){
    	int thresh = (Math.max(cur.size(), prev.size())/2);   // if more than half have changed.
    	return Utils.getMatchCount(cur.keySet(), prev.keySet());
    }
    
    
    

    /**
     * No need to worry about success of turning it on, because if it doesn't come on,
     * we won't be burning any extra battery because WiFi listening is passive.
     *
     * Logic table:
     * getWiFiState()   onAction   onByMeFlag  Result
     * ---------------  --------   ----------  ------
     *       ON           ON           false    leave
     *       ON           OFF          true     turnOff
     *       ON           OFF          false    leave
     *       OFF          ON           true     turnOn
     *       OFF          OFF            x      leave
     * The Wifi State listener will reset onByMeFlag is wifi is on by me and user turned it off.
     * No need to worry if not called in pair....screen off will turn WiFi off always.
     *    Log: WifiService: setWifiEnabled enable=false, persist=true, pid=14941, uid=10006
     * @param onAction on or off
     */
    public void setWifi(boolean onAction) {
        WifiState curstate = WifiState.UNINITIALIZED;

        // reduce states to binary states
        int state = mWifiMan.getWifiState();
        if (state == WifiManager.WIFI_STATE_DISABLED || state == WifiManager.WIFI_STATE_DISABLING) {
            curstate = WifiState.DISABLE;
        } else if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING) {
            curstate = WifiState.ENABLE;
        } else {
            MSAppLog.d(TAG, "setWifi: what I am supposed to do if unknown state");
            return;
        }

        // three do nothing situations.
        // 1.on : it it is already on, do nothing.
        // 2.off: if it is not me who turned wifi on, do nothing.
        // 3.Airplane mode on.
        // 4.Wifi hotspot on
        if ( (onAction  && curstate == WifiState.ENABLE) ||
                (!onAction && curstate == WifiState.DISABLE) ||
                (onAction && Utils.isAirplaneModeOn((Context)mMSMan))
           ) {
            MSAppLog.d(TAG, "setWifi: do nothing : action=" + onAction + ": curWifiState=" + curstate + ": or airplane mode on: or wifi hotspot on.");
            return;
        }

        if (onAction) {
            mWifiOnByMe = true;
            MSAppLog.d(TAG, "setWifi :: ON : saving Current wifi state before enabling : savedstate: " + curstate);
            mWifiMan.setWifiEnabled(true);    // on action
        } else if (mWifiOnByMe){
            WifiInfo wifiInfo = mWifiMan.getConnectionInfo();
            if (wifiInfo != null && SupplicantState.COMPLETED == wifiInfo.getSupplicantState()) {
                MSAppLog.d(TAG, "setWifi :: OFF: not off because wifi connection on:" + mWifiMan.getConnectionInfo());
            } else {
                mWifiOnByMe = false;
                mWifiMan.setWifiEnabled(false);   // restore
                MSAppLog.d(TAG, "setWifi :: OFF: restoring disabled WIFI : savedstate : " + curstate);
            }
        } else {
            MSAppLog.d(TAG, "setWifi :: OFF: leave wifi on because not turned on by me !");
        }
    }

    /**
     * get the current wifi state, reduce the states to two states, on and off.
     * @return
     */
    public boolean isWifiEnabled() {
        int state = mWifiMan.getWifiState();
        if (state == WifiManager.WIFI_STATE_ENABLED || state == WifiManager.WIFI_STATE_ENABLING) {
            return true;
        }
        return false;
    }

    /**
     * get the current wifi connection state,
     * @return true if wifi supplicant state is COMPLETE, false otherwise
     */
    public boolean isWifiConnected() {
        WifiInfo wifiInfo = mWifiMan.getConnectionInfo();
        if (wifiInfo != null && SupplicantState.COMPLETED == wifiInfo.getSupplicantState()) {
            MSAppLog.d(TAG, "isWifiConnected :: Yes, Wifi state is completed: " + wifiInfo.getSupplicantState());
            return true;
        }
        return false;
    }

    /**
     * return the current wifi conn state by checking class state variable directly.
     * State flag in updated inside wifi state change callback.
     * @return wifi conn state, 0:disconnected, 1:connected.
     */
    public int getWifiConnState() {
        return mWifiConnState.get();
    }
}
