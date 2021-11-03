package org.briarproject.briar.android.qrcode;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import androidx.annotation.UiThread;

import static com.google.zxing.DecodeHintType.CHARACTER_SET;
import static java.util.Collections.singletonMap;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class QrCodeDecoder implements PreviewConsumer, PreviewCallback {

	private static final Logger LOG = getLogger(QrCodeDecoder.class.getName());

	private final AndroidExecutor androidExecutor;
	private final Executor ioExecutor;
	private final Reader reader = new QRCodeReader();
	private final ResultCallback callback;

	private Camera camera = null;
	private int cameraIndex = 0;

	public QrCodeDecoder(AndroidExecutor androidExecutor,
			@IoExecutor Executor ioExecutor, ResultCallback callback) {
		this.androidExecutor = androidExecutor;
		this.ioExecutor = ioExecutor;
		this.callback = callback;
	}

	@Override
	public void start(Camera camera, int cameraIndex) {
		this.camera = camera;
		this.cameraIndex = cameraIndex;
		askForPreviewFrame();
	}

	@Override
	public void stop() {
		camera = null;
		cameraIndex = 0;
	}

	@UiThread
	private void askForPreviewFrame() {
		if (camera != null) camera.setOneShotPreviewCallback(this);
	}

	@UiThread
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (camera == this.camera) {
			try {
				Size size = camera.getParameters().getPreviewSize();
				// The preview should be in NV21 format: width * height bytes of
				// Y followed by width * height / 2 bytes of interleaved U and V
				if (data.length == size.width * size.height * 3 / 2) {
					CameraInfo info = new CameraInfo();
					Camera.getCameraInfo(cameraIndex, info);
					decode(data, size.width, size.height, info.orientation);
				} else {
					// Camera parameters have changed - ask for a new preview
					LOG.info("Preview size does not match camera parameters");
					askForPreviewFrame();
				}
			} catch (RuntimeException e) {
				LOG.log(WARNING, "Error getting camera parameters.", e);
			}
		} else {
			LOG.info("Camera has changed, ignoring preview frame");
		}
	}

	private void decode(byte[] data, int width, int height, int orientation) {
		ioExecutor.execute(() -> {
			BinaryBitmap bitmap = binarize(data, width, height, orientation);
			Result result;
			try {
				result = reader.decode(bitmap,
						singletonMap(CHARACTER_SET, "ISO8859_1"));
				callback.onQrCodeDecoded(result);
			} catch (ReaderException e) {
				// No barcode found
			} catch (RuntimeException e) {
				LOG.warning("Invalid preview frame");
			} finally {
				reader.reset();
				androidExecutor.runOnUiThread(this::askForPreviewFrame);
			}
		});
	}

	private static BinaryBitmap binarize(byte[] data, int width, int height,
			int orientation) {
		// Crop to a square at the top (portrait) or left (landscape) of the
		// screen - this will be faster to decode and should include
		// everything visible in the viewfinder
		int crop = Math.min(width, height);
		int left = orientation >= 180 ? width - crop : 0;
		int top = orientation >= 180 ? height - crop : 0;
		LuminanceSource src = new PlanarYUVLuminanceSource(data, width,
				height, left, top, crop, crop, false);
		return new BinaryBitmap(new HybridBinarizer(src));
	}

	@NotNullByDefault
	public interface ResultCallback {
		@IoExecutor
		void onQrCodeDecoded(Result result);
	}
}
