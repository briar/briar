package org.briarproject.briar.android.conversation;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.load.Transformation;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.attachment.AttachmentItem;
import org.briarproject.briar.android.conversation.glide.BriarImageTransformation;
import org.briarproject.briar.android.conversation.glide.GlideApp;
import org.briarproject.briar.android.conversation.glide.Radii;

import androidx.annotation.DrawableRes;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams;

import static android.os.Build.VERSION.SDK_INT;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

@NotNullByDefault
class ImageViewHolder extends ViewHolder {

	@DrawableRes
	private static final int ERROR_RES = R.drawable.ic_image_broken;

	protected final ImageView imageView;
	private final int imageSize;

	ImageViewHolder(View v, int imageSize) {
		super(v);
		imageView = v.findViewById(R.id.imageView);
		this.imageSize = imageSize;
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
			if (SDK_INT >= 21) {
				imageView.setTransitionName(attachment.getTransitionName());
			}
		}
	}

	private void setImageViewDimensions(AttachmentItem a, boolean single,
			boolean needsStretch) {
		LayoutParams params = (LayoutParams) imageView.getLayoutParams();
		int width = needsStretch ? imageSize * 2 : imageSize;
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
