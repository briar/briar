package org.briarproject.android.view;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.briarproject.android.util.PreviewConsumer;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;
import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
import static android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
import static android.hardware.Camera.Parameters.FOCUS_MODE_EDOF;
import static android.hardware.Camera.Parameters.FOCUS_MODE_FIXED;
import static android.hardware.Camera.Parameters.FOCUS_MODE_MACRO;
import static android.hardware.Camera.Parameters.SCENE_MODE_AUTO;
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
		// Use barcode scene mode if it's available
		Parameters params = camera.getParameters();
		params = setSceneMode(camera, params);
		if (SCENE_MODE_BARCODE.equals(params.getSceneMode())) {
			// If the scene mode enabled the flash, try to disable it
			if (!FLASH_MODE_OFF.equals(params.getFlashMode()))
				params = disableFlash(camera, params);
			// If the flash is still enabled, disable the scene mode
			if (!FLASH_MODE_OFF.equals(params.getFlashMode()))
				params = disableSceneMode(camera, params);
		}
		// Use the best available focus mode, preview size and other options
		params = setBestParameters(camera, params);
		// Enable auto focus if the selected focus mode uses it
		enableAutoFocus(params.getFocusMode());
		// Log the parameters that are being used (maybe not what we asked for)
		logCameraParameters();
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

	private Parameters setSceneMode(Camera camera, Parameters params) {
		List<String> sceneModes = params.getSupportedSceneModes();
		if (sceneModes == null) return params;
		if (LOG.isLoggable(INFO)) LOG.info("Scene modes: " + sceneModes);
		if (sceneModes.contains(SCENE_MODE_BARCODE)) {
			params.setSceneMode(SCENE_MODE_BARCODE);
			camera.setParameters(params);
			return camera.getParameters();
		}
		return params;
	}

	private Parameters disableFlash(Camera camera, Parameters params) {
		params.setFlashMode(FLASH_MODE_OFF);
		camera.setParameters(params);
		return camera.getParameters();
	}

	private Parameters disableSceneMode(Camera camera, Parameters params) {
		params.setSceneMode(SCENE_MODE_AUTO);
		camera.setParameters(params);
		return camera.getParameters();
	}

	private Parameters setBestParameters(Camera camera, Parameters params) {
		setVideoStabilisation(params);
		setFocusMode(params);
		params.setFlashMode(FLASH_MODE_OFF);
		setPreviewSize(params);
		camera.setParameters(params);
		return camera.getParameters();
	}

	private void setVideoStabilisation(Parameters params) {
		if (Build.VERSION.SDK_INT >= 15 &&
				params.isVideoStabilizationSupported()) {
			params.setVideoStabilization(true);
		}
	}

	private void setFocusMode(Parameters params) {
		List<String> focusModes = params.getSupportedFocusModes();
		if (LOG.isLoggable(INFO)) LOG.info("Focus modes: " + focusModes);
		if (focusModes.contains(FOCUS_MODE_CONTINUOUS_PICTURE)) {
			params.setFocusMode(FOCUS_MODE_CONTINUOUS_PICTURE);
		} else if (focusModes.contains(FOCUS_MODE_CONTINUOUS_VIDEO)) {
			params.setFocusMode(FOCUS_MODE_CONTINUOUS_VIDEO);
		} else if (focusModes.contains(FOCUS_MODE_EDOF)) {
			params.setFocusMode(FOCUS_MODE_EDOF);
		} else if (focusModes.contains(FOCUS_MODE_MACRO)) {
			params.setFocusMode(FOCUS_MODE_MACRO);
		} else if (focusModes.contains(FOCUS_MODE_AUTO)) {
			params.setFocusMode(FOCUS_MODE_AUTO);
		} else if (focusModes.contains(FOCUS_MODE_FIXED)) {
			params.setFocusMode(FOCUS_MODE_FIXED);
		}
	}

	private void setPreviewSize(Parameters params) {
		if (surfaceWidth == 0 || surfaceHeight == 0) return;
		float idealRatio = (float) surfaceWidth / surfaceHeight;
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
			float score = pixels / stretch;
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

	private void enableAutoFocus(String focusMode) {
		autoFocus = FOCUS_MODE_AUTO.equals(focusMode) ||
				FOCUS_MODE_MACRO.equals(focusMode);
	}

	private void logCameraParameters() {
		if (LOG.isLoggable(INFO)) {
			Parameters params = camera.getParameters();
			if (Build.VERSION.SDK_INT >= 15) {
				LOG.info("Video stabilisation enabled: "
						+ params.getVideoStabilization());
			}
			LOG.info("Scene mode: " + params.getSceneMode());
			LOG.info("Focus mode: " + params.getFocusMode());
			LOG.info("Flash mode: " + params.getFlashMode());
			Size size = params.getPreviewSize();
			LOG.info("Preview size: " + size.width + "x" + size.height);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		LOG.info("Surface created");
		surfaceExists = true;
		if (camera != null) startPreview(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (LOG.isLoggable(INFO)) LOG.info("Surface changed: " + w + "x" + h);
		surfaceWidth = w;
		surfaceHeight = h;
		if (camera == null) return; // We are stopped
		stopPreview();
		try {
			Parameters params = camera.getParameters();
			setPreviewSize(params);
			camera.setParameters(params);
			logCameraParameters();
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error setting preview size", e);
		}
		startPreview(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		LOG.info("Surface destroyed");
		surfaceExists = false;
	}

	@Override
	public void onAutoFocus(boolean success, final Camera camera) {
		LOG.info("Auto focus succeeded: " + success);
		postDelayed(new Runnable() {
			@Override
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