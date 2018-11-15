package org.briarproject.briar.android.conversation.glide;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.MessageDigest;

import javax.annotation.concurrent.Immutable;

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Shader.TileMode.CLAMP;

@Immutable
@NotNullByDefault
class ImageCornerTransformation extends BitmapTransformation {

	private static final String ID = ImageCornerTransformation.class.getName();

	private final int smallRadius, radius;
	private final boolean leftCornerSmall, bottomRound;

	ImageCornerTransformation(int smallRadius, int radius,
			boolean leftCornerSmall, boolean bottomRound) {
		this.smallRadius = smallRadius;
		this.radius = radius;
		this.leftCornerSmall = leftCornerSmall;
		this.bottomRound = bottomRound;
	}

	@Override
	protected Bitmap transform(BitmapPool pool, Bitmap toTransform,
			int outWidth, int outHeight) {
		int width = toTransform.getWidth();
		int height = toTransform.getHeight();

		Bitmap bitmap = pool.get(width, height, ARGB_8888);
		bitmap.setHasAlpha(true);

		Canvas canvas = new Canvas(bitmap);
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setShader(new BitmapShader(toTransform, CLAMP, CLAMP));
		drawRect(canvas, paint, width, height);
		return bitmap;
	}

	private void drawRect(Canvas canvas, Paint paint, float width,
			float height) {
		drawSmallCorner(canvas, paint, width);
		drawBigCorners(canvas, paint, width, height);
	}

	private void drawSmallCorner(Canvas canvas, Paint paint, float width) {
		float left = leftCornerSmall ? 0 : width - radius;
		float right = leftCornerSmall ? radius : width;
		canvas.drawRoundRect(new RectF(left, 0, right, radius),
				smallRadius, smallRadius, paint);
	}

	private void drawBigCorners(Canvas canvas, Paint paint, float width,
			float height) {
		float top = bottomRound ? 0 : radius;
		RectF rect = new RectF(0, top, width, height);
		if (bottomRound) {
			canvas.drawRoundRect(rect, radius, radius, paint);
		} else {
			canvas.drawRect(rect, paint);
			canvas.drawRoundRect(new RectF(0, 0, width, radius * 2),
					radius, radius, paint);
		}
	}

	@Override
	public String toString() {
		return "ImageCornerTransformation(smallRadius=" + smallRadius +
				", radius=" + radius + ", leftCornerSmall=" + leftCornerSmall +
				", bottomRound=" + bottomRound + ")";
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ImageCornerTransformation &&
				((ImageCornerTransformation) o).smallRadius == smallRadius &&
				((ImageCornerTransformation) o).radius == radius &&
				((ImageCornerTransformation) o).leftCornerSmall ==
						leftCornerSmall &&
				((ImageCornerTransformation) o).bottomRound == bottomRound;
	}

	@Override
	public int hashCode() {
		return ID.hashCode() + smallRadius * 100 + radius * 10 +
				(leftCornerSmall ? 9 : 8) + (bottomRound ? 7 : 6);
	}

	@Override
	public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
		messageDigest.update((ID + smallRadius + radius + leftCornerSmall +
				bottomRound).getBytes(CHARSET));
	}

}
