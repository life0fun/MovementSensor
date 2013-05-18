package com.colorcloud.movementsensor;

import android.os.Handler;
import android.os.RemoteException;

import com.colorcloud.movementsensor.IMovementSensor;

/**
 *<code><pre>
 * CLASS:
 *  implements service API handler Interface. For now, service stub is empty.
 *  This is reserved here for future usage. The Intent filter to bind to this service will be defined later.
 *
 * RESPONSIBILITIES:
 *  Expose Location manager service binder APIs so other components can bind to us.
 *
 * COLABORATORS:
 *
 * USAGE:
 * 	See each method.
 *
 *</pre></code>
 */

public class MovementSensorApiHandler extends IMovementSensor.Stub {

    @SuppressWarnings("unused")
    private MovementSensorManager mManager;


    public MovementSensorApiHandler(MovementSensorManager manager) {
        mManager = manager;    // dependency injection, reserved for future extension.
    }

    public int registerListener(int movement_interval, String intentActionString)
    throws RemoteException {
        return 0;
    }

    public int unregisterListener(int hdl) throws RemoteException {
        return 0;
    }

}
