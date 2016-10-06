package org.briarproject.android.util;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.support.annotation.UiThread;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

@SuppressWarnings("deprecation")
public class QrCodeDecoder implements PreviewConsumer, PreviewCallback {

	private static final Logger LOG =
			Logger.getLogger(QrCodeDecoder.class.getName());

	private final Reader reader = new QRCodeReader();
	private final ResultCallback callback;

	private boolean stopped = false;

	public QrCodeDecoder(ResultCallback callback) {
		this.callback = callback;
	}

	@Override
	public void start(Camera camera) {
		stopped = false;
		askForPreviewFrame(camera);
	}

	@Override
	public void stop() {
		stopped = true;
	}

	@UiThread
	private void askForPreviewFrame(Camera camera) {
		if (!stopped) camera.setOneShotPreviewCallback(this);
	}

	@UiThread
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (!stopped) {
			Size size = camera.getParameters().getPreviewSize();
			new DecoderTask(camera, data, size.width, size.height).execute();
		}
	}

	private class DecoderTask extends AsyncTask<Void, Void, Void> {

		private final Camera camera;
		private final byte[] data;
		private final int width, height;

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
			Result result = null;
			try {
				result = reader.decode(bitmap);
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
