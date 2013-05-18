package com.colorcloud.movementsensor;

interface IMovementSensor {
	int registerListener(in int startmoving_interval, in String intent_action_string);
	int unregisterListener(in int hdl);
}