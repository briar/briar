package org.briarproject.android.sharing;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.view.LargeTextInputView;
import org.briarproject.android.view.TextInputView.TextInputListener;

import static org.briarproject.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.util.StringUtils.truncateUtf8;

abstract class BaseMessageFragment extends BaseFragment
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

		// allow for "up" button to act as back button
		setHasOptionsMenu(true);

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

	protected abstract @StringRes int getButtonText();
	protected abstract @StringRes int getHintText();

	@Override
	public void onStart() {
		super.onStart();
		message.showSoftKeyboard();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				listener.onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSendClick(String msg) {
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

	}

}
