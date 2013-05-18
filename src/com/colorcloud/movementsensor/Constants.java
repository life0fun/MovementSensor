
package com.colorcloud.movementsensor;

import android.net.Uri;
import android.text.format.DateUtils;

/**
 *<code><pre>
 * CLASS:
 *  All the constant referred in this package
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
public final class Constants {

    private Constants() {}  // prevent instantiation

    public static final String TAG_PREFIX = "MOV_";
    public static final String PACKAGE_NAME = Constants.class.getPackage().getName();

    public static final boolean PRODUCTION_MODE = false;
    public static final boolean LOG_INFO = !PRODUCTION_MODE;
    public static final boolean LOG_DEBUG = !PRODUCTION_MODE;
    //public static final boolean LOG_VERBOSE = "1".equals(SystemProperties.get("debug.mot.lslog","0"));
    public static final boolean LOG_VERBOSE = true;

    public static final String DATABASE_NAME = "locationsensor.db";
    public static final long   DATABASE_SIZE = (5*1024*1024);   // avg 10 record per day, 5000 records ~ 800k, set to 5M should be sufficient enough.

    public static final String MOVING = "Moving";
    public static final String WALKING = "Walking";
    public static final String STATIONARY = "Stationary";
    
    /* set to one to disable no detection inside poi */
    public static final int DET_BOUNCING_CELL_SIZE = 1;
    public static final String INVALID_CELL = "-1";

    public static final int FUZZY_MATCH_MIN = 2;   // the min match to fuzzy match be positive.


    public static final long TIMER_HEARTBEAT_INTERVAL = 15 * DateUtils.MINUTE_IN_MILLIS; // heart beat 15min
    public static final long TIMER_ALIVE_INTERVAL = 2*DateUtils.HOUR_IN_MILLIS;  // 2 hours
    
    public static final int LOCATION_DETECTING_UPDATE_MAX_DIST_METERS = 100;  	// 100 meters
    public static final long LOCATION_DETECTING_UPDATE_INTERVAL_MILLIS = 1 * DateUtils.MINUTE_IN_MILLIS;  // 1 minute

    public static final long mStartDuration = 1*DateUtils.MINUTE_IN_MILLIS;   // 1 minute movement to trigger periodical wifi scan
    public static final long mEndDuration = 1*DateUtils.MINUTE_IN_MILLIS;     // stationary for 1 minutes to stop periodical wifi scan.
    
    public static final String ALARM_TIMER_DIFF_EXPIRED = PACKAGE_NAME + ".difftimer";
    public static final String ALARM_TIMER_MOVEMENT_EXPIRED =  PACKAGE_NAME + ".movtimer";
    public static final String ALARM_TIMER_STARTSCAN =  PACKAGE_NAME + ".startscan";
    public static final String ALARM_TIMER_METRIC_EXPIRED = PACKAGE_NAME + ".metrictimer";
    public static final String ALARM_TIMER_SET_TIME = PACKAGE_NAME + ".AlarmScheduledTime";
    public static final String ALARM_TIMER_ALIVE_EXPIRED = PACKAGE_NAME + ".alivetimer";
   
    public static final String BOOT_COMPLETE = "android.intent.action.BOOT_COMPLETED";

    // for beacon sensor json string
    public static final String LS_JSON_TYPE = "type";  // wifi, bt, ...
    public static final String LS_JSON_TIME = "time";
    public static final String LS_JSON_ARRAY = "valueset";
    public static final String LS_JSON_NAME = "name";
    public static final String LS_JSON_ID =   "id";
    public static final String LS_JSON_VALUE = "value";
    public static final String LS_JSON_WIFISSID = "wifissid";
    public static final String LS_JSON_WIFIBSSID = "wifibssid";
    
}
