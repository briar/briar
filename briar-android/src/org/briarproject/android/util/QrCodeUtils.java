package org.briarproject.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.DisplayMetrics;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class QrCodeUtils {

	private static final Logger LOG =
			Logger.getLogger(QrCodeUtils.class.getName());

	public static Bitmap createQrCode(Context context, String input) {
		// Get narrowest screen dimension
		DisplayMetrics dm = context.getResources().getDisplayMetrics();
		int smallestDimen = Math.min(dm.widthPixels, dm.heightPixels);
		try {
			// Generate QR code
			final BitMatrix encoded = new QRCodeWriter().encode(
					input, BarcodeFormat.QR_CODE, smallestDimen, smallestDimen);
			// Convert QR code to Bitmap
			int width = encoded.getWidth();
			int height = encoded.getHeight();
			int[] pixels = new int[width * height];
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					pixels[y * width + x] =
							encoded.get(x, y) ? Color.BLACK : Color.WHITE;
				}
			}
			Bitmap qr = Bitmap.createBitmap(width, height,
					Bitmap.Config.ARGB_8888);
			qr.setPixels(pixels, 0, width, 0, 0, width, height);
			return qr;
		} catch (WriterException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}
}
