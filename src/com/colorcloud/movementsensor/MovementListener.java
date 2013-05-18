package com.colorcloud.movementsensor;

public interface MovementListener {
	public void onMoving(boolean moving, long minutes);
}
