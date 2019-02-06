package org.briarproject.briar.android.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.briarproject.briar.R;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Objects.requireNonNull;

public class CompositeSendButton extends FrameLayout {

	private final AppCompatImageButton sendButton, imageButton;
	private final ProgressBar progressBar;

	private boolean hasImageSupport = false;

	public CompositeSendButton(@NonNull Context context,
			@Nullable AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater inflater = (LayoutInflater) requireNonNull(
				context.getSystemService(LAYOUT_INFLATER_SERVICE));
		inflater.inflate(R.layout.view_composite_send_button, this, true);

		sendButton = findViewById(R.id.sendButton);
		imageButton = findViewById(R.id.imageButton);
		progressBar = findViewById(R.id.progressBar);
	}

	@Override
	public void setEnabled(boolean enabled) {
		setSendEnabled(enabled);
	}

	@Override
	public void setOnClickListener(@Nullable View.OnClickListener l) {
		setOnSendClickListener(l);
	}

	public void setOnSendClickListener(@Nullable OnClickListener l) {
		sendButton.setOnClickListener(l);
	}

	public void setSendEnabled(boolean enabled) {
		sendButton.setEnabled(enabled);
	}

	public void setOnImageClickListener(@Nullable OnClickListener l) {
		imageButton.setOnClickListener(l);
	}

	/**
	 * By default, image support is disabled.
	 * Once you know that it is supported in the current context,
	 * call this method to enable it.
	 */
	public void setImagesSupported() {
		hasImageSupport = true;
		imageButton.setImageResource(R.drawable.ic_image);
	}

	public boolean hasImageSupport() {
		return hasImageSupport;
	}

	public void showImageButton(boolean showImageButton, boolean sendEnabled) {
		if (showImageButton) {
			imageButton.setVisibility(VISIBLE);
			sendButton.setEnabled(false);
			if (SDK_INT <= 15) {
				sendButton.setVisibility(INVISIBLE);
				imageButton.setEnabled(true);
			} else {
				sendButton.clearAnimation();
				sendButton.animate().alpha(0f).withEndAction(() -> {
					sendButton.setVisibility(INVISIBLE);
					imageButton.setEnabled(true);
				}).start();
				imageButton.clearAnimation();
				imageButton.animate().alpha(1f).start();
			}
		} else {
			sendButton.setVisibility(VISIBLE);
			// enable/disable buttons right away to allow fast sending
			sendButton.setEnabled(sendEnabled);
			imageButton.setEnabled(false);
			if (SDK_INT <= 15) {
				imageButton.setVisibility(INVISIBLE);
			} else {
				sendButton.clearAnimation();
				sendButton.animate().alpha(1f).start();
				imageButton.clearAnimation();
				imageButton.animate().alpha(0f).withEndAction(() ->
						imageButton.setVisibility(INVISIBLE)
				).start();
			}
		}
	}

	public void showProgress(boolean show) {
		sendButton.setVisibility(show ? INVISIBLE : VISIBLE);
		imageButton.setVisibility(show ? INVISIBLE : VISIBLE);
		progressBar.setVisibility(show ? VISIBLE : INVISIBLE);
	}

}
