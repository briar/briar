package org.briarproject.briar.android.view;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.AppCompatImageButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.vanniktech.emoji.EmojiEditText;

import org.briarproject.briar.R;
import org.briarproject.briar.android.conversation.glide.GlideApp;
import org.briarproject.briar.android.view.TextInputView.AttachImageListener;

import java.util.ArrayList;
import java.util.List;

import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_ALLOW_MULTIPLE;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_YES;
import static android.support.v7.app.AppCompatDelegate.getDefaultNightMode;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.FIT_CENTER;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

class TextInputAttachmentController implements TextWatcher {

	private final EmojiEditText editText;
	private final View sendButton;
	private final AppCompatImageButton imageButton;
	private final ViewGroup imageLayout;
	private final ImageView imageView;

	private final AttachImageListener listener;

	private String textHint;
	private List<Uri> imageUris = emptyList();

	public TextInputAttachmentController(View v, EmojiEditText editText,
			View sendButton, AttachImageListener listener) {

		imageLayout = v.findViewById(R.id.imageLayout);
		imageView = v.findViewById(R.id.imageView);
		FloatingActionButton imageCancelButton =
				v.findViewById(R.id.imageCancelButton);
		imageButton = v.findViewById(R.id.imageButton);

		this.listener = listener;
		this.sendButton = sendButton;
		this.editText = editText;
		this.textHint = editText.getHint().toString();

		editText.addTextChangedListener(this);
		imageButton.setOnClickListener(view -> onImageButtonClicked());
		imageCancelButton.setOnClickListener(view -> afterSendButtonClicked());
		showImageButton(true);
	}

	private void onImageButtonClicked() {
		Intent intent = new Intent(SDK_INT >= 19 ?
				ACTION_OPEN_DOCUMENT : ACTION_GET_CONTENT);
		intent.addCategory(CATEGORY_OPENABLE);
		intent.setType("image/*");
		if (SDK_INT >= 18)
			intent.putExtra(EXTRA_ALLOW_MULTIPLE, false);
		listener.onAttachImage(intent);
	}

	void onImageReceived(@Nullable Intent resultData) {
		if (resultData == null) return;
		if (resultData.getData() != null) {
			imageUris = singletonList(resultData.getData());
		} else if (SDK_INT >= 18 && resultData.getClipData() != null) {
			ClipData clipData = resultData.getClipData();
			imageUris = new ArrayList<>(clipData.getItemCount());
			for (int i = 0; i < clipData.getItemCount(); i++) {
				imageUris.add(clipData.getItemAt(i).getUri());
			}
		} else {
			return;
		}
		showImageButton(false);
		editText.setHint(R.string.image_caption_hint);
		imageLayout.setVisibility(VISIBLE);
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
						return false;
					}

					@Override
					public boolean onResourceReady(Bitmap resource,
							Object model, Target<Bitmap> target,
							DataSource dataSource, boolean isFirstResource) {
						Palette.from(resource).generate(
								TextInputAttachmentController.this::onPaletteGenerated);
						return false;
					}
				})
				.into(imageView);
	}

	@UiThread
	private void onPaletteGenerated(@Nullable Palette palette) {
		int color;
		if (palette == null) {
			color = getDefaultNightMode() == MODE_NIGHT_YES ? BLACK : WHITE;
		} else {
			color = getDefaultNightMode() == MODE_NIGHT_YES ?
					palette.getDarkMutedColor(BLACK) :
					palette.getLightMutedColor(WHITE);
		}
		imageView.setBackgroundColor(color);
	}

	private void showImageButton(boolean showImageButton) {
		if (showImageButton) {
			imageButton.setVisibility(VISIBLE);
			if (SDK_INT <= 15) {
				sendButton.setVisibility(INVISIBLE);
			} else {
				sendButton.clearAnimation();
				sendButton.animate().alpha(0f).withEndAction(
						() -> sendButton.setVisibility(INVISIBLE)
				).start();
				imageButton.clearAnimation();
				imageButton.animate().alpha(1f).start();
			}
		} else {
			sendButton.setVisibility(VISIBLE);
			if (SDK_INT <= 15) {
				imageButton.setVisibility(INVISIBLE);
			} else {
				sendButton.clearAnimation();
				sendButton.animate().alpha(1f).start();
				imageButton.clearAnimation();
				imageButton.animate().alpha(0f).withEndAction(
						() -> imageButton.setVisibility(INVISIBLE)
				).start();
			}
		}
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// noop
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before,
			int count) {
		if (start != 0 || !imageUris.isEmpty()) return;
		if (s.length() > 0) showImageButton(false);
		else if (s.length() == 0) showImageButton(true);
	}

	@Override
	public void afterTextChanged(Editable s) {
		// noop
	}

	public List<Uri> getUris() {
		return imageUris;
	}

	public void saveHint(String hint) {
		textHint = hint;
	}

	void afterSendButtonClicked() {
		// restore hint
		editText.setHint(textHint);
		// hide image layout
		imageLayout.setVisibility(GONE);
		// reset image URIs
		imageUris = emptyList();
		// show the image button again, so images can get attached
		showImageButton(true);
	}

}
