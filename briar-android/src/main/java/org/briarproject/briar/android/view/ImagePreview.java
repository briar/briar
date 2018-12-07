package org.briarproject.briar.android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.GlideApp;

import java.util.List;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_YES;
import static android.support.v7.app.AppCompatDelegate.getDefaultNightMode;
import static android.widget.Toast.LENGTH_LONG;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.FIT_CENTER;
import static java.util.Objects.requireNonNull;

@NotNullByDefault
public class ImagePreview extends ConstraintLayout {

	private final ImageView imageView;
	private final int backgroundColor =
			getDefaultNightMode() == MODE_NIGHT_YES ? BLACK : WHITE;

	@Nullable
	private ImagePreviewListener listener;

	public ImagePreview(Context context) {
		this(context, null);
	}

	public ImagePreview(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ImagePreview(Context context, @Nullable AttributeSet attrs,
			int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		// inflate layout
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
		inflater.inflate(R.layout.image_preview, this, true);

		// find image view and set background color
		imageView = findViewById(R.id.imageView);
		imageView.setBackgroundColor(backgroundColor);

		// set cancel listener
		findViewById(R.id.imageCancelButton).setOnClickListener(view -> {
			if (listener != null) listener.onCancel();
		});
	}

	void setImagePreviewListener(ImagePreviewListener listener) {
		this.listener = listener;
	}

	void showPreview(List<Uri> imageUris) {
		setVisibility(VISIBLE);
		GlideApp.with(imageView)
				.asBitmap()
				.load(imageUris.get(0))  // TODO show more than the first
				.diskCacheStrategy(NONE)
				.downsample(FIT_CENTER)
				.addListener(new RequestListener<Bitmap>() {
					@Override
					public boolean onLoadFailed(@Nullable GlideException e,
							Object model, Target<Bitmap> target,
							boolean isFirstResource) {
						if (listener != null) listener.onCancel();
						Toast.makeText(imageView.getContext(),
								R.string.image_attach_error, LENGTH_LONG)
								.show();
						return false;
					}

					@Override
					public boolean onResourceReady(Bitmap resource,
							Object model, Target<Bitmap> target,
							DataSource dataSource, boolean isFirstResource) {
						Palette.from(resource).generate(
								ImagePreview.this::onPaletteGenerated);
						return false;
					}
				})
				.into(imageView);
	}

	void onPaletteGenerated(@Nullable Palette palette) {
		if (palette == null) return;
		int color = getDefaultNightMode() == MODE_NIGHT_YES ?
				palette.getDarkMutedColor(backgroundColor) :
				palette.getLightMutedColor(backgroundColor);
		imageView.setBackgroundColor(color);
	}

	interface ImagePreviewListener {
		void onCancel();
	}

}
