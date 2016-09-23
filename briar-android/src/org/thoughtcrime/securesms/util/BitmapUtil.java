package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.FitCenter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class BitmapUtil {

	private static final Logger LOG =
			Logger.getLogger(BitmapUtil.class.getName());

	private static <T> InputStream getInputStreamForModel(Context context,
			T model)
			throws BitmapDecodingException {
		try {
			return Glide.buildStreamModelLoader(model, context)
					.getResourceFetcher(model, -1, -1)
					.loadData(Priority.NORMAL);
		} catch (Exception e) {
			throw new BitmapDecodingException(e);
		}
	}

	private static <T> Bitmap createScaledBitmapInto(Context context, T model,
			int width, int height)
			throws BitmapDecodingException {
		final Bitmap rough = Downsampler.AT_LEAST
				.decode(getInputStreamForModel(context, model),
						Glide.get(context).getBitmapPool(),
						width, height, DecodeFormat.PREFER_RGB_565);

		final Resource<Bitmap> resource = BitmapResource
				.obtain(rough, Glide.get(context).getBitmapPool());
		final Resource<Bitmap> result =
				new FitCenter(context).transform(resource, width, height);

		if (result == null) {
			throw new BitmapDecodingException("unable to transform Bitmap");
		}
		return result.get();
	}

	public static <T> Bitmap createScaledBitmap(Context context, T model,
			float scale) throws BitmapDecodingException {
		Pair<Integer, Integer> dimens =
				getDimensions(getInputStreamForModel(context, model));
		return createScaledBitmapInto(context, model,
				(int) (dimens.first * scale), (int) (dimens.second * scale));
	}

	private static BitmapFactory.Options getImageDimensions(
			InputStream inputStream)
			throws BitmapDecodingException {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BufferedInputStream fis = new BufferedInputStream(inputStream);
		BitmapFactory.decodeStream(fis, null, options);
		try {
			fis.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}

		if (options.outWidth == -1 || options.outHeight == -1) {
			throw new BitmapDecodingException(
					"Failed to decode image dimensions: " + options.outWidth +
							", " + options.outHeight);
		}

		return options;
	}

	private static Pair<Integer, Integer> getDimensions(InputStream inputStream)
			throws BitmapDecodingException {
		BitmapFactory.Options options = getImageDimensions(inputStream);
		return new Pair<>(options.outWidth, options.outHeight);
	}

}
