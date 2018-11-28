package org.briarproject.briar.android.conversation;

import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.StaggeredGridLayoutManager.LayoutParams;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.load.Transformation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.BriarImageTransformation;
import org.briarproject.briar.android.conversation.glide.GlideApp;
import org.briarproject.briar.android.conversation.glide.Radii;

import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

@NotNullByDefault
class ImageViewHolder extends ViewHolder {

	@DrawableRes
	private static final int ERROR_RES = R.drawable.ic_image_broken;

	protected final ImageView imageView;
	private final int imageSize, borderSize;

	public ImageViewHolder(View v, int imageSize, int borderSize) {
		super(v);
		imageView = v.findViewById(R.id.imageView);
		this.imageSize = imageSize;
		this.borderSize = borderSize;
	}

	void bind(AttachmentItem attachment, Radii r, boolean single,
			boolean needsStretch) {
		if (attachment.hasError()) {
			GlideApp.with(imageView)
					.clear(imageView);
			imageView.setImageResource(ERROR_RES);
		} else {
			setImageViewDimensions(attachment, single, needsStretch);
			loadImage(attachment, r);
		}
	}

	private void setImageViewDimensions(AttachmentItem a, boolean single,
			boolean needsStretch) {
		LayoutParams params = (LayoutParams) imageView.getLayoutParams();
		// actual image size will shrink half the border
		int stretchSize = (imageSize - borderSize / 2) * 2 + borderSize;
		int width = needsStretch ? stretchSize : imageSize;
		params.width = single ? a.getThumbnailWidth() : width;
		params.height = single ? a.getThumbnailHeight() : imageSize;
		params.setFullSpan(!single && needsStretch);
		imageView.setLayoutParams(params);
	}

	private void loadImage(AttachmentItem a, Radii r) {
		Transformation<Bitmap> transformation = new BriarImageTransformation(r);
		GlideApp.with(imageView)
				.load(a)
				.diskCacheStrategy(NONE)
				.error(ERROR_RES)
				.transform(transformation)
				.transition(withCrossFade())
				.into(imageView)
				.waitForLayout();
	}

}
