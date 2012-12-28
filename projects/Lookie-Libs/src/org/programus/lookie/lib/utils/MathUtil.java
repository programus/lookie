package org.programus.lookie.lib.utils;

public class MathUtil {
	public static float calculateAngle(float[] values) {
		double x = -values[0];
		double y = -values[2];
		double dis = Math.sqrt(x * x + y * y);
		double r = Math.asin(y / dis);
		double angle = Math.toDegrees(r);
		if (x < 0) {
			angle = 180 - angle;
		}
		if (angle > 180) {
			angle -= 360;
		}
		return (float) angle;
	}
}
