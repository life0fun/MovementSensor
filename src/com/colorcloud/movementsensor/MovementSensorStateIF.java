package com.colorcloud.movementsensor;

import android.os.Bundle;

import com.colorcloud.movementsensor.MovementSensorManager.SensorEventType;

public interface MovementSensorStateIF {
	public void onEnter(String reason);
	public void onExit();
	public void onEvent(SensorEventType t, Object arg, Bundle data);  // javascript type args object. {par1:val1, par2:val2}
	public MovementSensorStateIF getState();
	public long getEnterTime();
}
