package org.briarproject.briar.android.view;

import android.app.Activity;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
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
import org.briarproject.briar.android.conversation.AttachmentResult;
import org.briarproject.briar.android.view.ImagePreview.ImagePreviewListener;
import org.briarproject.briar.api.messaging.AttachmentHeader;

import java.util.ArrayList;
import java.util.List;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.PromptStateChangeListener;

import static android.arch.lifecycle.Lifecycle.State.DESTROYED;
import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_ALLOW_MULTIPLE;
import static android.content.Intent.EXTRA_MIME_TYPES;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.content.ContextCompat.getColor;
import static android.support.v4.view.AbsSavedState.EMPTY_STATE;
import static android.view.View.GONE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.util.UiUtils.resolveColorAttribute;
import static org.briarproject.briar.api.messaging.MessagingConstants.IMAGE_MIME_TYPES;
import static uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_DISMISSED;
import static uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_FINISHED;

@UiThread
@NotNullByDefault
public class TextAttachmentController extends TextSendController
		implements ImagePreviewListener {

	private final ImagePreview imagePreview;
	private final AttachImageListener imageListener;
	private final CompositeSendButton sendButton;
	private final AttachmentManager attachmentManager;

	private CharSequence textHint;
	private List<Uri> imageUris = emptyList();
	private int urisLoaded = 0;
	private boolean loadingUris = false;

	public TextAttachmentController(TextInputView v, ImagePreview imagePreview,
			SendListener listener, AttachImageListener imageListener,
			AttachmentManager attachmentManager) {
		super(v, listener, false);
		this.imageListener = imageListener;
		this.imagePreview = imagePreview;
		this.attachmentManager = attachmentManager;
		this.imagePreview.setImagePreviewListener(this);

		sendButton = (CompositeSendButton) compositeSendButton;
		sendButton.setOnImageClickListener(view -> onImageButtonClicked());

		textHint = textInput.getHint();
	}

	@Override
	protected void updateViewState() {
		textInput.setEnabled(ready && !loadingUris);
		boolean sendEnabled = ready && !loadingUris &&
				(!textIsEmpty || canSendEmptyText());
		if (loadingUris) {
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
			listener.onSendClick(textInput.getText(),
					attachmentManager.getAttachmentHeaders());
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
		Intent intent = getAttachFileIntent();
		if (imageListener.getLifecycle().getCurrentState() != DESTROYED) {
			requireNonNull(imageListener).onAttachImage(intent);
		}
	}

	private Intent getAttachFileIntent() {
		Intent intent = new Intent(SDK_INT >= 19 ?
				ACTION_OPEN_DOCUMENT : ACTION_GET_CONTENT);
		intent.setType("image/*");
		intent.addCategory(CATEGORY_OPENABLE);
		if (SDK_INT >= 19) intent.putExtra(EXTRA_MIME_TYPES, IMAGE_MIME_TYPES);
		if (SDK_INT >= 18) intent.putExtra(EXTRA_ALLOW_MULTIPLE, true);
		return intent;
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
		loadingUris = true;
		updateViewState();
		textInput.setHint(R.string.image_caption_hint);
		List<ImagePreviewItem> items = ImagePreviewItem.fromUris(imageUris);
		imagePreview.showPreview(items);
		// store attachments and show preview when successful
		boolean needsSize = items.size() == 1;
		for (ImagePreviewItem item : items) {
			attachmentManager.storeAttachment(item.getUri(), needsSize)
					.observe(imageListener, this::onAttachmentResultReceived);
		}
	}

	private void onAttachmentResultReceived(AttachmentResult result) {
		if (result.isError() || result.getUri() == null) {
			onError(result.getErrorMsg());
		} else {
			imagePreview.loadPreviewImage(result);
			urisLoaded++;
			checkAllUrisLoaded();
		}
	}

	private void reset() {
		// restore hint
		textInput.setHint(textHint);
		// hide image layout
		imagePreview.setVisibility(GONE);
		// reset image URIs
		imageUris = emptyList();
		// no URIs has been loaded
		urisLoaded = 0;
		loadingUris = false;
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

	@UiThread
	private void onError(@Nullable String errorMsg) {
		if (errorMsg == null) {
			errorMsg = imagePreview.getContext()
					.getString(R.string.image_attach_error);
		}
		Toast.makeText(textInput.getContext(), errorMsg, LENGTH_LONG).show();
		onCancel();
	}

	@Override
	public void onCancel() {
		textInput.clearText();
		attachmentManager.removeAttachments();
		reset();
	}

	private void checkAllUrisLoaded() {
		if (urisLoaded == imageUris.size()) {
			loadingUris = false;
			// all images were turned into attachments
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

	public interface AttachImageListener extends LifecycleOwner {
		void onAttachImage(Intent intent);
	}

	public interface AttachmentManager {
		/**
		 * Stores a new attachment in the database.
		 *
		 * @param uri The Uri of the attachment to store.
		 * @param needsSize true if this is the only image in the message
		 * and therefore needs to know its size.
		 */
		LiveData<AttachmentResult> storeAttachment(Uri uri, boolean needsSize);

		List<AttachmentHeader> getAttachmentHeaders();

		void removeAttachments();
	}

}
