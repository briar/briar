package org.briarproject.briar.android.view;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.view.AbsSavedState;
import android.support.v7.app.AlertDialog.Builder;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.view.ImagePreview.ImagePreviewListener;

import java.util.ArrayList;
import java.util.List;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.PromptStateChangeListener;

import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_ALLOW_MULTIPLE;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.content.ContextCompat.getColor;
import static android.support.v4.view.AbsSavedState.EMPTY_STATE;
import static android.view.View.GONE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.util.UiUtils.resolveColorAttribute;
import static uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_DISMISSED;
import static uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_FINISHED;

@UiThread
@NotNullByDefault
public class TextAttachmentController extends TextSendController
		implements ImagePreviewListener {

	private final ImagePreview imagePreview;
	private final AttachImageListener imageListener;
	private final CompositeSendButton sendButton;

	private CharSequence textHint;
	private List<Uri> imageUris = emptyList();
	private int previewsLoaded = 0;
	private boolean loadingPreviews = false;

	public TextAttachmentController(TextInputView v, ImagePreview imagePreview,
			SendListener listener, AttachImageListener imageListener) {
		super(v, listener, false);
		this.imageListener = imageListener;
		this.imagePreview = imagePreview;
		this.imagePreview.setImagePreviewListener(this);

		sendButton = (CompositeSendButton) compositeSendButton;
		sendButton.setOnImageClickListener(view -> onImageButtonClicked());

		textHint = textInput.getHint();
	}

	@Override
	protected void updateViewState() {
		textInput.setEnabled(ready && !loadingPreviews);
		boolean sendEnabled = ready && !loadingPreviews &&
				(!textIsEmpty || canSendEmptyText());
		if (loadingPreviews) {
			sendButton.showProgress(true);
		} else if (imageUris.isEmpty()) {
			sendButton.showProgress(false);
			sendButton.showImageButton(textIsEmpty, sendEnabled);
		} else {
			sendButton.showProgress(false);
			sendButton.showImageButton(false, sendEnabled);
		}
	}

	@Override
	public void onSendEvent() {
		if (canSend()) {
			listener.onSendClick(textInput.getText(), imageUris);
			reset();
		}
	}

	@Override
	protected boolean canSendEmptyText() {
		return !imageUris.isEmpty();
	}

	public void setImagesSupported() {
		sendButton.setImagesSupported();
	}

	private void onImageButtonClicked() {
		if (!sendButton.hasImageSupport()) {
			Context ctx = imagePreview.getContext();
			Builder builder = new Builder(ctx, R.style.OnboardingDialogTheme);
			builder.setTitle(
					ctx.getString(R.string.dialog_title_no_image_support));
			builder.setMessage(
					ctx.getString(R.string.dialog_message_no_image_support));
			builder.setPositiveButton(R.string.got_it, null);
			builder.show();
			return;
		}
		Intent intent = new Intent(SDK_INT >= 19 ?
				ACTION_OPEN_DOCUMENT : ACTION_GET_CONTENT);
		intent.addCategory(CATEGORY_OPENABLE);
		intent.setType("image/*");
		if (SDK_INT >= 18) intent.putExtra(EXTRA_ALLOW_MULTIPLE, true);
		requireNonNull(imageListener).onAttachImage(intent);
	}

	public void onImageReceived(@Nullable Intent resultData) {
		if (resultData == null) return;
		if (resultData.getData() != null) {
			imageUris = new ArrayList<>(1);
			imageUris.add(resultData.getData());
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
		loadingPreviews = true;
		updateViewState();
		textInput.setHint(R.string.image_caption_hint);
		imagePreview.showPreview(imageUris);
	}

	private void reset() {
		// restore hint
		textInput.setHint(textHint);
		// hide image layout
		imagePreview.setVisibility(GONE);
		// reset image URIs
		imageUris = emptyList();
		// no preview has been loaded
		previewsLoaded = 0;
		loadingPreviews = false;
		// show the image button again, so images can get attached
		updateViewState();
	}

	@Override
	public Parcelable onSaveInstanceState(@Nullable Parcelable superState) {
		SavedState state =
				new SavedState(superState == null ? EMPTY_STATE : superState);
		state.imageUris = imageUris;
		return state;
	}

	@Override
	@Nullable
	public Parcelable onRestoreInstanceState(Parcelable inState) {
		SavedState state = (SavedState) inState;
		imageUris = requireNonNull(state.imageUris);
		onNewUris();
		return state.getSuperState();
	}

	@Override
	public void onPreviewLoaded() {
		previewsLoaded++;
		checkAllPreviewsLoaded();
	}

	@Override
	public void onUriError(Uri uri) {
		boolean removed = imageUris.remove(uri);
		if (!removed) {
			// we have removed this Uri already, do not remove it again
			return;
		}
		imagePreview.removeUri(uri);
		if (imageUris.isEmpty()) onCancel();
		Toast.makeText(textInput.getContext(), R.string.image_attach_error,
				LENGTH_LONG).show();
		checkAllPreviewsLoaded();
	}

	@Override
	public void onCancel() {
		textInput.clearText();
		reset();
	}

	private void checkAllPreviewsLoaded() {
		if (previewsLoaded == imageUris.size()) {
			loadingPreviews = false;
			// all previews were loaded
			updateViewState();
		}
	}

	public void showImageOnboarding(Activity activity,
			Runnable onOnboardingSeen) {
		PromptStateChangeListener listener = (prompt, state) -> {
			if (state == STATE_DISMISSED || state == STATE_FINISHED) {
				onOnboardingSeen.run();
			}
		};
		int color = resolveColorAttribute(activity, R.attr.colorControlNormal);
		new MaterialTapTargetPrompt.Builder(activity,
				R.style.OnboardingDialogTheme).setTarget(sendButton)
				.setPrimaryText(R.string.dialog_title_image_support)
				.setSecondaryText(R.string.dialog_message_image_support)
				.setBackgroundColour(getColor(activity, R.color.briar_primary))
				.setIcon(R.drawable.ic_image)
				.setIconDrawableColourFilter(color)
				.setPromptStateChangeListener(listener)
				.show();
	}

	private static class SavedState extends AbsSavedState {

		@Nullable
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

		public static final Creator<SavedState> CREATOR =
				new Creator<SavedState>() {
					@Override
					public SavedState createFromParcel(Parcel in) {
						return new SavedState(in);
					}

					@Override
					public SavedState[] newArray(int size) {
						return new SavedState[size];
					}
				};
	}

	public interface AttachImageListener {
		void onAttachImage(Intent intent);
	}

}
