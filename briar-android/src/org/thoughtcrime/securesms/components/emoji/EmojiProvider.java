package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.SparseArray;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.android.BaseActivity;
import org.briarproject.android.api.AndroidExecutor;
import org.thoughtcrime.securesms.components.util.FutureTaskListener;
import org.thoughtcrime.securesms.components.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;

public class EmojiProvider {
	private static final String TAG = EmojiProvider.class.getSimpleName();
	private static volatile EmojiProvider instance = null;
	private static final Paint paint =
			new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

	@Inject
	AndroidExecutor androidExecutor;

	private static final Logger LOG = Logger.getLogger(TAG);

	private final SparseArray<DrawInfo> offsets = new SparseArray<>();

	private static final Pattern EMOJI_RANGE = Pattern.compile(
	//     0x203c,0x2049  0x20a0-0x32ff          0x1f00-0x1fff              0xfe4e5-0xfe4ee
	//   |=== !!, ?! ===||==== misc ===||========= emoticons =======||========== flags ==========|
		"[\\u203c\\u2049\\u20a0-\\u32ff\\ud83c\\udc00-\\ud83f\\udfff\\udbb9\\udce5-\\udbb9\\udcee]");

	private static final int EMOJI_RAW_HEIGHT = 64;
	private static final int EMOJI_RAW_WIDTH = 64;
	private static final int EMOJI_VERT_PAD = 0;
	private static final int EMOJI_PER_ROW = 32;

	private final Context context;
	private final float decodeScale;
	private final float verticalPad;
	private final List<EmojiPageModel> staticPages;

	public static EmojiProvider getInstance(Context context) {
		if (instance == null) {
			synchronized (EmojiProvider.class) {
				if (instance == null) {
					LOG.info("Creating new instance of EmojiProvider");
					instance = new EmojiProvider(context);
					((BaseActivity) context).getActivityComponent()
							.inject(instance);
				}
			}
		}
		return instance;
	}

	private EmojiProvider(Context context) {
		this.context = context.getApplicationContext();
		this.decodeScale = Math.min(1f,
				context.getResources().getDimension(R.dimen.emoji_drawer_size) /
						EMOJI_RAW_HEIGHT);
		this.verticalPad = EMOJI_VERT_PAD * this.decodeScale;
		staticPages = EmojiPages.getPages(context);
		for (EmojiPageModel page : staticPages) {
			if (page.hasSpriteMap()) {
				final EmojiPageBitmap pageBitmap = new EmojiPageBitmap(page);
				for (int i = 0; i < page.getEmoji().length; i++) {
					offsets.put(Character.codePointAt(page.getEmoji()[i], 0),
							new DrawInfo(pageBitmap, i));
				}
			}
		}
	}

	@Nullable
	public Spannable emojify(@Nullable CharSequence text, @NonNull TextView tv) {
		if (text == null) return null;
		Matcher matches = EMOJI_RANGE.matcher(text);
		SpannableStringBuilder builder = new SpannableStringBuilder(text);

		while (matches.find()) {
			int codePoint = matches.group().codePointAt(0);
			Drawable drawable = getEmojiDrawable(codePoint);
			if (drawable != null) {
				builder.setSpan(new EmojiSpan(drawable, tv), matches.start(),
						matches.end(),
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		return builder;
	}

	@Nullable
	public Drawable getEmojiDrawable(int emojiCode) {
		return getEmojiDrawable(offsets.get(emojiCode));
	}

	@Nullable
	private Drawable getEmojiDrawable(DrawInfo drawInfo) {
		if (drawInfo == null) {
			return null;
		}

		final EmojiDrawable drawable = new EmojiDrawable(drawInfo, decodeScale);
		drawInfo.page.get().addListener(new FutureTaskListener<Bitmap>() {
			@Override
			public void onSuccess(final Bitmap result) {
				androidExecutor.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						drawable.setBitmap(result);
					}
				});
			}

			@Override
			public void onFailure(Throwable error) {
				LOG.log(WARNING, error.toString(), error);
			}
		});
		return drawable;
	}

	public List<EmojiPageModel> getStaticPages() {
		return staticPages;
	}


	public class EmojiDrawable extends Drawable {
		private final DrawInfo info;
		private Bitmap bmp;
		private float intrinsicWidth;
		private float intrinsicHeight;

		@Override
		public int getIntrinsicWidth() {
			return (int) intrinsicWidth;
		}

		@Override
		public int getIntrinsicHeight() {
			return (int) intrinsicHeight;
		}

		private EmojiDrawable(DrawInfo info, float decodeScale) {
			this.info = info;
			this.intrinsicWidth = EMOJI_RAW_WIDTH * decodeScale;
			this.intrinsicHeight = EMOJI_RAW_HEIGHT * decodeScale;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			if (bmp == null) {
				return;
			}

			final int row = info.index / EMOJI_PER_ROW;
			final int row_index = info.index % EMOJI_PER_ROW;

			canvas.drawBitmap(bmp,
					new Rect((int) (row_index * intrinsicWidth),
							(int) (row * intrinsicHeight + row * verticalPad),
							(int) ((row_index + 1) * intrinsicWidth),
							(int) ((row + 1) * intrinsicHeight +
									row * verticalPad)),
					getBounds(),
					paint);
		}

		public void setBitmap(Bitmap bitmap) {
			if (bmp == null || !bmp.sameAs(bitmap)) {
				bmp = bitmap;
				invalidateSelf();
			}
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public void setAlpha(int alpha) {
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
		}
	}


	private static class DrawInfo {
		private EmojiPageBitmap page;
		int index;

		private DrawInfo(final EmojiPageBitmap page, final int index) {
			this.page = page;
			this.index = index;
		}

		@Override
		public String toString() {
			return "DrawInfo{ " +"page = " + page +", index = " + index + '}';
		}
	}


	private class EmojiPageBitmap {
		private EmojiPageModel model;
		private SoftReference<Bitmap> bitmapReference;
		private ListenableFutureTask<Bitmap> task;

		private EmojiPageBitmap(EmojiPageModel model) {
			this.model = model;
		}

		private ListenableFutureTask<Bitmap> get() {
			if (bitmapReference != null && bitmapReference.get() != null) {
				return new ListenableFutureTask<>(bitmapReference.get());
			} else if (task != null) {
				return task;
			} else {
				Callable<Bitmap> callable = new Callable<Bitmap>() {
					@Override
					@Nullable
					public Bitmap call() throws Exception {
						try {
							LOG.info("loading page " + model.getSprite());
							return loadPage();
						} catch (IOException ioe) {
							LOG.log(WARNING, ioe.toString(), ioe);
						}
						return null;
					}
				};
				task = new ListenableFutureTask<>(callable);
				new AsyncTask<Void, Void, Void>() {
					@Override
					protected Void doInBackground(Void... params) {
						task.run();
						return null;
					}

					@Override
					protected void onPostExecute(Void aVoid) {
						task = null;
					}
				}.execute();
			}
			return task;
		}

		private Bitmap loadPage() throws IOException {
			if (bitmapReference != null && bitmapReference.get() != null)
				return bitmapReference.get();

			try {
				final Bitmap bitmap = BitmapUtil.createScaledBitmap(context,
						"file:///android_asset/" + model.getSprite(),
						decodeScale);
				bitmapReference = new SoftReference<>(bitmap);
				LOG.info("onPageLoaded(" + model.getSprite() + ")");
				return bitmap;
			} catch (BitmapDecodingException e) {
				LOG.log(WARNING, e.toString(), e);
				throw new IOException(e);
			}
		}

		@Nullable
		@Override
		public String toString() {
			return model.getSprite();
		}
	}

}
