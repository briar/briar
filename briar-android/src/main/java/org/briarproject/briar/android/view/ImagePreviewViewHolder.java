package org.briarproject.briar.android.view;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.GlideApp;
import org.briarproject.briar.android.view.ImagePreview.ImagePreviewListener;

import static android.view.View.INVISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.FIT_CENTER;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

@NotNullByDefault
class ImagePreviewViewHolder extends ViewHolder {

	@DrawableRes
	private static final int ERROR_RES = R.drawable.ic_image_broken;

	private final boolean single;
	private final ImagePreviewListener listener;

	private final ImageView imageView;
	private final ProgressBar progressBar;

	ImagePreviewViewHolder(View v, boolean single,
			ImagePreviewListener listener) {
		super(v);
		this.single = single;
		this.listener = listener;
		this.imageView = v.findViewById(R.id.imageView);
		this.progressBar = v.findViewById(R.id.progressBar);
	}

	void bind(Uri uri) {
		GlideApp.with(imageView)
				.load(uri)
				.diskCacheStrategy(NONE)
				.error(ERROR_RES)
				.downsample(FIT_CENTER)
				.transition(withCrossFade())
				.addListener(new RequestListener<Drawable>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e,
							Object model, Target<Drawable> target,
							boolean isFirstResource) {
						if (single) listener.onCancel();
						progressBar.setVisibility(INVISIBLE);
						Toast.makeText(imageView.getContext(),
								R.string.image_attach_error, LENGTH_LONG)
								.show();
						return false;
					}

					@Override
					public boolean onResourceReady(Drawable resource,
							Object model, Target<Drawable> target,
							DataSource dataSource, boolean isFirstResource) {
						progressBar.setVisibility(INVISIBLE);
						return false;
					}
				})
				.into(imageView);
	}

}
