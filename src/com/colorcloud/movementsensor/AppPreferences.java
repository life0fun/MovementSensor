
package com.colorcloud.movementsensor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;


/**
*<code><pre>
* CLASS:
*  To handle App setting and preference
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
public class AppPreferences {

    private static final String TAG = "MOV_PREF";
    public static final String POI = "poi";

    private MovementSensorApp mLSApp;
    private SharedPreferences mPref;

    public AppPreferences(MovementSensorApp lsapp) {
        mLSApp = lsapp;
        mPref = mLSApp.getSharedPreferences(Constants.PACKAGE_NAME, 0);
    }

    /**
     * Get the value of a key
     * @param key
     * @return
     */
    public String getString(String key) {
        return mPref.getString(key, null);
    }

    /**
     * Set the value of a key
     * @param key
     * @return
     */
    public void setString(String key, String value) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putString(key, value);
        editor.commit();
    }
}
