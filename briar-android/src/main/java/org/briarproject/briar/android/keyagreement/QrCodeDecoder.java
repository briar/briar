package org.briarproject.briar.android.keyagreement;

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

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

@SuppressWarnings("deprecation")
@MethodsNotNullByDefault
@ParametersNotNullByDefault
class QrCodeDecoder implements PreviewConsumer, PreviewCallback {

	private static final Logger LOG =
			Logger.getLogger(QrCodeDecoder.class.getName());

	private final Reader reader = new QRCodeReader();
	private final ResultCallback callback;

	private Camera camera = null;

	QrCodeDecoder(ResultCallback callback) {
		this.callback = callback;
	}

	@Override
	public void start(Camera camera) {
		this.camera = camera;
		askForPreviewFrame();
	}

	@Override
	public void stop() {
		camera = null;
	}

	@UiThread
	private void askForPreviewFrame() {
		if (camera != null) camera.setOneShotPreviewCallback(this);
	}

	@UiThread
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (camera == this.camera) {
			Size size = camera.getParameters().getPreviewSize();
			new DecoderTask(data, size.width, size.height).execute();
		}
	}

	private class DecoderTask extends AsyncTask<Void, Void, Void> {

		private final byte[] data;
		private final int width, height;

		DecoderTask(byte[] data, int width, int height) {
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
				return null; // Preview data did not match width and height
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
			askForPreviewFrame();
		}
	}

	@NotNullByDefault
	interface ResultCallback {

		void handleResult(Result result);
	}
}
