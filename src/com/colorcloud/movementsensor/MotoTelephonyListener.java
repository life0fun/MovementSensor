package com.colorcloud.movementsensor;

import android.telephony.SignalStrength;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

/**
 *<code><pre>
 * CLASS:
 *  telephony network callback interface
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

public interface MotoTelephonyListener {

    public void onSignalStrengthChangedSignificantly(SignalStrength signalStrength);

    public void onCellTowerChanged(GsmCellLocation location);

    public void onCellTowerChanged(CdmaCellLocation location);

}
