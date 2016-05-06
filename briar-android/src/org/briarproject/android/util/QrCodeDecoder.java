package org.briarproject.android.util;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

@SuppressWarnings("deprecation")
public class QrCodeDecoder implements PreviewConsumer, PreviewCallback {

	private static final Logger LOG =
			Logger.getLogger(QrCodeDecoder.class.getName());

	private final Reader reader = new QRCodeReader();
	private final ResultCallback callback;
	private final ResultPointCallback pointCallback;

	private boolean stopped = false;

	public QrCodeDecoder(ResultCallback callback,
			ResultPointCallback pointCallback) {
		this.callback = callback;
		this.pointCallback = pointCallback;
	}

	public void start(Camera camera) {
		stopped = false;
		askForPreviewFrame(camera);
	}

	public void stop() {
		stopped = true;
	}

	private void askForPreviewFrame(Camera camera) {
		if (!stopped) camera.setOneShotPreviewCallback(this);
	}

	public void onPreviewFrame(byte[] data, Camera camera) {
		if (!stopped) {
			Size size = camera.getParameters().getPreviewSize();
			new DecoderTask(camera, data, size.width, size.height).execute();
		}
	}

	private class DecoderTask extends AsyncTask<Void, Void, Void> {

		final Camera camera;
		final byte[] data;
		final int width, height;

		DecoderTask(Camera camera, byte[] data, int width, int height) {
			this.camera = camera;
			this.data = data;
			this.width = width;
			this.height = height;
		}

		@Override
		protected Void doInBackground(Void... params) {
			long now = System.currentTimeMillis();
			LuminanceSource src = new PlanarYUVLuminanceSource(data, width,
					height, 0, 0, width, height, false);
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(src));
			Map<DecodeHintType, Object> hints = new HashMap<>();
			hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, pointCallback);
			Result result = null;
			try {
				result = reader.decode(bitmap, hints);
			} catch (ReaderException e) {
				return null; // No barcode found
			} catch (RuntimeException e) {
				return null; // Decoding failed due to bug in decoder
			} finally {
				reader.reset();
			}
			long duration = System.currentTimeMillis() - now;
			if (LOG.isLoggable(INFO))
				LOG.info("Decoding barcode took " + duration + " ms");
			callback.handleResult(result);
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			askForPreviewFrame(camera);
		}
	}

	public interface ResultCallback {

		void handleResult(Result result);
	}
}
