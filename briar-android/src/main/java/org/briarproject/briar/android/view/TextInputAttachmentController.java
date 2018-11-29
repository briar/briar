package org.briarproject.briar.android.view;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.AbsSavedState;
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
import static android.support.v4.view.AbsSavedState.EMPTY_STATE;
import static android.support.v7.app.AppCompatDelegate.MODE_NIGHT_YES;
import static android.support.v7.app.AppCompatDelegate.getDefaultNightMode;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.FIT_CENTER;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@UiThread
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
		imageCancelButton.setOnClickListener(view -> {
			editText.setText(null);
			reset();
		});
		showImageButton(true);
	}

	private void onImageButtonClicked() {
		Intent intent = new Intent(SDK_INT >= 19 ?
				ACTION_OPEN_DOCUMENT : ACTION_GET_CONTENT);
		intent.addCategory(CATEGORY_OPENABLE);
		intent.setType("image/*");
		if (SDK_INT >= 18)  // TODO set true to allow attaching multiple images
			intent.putExtra(EXTRA_ALLOW_MULTIPLE, false);
		listener.onAttachImage(intent);
	}

	void onImageReceived(@Nullable Intent resultData) {
		if (resultData == null) return;
		if (resultData.getData() != null) {
			imageUris = singletonList(resultData.getData());
			onNewUris();
		} else if (SDK_INT >= 18 && resultData.getClipData() != null) {
			ClipData clipData = resultData.getClipData();
			imageUris = new ArrayList<>(clipData.getItemCount());
			for (int i = 0; i < clipData.getItemCount(); i++) {
				imageUris.add(clipData.getItemAt(i).getUri());
			}
			onNewUris();
		}
	}

	private void onNewUris() {
		if (imageUris.isEmpty()) return;
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
						reset();
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
				sendButton.setEnabled(false);
				sendButton.animate().alpha(0f).withEndAction(() -> {
					sendButton.setVisibility(INVISIBLE);
					imageButton.setEnabled(true);
				}).start();
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
				imageButton.setEnabled(false);
				imageButton.animate().alpha(0f).withEndAction(() -> {
					imageButton.setVisibility(INVISIBLE);
					sendButton.setEnabled(true);
				}).start();
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
		else showImageButton(true);
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

	void reset() {
		// restore hint
		editText.setHint(textHint);
		// hide image layout
		imageLayout.setVisibility(GONE);
		// reset image URIs
		imageUris = emptyList();
		// show the image button again, so images can get attached
		showImageButton(true);
	}

	public Parcelable onSaveInstanceState(@Nullable Parcelable superState) {
		SavedState state =
				new SavedState(superState == null ? EMPTY_STATE : superState);
		state.imageUris = imageUris;
		return state;
	}

	@Nullable
	public Parcelable onRestoreInstanceState(Parcelable inState) {
		SavedState state = (SavedState) inState;
		imageUris = state.imageUris;
		onNewUris();
		return state.getSuperState();
	}

	private static class SavedState extends AbsSavedState {
		private List<Uri> imageUris;

		private SavedState(Parcelable superState) {
			super(superState);
		}

		private SavedState(Parcel in) {
			super(in);
			//noinspection unchecked
			imageUris = in.readArrayList(Uri.class.getClassLoader());
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeList(imageUris);
		}

		public static final Parcelable.Creator<SavedState> CREATOR
				= new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

}
