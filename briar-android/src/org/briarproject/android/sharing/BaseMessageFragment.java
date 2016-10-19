package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.LargeTextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;
import org.briarproject.util.StringUtils;

import static android.support.design.widget.Snackbar.LENGTH_SHORT;

import static org.briarproject.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.util.StringUtils.truncateUtf8;

public abstract class BaseMessageFragment extends BaseFragment
		implements TextInputListener {

	protected LargeTextInputView message;
	private MessageFragmentListener listener;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (MessageFragmentListener) context;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		// inflate view
		View v = inflater.inflate(R.layout.fragment_message, container,
				false);
		message = (LargeTextInputView) v.findViewById(R.id.messageView);
		message.setButtonText(getString(getButtonText()));
		message.setHint(getHintText());
		message.setListener(this);

		return v;
	}

	protected void setTitle(int res) {
		listener.setTitle(res);
	}

	@StringRes
	protected abstract int getButtonText();
	@StringRes
	protected abstract int getHintText();

	@Override
	public void onStart() {
		super.onStart();
		message.showSoftKeyboard();
	}

	@Override
	public void onSendClick(String msg) {
		if (StringUtils.isTooLong(msg, listener.getMaximumMessageLength())) {
			Snackbar.make(message, R.string.text_too_long, LENGTH_SHORT).show();
			return;
		}

		// disable button to prevent accidental double actions
		message.setSendButtonEnabled(false);
		message.hideSoftKeyboard();

		msg = truncateUtf8(msg, MAX_INVITATION_MESSAGE_LENGTH);
		if(!listener.onButtonClick(msg)) {
			message.setSendButtonEnabled(true);
			message.showSoftKeyboard();
		}
	}

	public interface MessageFragmentListener {

		void onBackPressed();

		void setTitle(@StringRes int titleRes);

		/** Returns true when the button click has been consumed. */
		boolean onButtonClick(String message);

		int getMaximumMessageLength();

	}

}
