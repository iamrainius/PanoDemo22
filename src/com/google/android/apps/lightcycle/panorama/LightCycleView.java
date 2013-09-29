package com.google.android.apps.lightcycle.panorama;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import android.R.integer;
import android.R.string;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.FloatMath;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.apps.lightcycle.camera.CameraApiProxy.CameraProxy;
import com.google.android.apps.lightcycle.camera.CameraPreview;
import com.google.android.apps.lightcycle.panorama.MessageSender.MessageSubscriber;
import com.google.android.apps.lightcycle.sensor.SensorReader;
import com.google.android.apps.lightcycle.storage.LocalSessionStorage;
import com.google.android.apps.lightcycle.storage.PhotoMetadata;
import com.google.android.apps.lightcycle.util.Callback;
import com.google.android.apps.lightcycle.util.LG;
import com.google.android.apps.lightcycle.util.LocationProvider;

public class LightCycleView extends GLSurfaceView implements
		View.OnClickListener {

	private static final String TAG = LightCycleView.class.getSimpleName();
	private final MovingSpeedCalibrator calibrator = new MovingSpeedCalibrator();
	private Handler imageFileWriteHandler = new Handler();
	private IncrementalAligner mAligner;
	private CameraPreview mCameraPreview;
	private boolean mCameraStopped = true;
	private Context mContext;
	private int mCurrentPhoto = 0;
	private boolean mEnableTouchEvent = true;
	private boolean mFirstFrame = false;
	private Handler mHandler;
	private boolean mLastZoom;
	
	private LocalSessionStorage mLocalStorage;
	private  LocationProvider mLocationProvider = null;
	private MessageSender mMessageSender = new MessageSender();
	private FileWriter mOrientationWriter = null;
	
	PictureCallback mPictureCallback = new PictureCallback() {
		
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			mCameraPreview.initCamera(mPreviewCallback, 320, 240, true);
			mTakingPhoto = false;
			writePictureToFileAsync(data);
			mFirstFrame = true;
		}
	};
	
	PreviewCallback mPreviewCallback = new PreviewCallback() {
		
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			//xxx
		}
	};
	
	private LightCycleRenderer mRenderer;
	private SensorReader mSensorReader;
	ShutterCallback mShutterCallback = new ShutterCallback() {
		
		@Override
		public void onShutter() {
		}
	};
	
	private boolean mTakingNewPhoto = false;
	private boolean mTakingPhoto = false;
	
	PictureCallback mTestCallback = new PictureCallback() {
		
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
			
		}
	};
	
	private Vector<Integer> mThumbnailTextureIds = new Vector<Integer>(100);
	private float mZoomCurrentDistance;
	private float mZoomStartingDistance;
	private boolean mZooming;
	private Callback<Void> onPhotoTakenCallback;
	private final List<PhotoMetadata> photosTaken = new ArrayList<PhotoMetadata>();
	private final List<float[]> rotationQueue = new ArrayList<float[]>();
	
	public LightCycleView(Activity activity, 
			CameraPreview cameraPreview,
			SensorReader sensorReader, 
			LocalSessionStorage localStorage,
			IncrementalAligner aligner, 
			LightCycleRenderer renderer) {
		this(activity, cameraPreview, sensorReader, localStorage, aligner, renderer, null);
	}
	
	public LightCycleView(Activity activity, 
			CameraPreview cameraPreview,
			SensorReader sensorReader, 
			LocalSessionStorage localStorage,
			IncrementalAligner aligner, 
			LightCycleRenderer renderer,
			SurfaceTexture surfaceTexture) {
		super(activity);
		mContext = activity;
		mSensorReader = sensorReader;
		mLocalStorage = localStorage;
		mAligner = aligner;
		initPhotoStorage(activity);
		
		mCameraPreview = cameraPreview;
		if (mCameraPreview == null) {
			Log.v(TAG, "Error creating CameraPreview.");
			return;
		}
		mCameraPreview.setMainView(this);
		
		mRenderer = renderer;
		mRenderer.setView(this);
		Display display = activity.getWindow().getWindowManager()
				.getDefaultDisplay();
		mRenderer.setSensorReader(display, sensorReader);
		mRenderer.getRenderedGui().subscribe(new MessageSubscriber() {
			
			@Override
			public void message(int arg0, float arg1, String arg2) {
				if (arg0 != 1) {
					return;
				}
				mMessageSender.notifyAll(1, 0.0f, "");
			}
		});	// ~subcribe()
		
		setEGLContextClientVersion(0x2);
		
		if (surfaceTexture != null) {
			final SurfaceTexture sfTexture = surfaceTexture;
			setEGLWindowSurfaceFactory(new EGLWindowSurfaceFactory() {
				@Override
				public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
					egl.eglDestroySurface(display, surface);
				}
				
				@Override
				public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display,
						EGLConfig config, Object nativeWindow) {
					try {
						EGLSurface eglSurface = egl.eglCreateWindowSurface(
								display, config, sfTexture, null);
					} catch (IllegalArgumentException e) {
					}
					
					return null;
				}
			});	
		}
		
		setRenderer(mRenderer);
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		setClickable(true);
		setOnClickListener(this);
		mHandler = new MainHandler(null);
		mLocationProvider = new LocationProvider(
				(LocationManager) activity.getSystemService("location"));
		mSensorReader.setSensorVelocityCallback(new Callback<Float>() {
			
			@Override
			public void onCallback(Float velocity) {
				calibrator.onSensorVelocityUpdate(velocity);
			}
		});
	}

	private float getPinchDistence(MotionEvent event) {
		float f1 = event.getX(0) - event.getX(1);
		float f2 = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(f1 * f1 + f2 * f2);
	}
	
	private void initPhotoStorage(Activity activity) {
		try {
			mOrientationWriter = new FileWriter(
					mLocalStorage.orientationFilePath);
		} catch (IOException e) {
			Log.e(TAG, "Could not create file writer for: "
					+ mLocalStorage.orientationFilePath);
		}
	}
	
	private static double readExposureFromFile(File file) {
		String expTimeString = null;
		double exposureTime = -1.0D;
		
		try {
			expTimeString = (new ExifInterface(file.getAbsolutePath()))
					.getAttribute("ExposureTime");
		} catch (IOException e) {
			exposureTime = -3.0;
		}
		
		if (expTimeString != null) {
			try {
				exposureTime = Double.parseDouble(expTimeString);
			} catch (NumberFormatException e) {
				exposureTime = -2.0;
			}
		}
		
		return exposureTime;
	}
	
	private synchronized void takePhoto() {
		CameraProxy cameraProxy = mCameraPreview.getCamera();
		if (cameraProxy == null) {
			LG.d("Unable to take a photo : camera is null");
			return;
		}
		
		cameraProxy.setPreviewCallbackWithBuffer(null);
		cameraProxy.setPreviewCallback(null);
		cameraProxy.takePicture(mShutterCallback, mTestCallback,
				mPictureCallback);
		
		photosTaken.add(
				new PhotoMetadata(
						System.currentTimeMillis(), 
						null, 
						mLocationProvider.getCurrentLocation(), 
						mSensorReader.getAzimuthInDeg()));
		
		if (onPhotoTakenCallback != null) {
			onPhotoTakenCallback.onCallback(null);
		}
	}
	
	private void writePictureToFileAsync(byte[] data) {
		StringBuilder sb = new StringBuilder();
		float last = 0;
		
		for (int i = 0; i < 9; i++) {
			sb.append(data[i])
			  .append(" ");
			
			last += data[i];
		}
		
		String line = sb.toString() + last + "\n";
		try {
			mOrientationWriter.write(line);
			mOrientationWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void clearRendering() {
		mRenderer.setRenderBlankScreen(true);
		requestRender();
	}
	
	public CameraPreview getCameraPreview() {
		return mCameraPreview;
	}
	
	public Camera.PreviewCallback getPreviewCallback() {
		return mPreviewCallback;
	}
	
	public int getTotalPhotos() {
		return photosTaken.size();
	}
	
	public boolean isProcessingAlignment() {
		return mAligner.isProcessingImages();
	}
	
	@Override
	public void onClick(View v) {
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		xxx
		return super.onTouchEvent(event);
	}

	public void registerMessageSink(MessageSubscriber msgSubscriber) {
		mMessageSender.subscribe(msgSubscriber);
	}
	
	public void requestPhoto(float[] rotation, int numFrames, int photoIndex) {
		if (mTakingPhoto) {
			return;
		}
		
		rotationQueue.add(rotation);
		mHandler.sendEmptyMessage(4);
		mThumbnailTextureIds.setSize(Math.max(numFrames + 1,
				mThumbnailTextureIds.size()));
		mThumbnailTextureIds.set(numFrames, photoIndex);
		mTakingPhoto = true;
		writeOrientationString(rotation);
	}

	private class MainHandler extends Handler {
		private MainHandler() {}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 3:
				mTakingPhoto = false;
				if (mCameraPreview == null) {
					return;
				}
				mCameraPreview.startPreview();
				break;
			case 4:
				takePhoto();
				break;
			}
		}
		
		
	}


}
