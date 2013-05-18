package com.colorcloud.movementsensor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.colorcloud.movementsensor.R;
import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;

/**
 *<code><pre>
 * CLASS:
 *  The Main UI of the App
 *  however this app will not have a UI in production. This is just for testing, right Haijin?????
 *
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
public class LogViewActivity extends Activity {

    public final static String TAG = "MOV_LOGV";

    public static final int MENUITEM_STATUS = 100;
    public static final int MENUITEM_MOCKLOCATION = 101;
    public static final int MENUITEM_DISPLAY_GRAPH = 102;
    public static final int MENUITEM_DETECTION_RADIUS = 103;
    public static final int MENUITEM_UNITTEST = 104;
    public static final int MENUITEM_WIFI = 105;
    public static final int MENUITEM_OPTIN = 106;

    private MovementSensorApp mMSApp;	// reference to app
    private TestCases mTest;
    
    TextView mLogText;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMSApp = (MovementSensorApp)getApplication();

        setContentView(R.layout.logview);
        
        mLogText = (TextView)findViewById(R.id.logtxt);
        mLogText.setMovementMethod(new ScrollingMovementMethod());
        mLogText.setText(readLog());
        
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    }
    
  
    public String readLogL() {
    	File file = new File("/data/mv.log");
    	ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    	
    	int i = 0;    
    	try {
    		InputStream in = new BufferedInputStream(new FileInputStream(file));
    		i = in.read();
    		while (i != -1){
    			byteArrayOutputStream.write(i);
    			i = in.read();
    	   }
    	   in.close();
    	  
    	} catch (IOException e) {
    	
    	}
    	return byteArrayOutputStream.toString();
    }
    
    public String readLog() {
    	return mMSApp.mMSMan.mLogF.readLog();
    }
    
  
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        MSAppLog.i(TAG, "onActivityResult and starting Loc Man");
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        menu.add(Menu.NONE, MENUITEM_STATUS, 0, "Current Movement Status");
        
        /*
        menu.add(Menu.NONE, MENUITEM_DISPLAY_GRAPH, 0, "Display Location Graph");
        menu.add(Menu.NONE, MENUITEM_WIFI, 0, "Wifi hotspot mode");
        menu.add(Menu.NONE, MENUITEM_OPTIN, 0, "toggle opted in");
        menu.add(Menu.NONE, MENUITEM_UNITTEST, 0, "run unit test online");
        */
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {
        case MENUITEM_STATUS:
            break;
        
        case MENUITEM_DISPLAY_GRAPH:
            //mLSApp.mGraph.buildGraph();
            break;

        case MENUITEM_WIFI:
            break;
       
        case MENUITEM_UNITTEST:
            //mTest.testCheckinData();
            //Toast.makeText(this, mTest.testDataConnection(), 5).show();
            //mTest.testDBOverflow();
            mTest.main();
            break;
        }

        return true;
    }
    
}
