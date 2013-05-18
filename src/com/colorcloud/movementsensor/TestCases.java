package com.colorcloud.movementsensor;

import android.database.sqlite.SQLiteDatabase;

import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;


/**
 *<code><pre>
 * CLASS:
 *  this class wraps all the unit test cases.
 *
 * RESPONSIBILITIES:
 *  for conducting unit test on the phone.
 *
 * COLABORATORS:
 *  LocationSensorApp, LocatinSensorManager, LocationStore, LocationDetection.
 *
 * USAGE:
 *  See each method.
 *
 *</pre></code>
 */

public class TestCases {
    private static final String TAG = "MOV_TEST";

    protected MovementSensorApp     mMSApp;
    SQLiteDatabase mDb;

    @SuppressWarnings("unused")
    private TestCases() {} // hide default constructor

    public TestCases(MovementSensorApp lsapp) {
        mMSApp = lsapp;
    }
    

    /**
     * the main test, test all. the same as python unittest.main()
     */
    public void main() {
    	testDataConnection();
    }

    /**
     * return the data connection status.
     * @return
     */
    public String testDataConnection() {
        return "DataConn="+mMSApp.getMSMan().mTelMon.isDataConnectionGood();
    }

}
