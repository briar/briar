package org.briarproject.briar.android.conversation;

import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.GlideApp;

import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

@NotNullByDefault
class ImageViewHolder extends ViewHolder {

	@DrawableRes
	private static final int ERROR_RES = R.drawable.ic_image_broken;

	protected final ImageView imageView;
	protected Transformation<Bitmap> transformation = new CenterCrop();

	public ImageViewHolder(View v) {
		super(v);
		imageView = v.findViewById(R.id.imageView);
	}

	void bind(AttachmentItem attachment) {
		if (attachment.hasError()) {
			GlideApp.with(imageView)
					.clear(imageView);
			imageView.setImageResource(ERROR_RES);
		} else {
			loadImage(attachment);
		}
	}

	private void loadImage(AttachmentItem a) {
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
