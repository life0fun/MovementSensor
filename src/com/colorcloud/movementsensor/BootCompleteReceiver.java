
package com.colorcloud.movementsensor;

import static com.colorcloud.movementsensor.Constants.BOOT_COMPLETE;
import static com.colorcloud.movementsensor.Constants.INTENT_PARM_STARTED_FROM_ADA;
import static com.colorcloud.movementsensor.Constants.INTENT_PARM_STARTED_FROM_BOOT;
import static com.colorcloud.movementsensor.Constants.INTENT_PARM_STARTED_FROM_VSM;
import static com.colorcloud.movementsensor.Constants.VSM_INIT_COMPLETE;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;

/**
 *<code><pre>
 * CLASS:
 *  Boot complete recevier
 *
 * RESPONSIBILITIES:
 * 	Start the service upon boot
 *
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */
public class BootCompleteReceiver extends BroadcastReceiver {

    private static final String TAG = "MOV_Boot";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Intent myIntent = new Intent(context, MovementSensorManager.class);
        //Intent myIntent = new Intent(context, NoCellLocationManager.class);
        if (BOOT_COMPLETE.equals(action)) {
            myIntent.putExtra(INTENT_PARM_STARTED_FROM_BOOT, true);
            MSAppLog.pd(TAG, " movementsensor started: from boot complete");
        } else if (VSM_INIT_COMPLETE.equals(action)) {
            myIntent.putExtra(INTENT_PARM_STARTED_FROM_VSM, true);
            MSAppLog.pd(TAG, " movementsensor started: from boot VSM init complete");
        }
        
        // we are virtual sensor and we need to notify VSM we are up and running once VSM comes up.
        ComponentName component = context.startService(myIntent);
        if (component != null) {
            MSAppLog.i(TAG, " started: " + component.toShortString());
        } else {
            MSAppLog.i(TAG, " start failed.");
        }
    }
}
