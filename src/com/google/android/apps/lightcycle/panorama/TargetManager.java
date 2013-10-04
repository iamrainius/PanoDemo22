package com.google.android.apps.lightcycle.panorama;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import android.content.Context;
import android.graphics.Point;

import com.google.android.apps.lightcycle.math.Vector3;
import com.google.android.apps.lightcycle.opengl.OpenGLException;
import com.google.android.apps.lightcycle.opengl.Sprite;
import com.google.android.apps.lightcycle.sensor.DeviceOrientationDetector;
import com.google.android.apps.lightcycle.sensor.SensorReader;
import com.google.android.apps.lightcycle.shaders.ScaledTransparencyShader;
import com.google.android.apps.lightcycle.shaders.TargetShader;

public class TargetManager {
	private static final float MAX_ANGLE_THRESHOLD_RAD = degreesToRadians(22.0F);
	private static final float MIN_ANGLE_THRESHOLD_RAD = degreesToRadians(12.0F);
	private float activeTargetAlpha = 0.0F;
	private int activeTargetIndex = -1;
	private AlphaScalePair alphaScalePair = new AlphaScalePair();
	private float[] currentDeviceTransform = null;
	private DeviceOrientationDetector deviceOrientationDetector = null;
	private float halfSurfaceHeight;
	private float halfSurfaceWidth;
	private float hitTargetAlpha = 0.0F;
	private float[] hitTargetTransform = null;
	private Context mContext;
	private boolean mTargetInRange = false;
	private Map<Integer, float[]> mTargets = Collections
			.synchronizedMap(new TreeMap<Integer, float[]>());
	private Sprite nearestSpriteOrtho;
	private float[] projected = new float[4];
	private SensorReader sensorReader = null;
	private float targetHitAngleDeg = 2.0F;
	private TargetShader targetShader;
	private Sprite targetSpriteOrtho;
	private float[] tempTransform = new float[16];
	private ScaledTransparencyShader transparencyShader;
	private float[] unitVector = { 0.0F, 0.0F, -1.0F, 1.0F };
	private Sprite viewFinderSprite;
	private Sprite viewfinderActivatedSprite;
	private Point viewfinderCoord;
	
	public TargetManager(Context context) {
		mContext = context;
	}
	
	private void computeProximityAlphaAndScale(float[] f, Vector3 v,
			AlphaScalePair a) {
		float f1 = (float) Math.acos(new Vector3(-f[8], -f[9], -f[10]).dot(v));
		if (f1 < MIN_ANGLE_THRESHOLD_RAD) {
			a.alpha = 1.0f;
			a.scale = 1.0f;
			return;
		}
		
		if (f1 < MAX_ANGLE_THRESHOLD_RAD) {
			float f2 = MAX_ANGLE_THRESHOLD_RAD - MIN_ANGLE_THRESHOLD_RAD;
			float f3 = 1.0f - (f1 - MIN_ANGLE_THRESHOLD_RAD) / 2;
			a.alpha = 0.0f + f3 * 1.0f;
			a.scale = 0.4f + 0.6f * f3;
			return;
		}
		
		a.alpha = 0.0f;
		a.scale = 0.4f;
	}
	
	private static float degreesToRadians(float degree) {
		return 0.01745329F * degree;
	}
	
	private void drawHitTarget(float[] f1, float[] f2) throws OpenGLException {

	}

	private class AlphaScalePair {
		float alpha;
		float scale;
		
		private AlphaScalePair() {}
	}
}
