package org.briarproject.android.util;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
import static android.hardware.Camera.Parameters.FOCUS_MODE_EDOF;
import static android.hardware.Camera.Parameters.FOCUS_MODE_FIXED;
import static android.hardware.Camera.Parameters.FOCUS_MODE_MACRO;
import static android.hardware.Camera.Parameters.SCENE_MODE_BARCODE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@SuppressWarnings("deprecation")
public class CameraView extends SurfaceView implements SurfaceHolder.Callback,
		AutoFocusCallback {

	private static final int AUTO_FOCUS_RETRY_DELAY = 5000; // Milliseconds
	private static final Logger LOG =
			Logger.getLogger(CameraView.class.getName());

	private Camera camera = null;
	private PreviewConsumer previewConsumer = null;
	private int displayOrientation = 0, surfaceWidth = 0, surfaceHeight = 0;
	private boolean autoFocus = false, surfaceExists = false;

	public CameraView(Context context) {
		super(context);
	}

	public CameraView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		setKeepScreenOn(true);
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		setKeepScreenOn(false);
		getHolder().removeCallback(this);
	}

	public void start(Camera camera, PreviewConsumer previewConsumer,
			int rotationDegrees) {
		this.camera = camera;
		this.previewConsumer = previewConsumer;
		setDisplayOrientation(rotationDegrees);
		Parameters params = camera.getParameters();
		setFocusMode(params);
		setPreviewSize(params);
		applyParameters(params);
		if (surfaceExists) startPreview(getHolder());
	}

	public void stop() {
		stopPreview();
		try {
			if (camera != null)
				camera.release();
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error releasing camera", e);
		}
		camera = null;
	}

	private void startPreview(SurfaceHolder holder) {
		try {
			camera.setPreviewDisplay(holder);
			camera.startPreview();
			startConsumer();
		} catch (IOException | RuntimeException e) {
			LOG.log(WARNING, "Error starting camera preview", e);
		}
	}

	private void stopPreview() {
		try {
			stopConsumer();
			if (camera != null)
				camera.stopPreview();
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error stopping camera preview", e);
		}
	}

	public void startConsumer() {
		if (autoFocus) camera.autoFocus(this);
		previewConsumer.start(camera);
	}

	public void stopConsumer() {
		if (previewConsumer != null) {
			previewConsumer.stop();
		}
		if (autoFocus) camera.cancelAutoFocus();
	}

	private void setDisplayOrientation(int rotationDegrees) {
		int orientation;
		CameraInfo info = new CameraInfo();
		Camera.getCameraInfo(0, info);
		if (info.facing == CAMERA_FACING_FRONT) {
			orientation = (info.orientation + rotationDegrees) % 360;
			orientation = (360 - orientation) % 360;
		} else {
			orientation = (info.orientation - rotationDegrees + 360) % 360;
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Display orientation " + orientation + " degrees");
		try {
			camera.setDisplayOrientation(orientation);
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error setting display orientation", e);
		}
		displayOrientation = orientation;
	}

	private void setFocusMode(Parameters params) {
		if (Build.VERSION.SDK_INT >= 15 &&
				params.isVideoStabilizationSupported()) {
			LOG.info("Enabling video stabilisation");
			params.setVideoStabilization(true);
		}
		// This returns null on the HTC Wildfire S
		List<String> sceneModes = params.getSupportedSceneModes();
		if (sceneModes == null) sceneModes = Collections.emptyList();
		List<String> focusModes = params.getSupportedFocusModes();
		if (LOG.isLoggable(INFO)) {
			LOG.info("Scene modes: " + sceneModes);
			LOG.info("Focus modes: " + focusModes);
		}
		if (sceneModes.contains(SCENE_MODE_BARCODE)) {
			LOG.info("Setting scene mode to barcode");
			params.setSceneMode(SCENE_MODE_BARCODE);
		}
		if (focusModes.contains(FOCUS_MODE_CONTINUOUS_PICTURE)) {
			LOG.info("Setting focus mode to continuous picture");
			params.setFocusMode(FOCUS_MODE_CONTINUOUS_PICTURE);
		} else if (focusModes.contains(FOCUS_MODE_CONTINUOUS_VIDEO)) {
			LOG.info("Setting focus mode to continuous video");
			params.setFocusMode(FOCUS_MODE_CONTINUOUS_VIDEO);
		} else if (focusModes.contains(FOCUS_MODE_EDOF)) {
			LOG.info("Setting focus mode to EDOF");
			params.setFocusMode(FOCUS_MODE_EDOF);
		} else if (focusModes.contains(FOCUS_MODE_MACRO)) {
			LOG.info("Setting focus mode to macro");
			params.setFocusMode(FOCUS_MODE_MACRO);
			autoFocus = true;
		} else if (focusModes.contains(FOCUS_MODE_AUTO)) {
			LOG.info("Setting focus mode to auto");
			params.setFocusMode(FOCUS_MODE_AUTO);
			autoFocus = true;
		} else if (focusModes.contains(FOCUS_MODE_FIXED)) {
			LOG.info("Setting focus mode to fixed");
			params.setFocusMode(FOCUS_MODE_FIXED);
		} else {
			LOG.info("No suitable focus mode");
		}
		params.setZoom(0);
	}

	private void setPreviewSize(Parameters params) {
		if (surfaceWidth == 0 || surfaceHeight == 0) return;
		float idealRatio = (float) surfaceWidth / surfaceHeight;
		DisplayMetrics screen = getContext().getResources().getDisplayMetrics();
		int screenMax = Math.max(screen.widthPixels, screen.heightPixels);
		boolean rotatePreview = displayOrientation % 180 == 90;
		List<Size> sizes = params.getSupportedPreviewSizes();
		Size bestSize = null;
		float bestScore = 0;
		for (Size size : sizes) {
			int width = rotatePreview ? size.height : size.width;
			int height = rotatePreview ? size.width : size.height;
			float ratio = (float) width / height;
			float stretch = Math.max(ratio / idealRatio, idealRatio / ratio);
			int pixels = width * height;
			float score = width * height / stretch;
			if (LOG.isLoggable(INFO)) {
				LOG.info("Size " + size.width + "x" + size.height
						+ ", stretch " + stretch + ", pixels " + pixels
						+ ", score " + score);
			}
			if (bestSize == null || score > bestScore) {
				bestSize = size;
				bestScore = score;
			}
		}
		if (bestSize != null) {
			if (LOG.isLoggable(INFO))
				LOG.info("Best size " + bestSize.width + "x" + bestSize.height);
			params.setPreviewSize(bestSize.width, bestSize.height);
		}
	}

	private void applyParameters(Parameters params) {
		try {
			camera.setParameters(params);
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error setting camera parameters", e);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		LOG.info("Surface created");
		surfaceExists = true;
		if (camera != null) startPreview(holder);
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (LOG.isLoggable(INFO)) LOG.info("Surface changed: " + w + "x" + h);
		surfaceWidth = w;
		surfaceHeight = h;
		if (camera == null) return; // We are stopped
		stopPreview();
		try {
			Parameters params = camera.getParameters();
			setPreviewSize(params);
			applyParameters(params);
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error getting camera parameters", e);
		}
		startPreview(holder);
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		LOG.info("Surface destroyed");
		surfaceExists = false;
	}

	public void onAutoFocus(boolean success, final Camera camera) {
		LOG.info("Auto focus succeeded: " + success);
		postDelayed(new Runnable() {
			public void run() {
				retryAutoFocus();
			}
		}, AUTO_FOCUS_RETRY_DELAY);
	}

	private void retryAutoFocus() {
		try {
			if (camera != null) camera.autoFocus(this);
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error retrying auto focus", e);
		}
	}
}