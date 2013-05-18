package com.colorcloud.movementsensor;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.colorcloud.movementsensor.R;
import com.colorcloud.movementsensor.MovementSensorApp.MSAppLog;

import static com.colorcloud.movementsensor.Constants.*;

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
public class MovementSensorUI extends Activity {

    public final static String TAG = "MOV_UI";

    public static final int MENUITEM_STATUS = 100;
    public static final int MENUITEM_MOCKLOCATION = 101;
    public static final int MENUITEM_DISPLAY_GRAPH = 102;
    public static final int MENUITEM_DETECTION_RADIUS = 103;
    public static final int MENUITEM_UNITTEST = 104;
    public static final int MENUITEM_WIFI = 105;
    public static final int MENUITEM_OPTIN = 106;

    private MovementSensorApp mMSApp;	// reference to app
    private TestCases mTest;
    
    TextView mSensorState;
    TextView mUserState;
    
    private ToggleButton mONOFF;
    private Button mUserMove;
    private Button mUserWalk;
    private Button mUserStation;
    private Button mLogfile;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMSApp = (MovementSensorApp)getApplication();
        mTest = new TestCases(mMSApp);

        setContentView(R.layout.main);
        
        mSensorState = (TextView)findViewById(R.id.sensorstate);
        mUserState = (TextView)findViewById(R.id.userstate);

        mONOFF = (ToggleButton)findViewById(R.id.onoff);
        mONOFF.setChecked(true);
        mONOFF.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mONOFF.isChecked()) {
                	startRunning();
                } else {
                	stopRunning();
                }
            }
        });
                   
        mUserMove = (Button)findViewById(R.id.usermove);
        mUserMove.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	setUserState(MOVING, 1);
            }
        });
        
        mUserWalk = (Button)findViewById(R.id.userwalk);
        mUserWalk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	setUserState(WALKING, 1);
            }
        });
        
        mUserStation = (Button)findViewById(R.id.userstationary);
        mUserStation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	setUserState(STATIONARY, 0);
            }
        });
        
        mLogfile = (Button)findViewById(R.id.logfile);
        mLogfile.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View v) {
        		startActivity(new Intent(mMSApp, LogViewActivity.class));
			}
		});
        
        // start ls man
        if (mMSApp != null)
            mMSApp.startMovementManager();   // start lsman only after we obtain credential
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	mMSApp.setUI(this);
    	
    	mUserState.setText(mMSApp.mAppPref.getString("USER_STATE"));
    	if(mMSApp.mMSMan != null){
    		mSensorState.setText(mMSApp.mMSMan.mState.getState());
    	}
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mMSApp.setUI(null);
    	mMSApp.mAppPref.setString("USER_STATE", (String)mUserState.getText());
    }
    
    private void setUserState(String text, int state){
    	mUserState.setText(text);
    	mMSApp.mAppPref.setString("USER_STATE", text);
    	mMSApp.mMSMan.mState.setUserState(state);  // 0 is station, 1 is mov
    }
    
    public void setSensorState(final String state){
    	Runnable updatestate = new Runnable() {
    		public void run() {
        		mSensorState.setText(state);  // java closure ?
    		}
    	};
    	runOnUiThread(updatestate);
    	//new updateStateTask().execute(state);
    }
    
    /**
     * use AsynTask, whose result is pubed on UI thread, to update UI
     * caller gives params, post execute pub final result to UI to update.
     * 1. The task instance must be created on the UI thread.
     * 2. execute() must be called on the UI thread
     * 3. the task can only be executed once.
     */
    private class updateStateTask extends AsyncTask<String, Void, String>{
    	@Override
    	protected void onPreExecute() {
    	}
    	@Override
    	protected String doInBackground(String... params){
    		//the result of the computation must be returned by this step and will be passed back to the last step
    		return params[0];
    	}
    	@Override
    	protected void onPostExecute(String state){
    		mSensorState.setText(state);
    	}
    }
    
    private void getStatus() {
    	mMSApp.mMSMan.soundState();
        StringBuilder sb = new StringBuilder();
        sb.append(" Current in driving ? " + mMSApp.mMSMan.mState.isMovingState());
        // {"Lac":"7824","CntryISO":"us","NetTyp":"GSM","NetOp":"310260","Cid":"15415"}
        // Pattern p = Pattern.compile("{\"Lac\":");
        //String poiuri = "geo:"+"42.289"+","+"-88.000";
        //startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(poiuri)));
        Toast.makeText(this, sb.toString(), 5).show();
        
        mMSApp.mMSMan.mLogF.writeLog(sb.toString());
    }
    
    public void stopRunning() {
    	if (mMSApp != null)
    		mMSApp.mMSMan.stopSelf();
    }
    
    public void startRunning() {
    	 if (mMSApp != null)
             mMSApp.startMovementManager();   // start lsman only after we obtain credential
    }
    
   
    
    private void lauchCheckinApp(String app) {
        if ("Foursquare".equals(app)) {
            //appintent = new Intent(this, FoursquareMainActivity.class);
        } else if ("Facebook".equals(app)) {
            //appintent = new Intent(this, FacebookMainActivity.class);
        }
        // do nothing now !!!
        //startActivityForResult(appintent, 1234);  // request code = 0;
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
        	getStatus();
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
