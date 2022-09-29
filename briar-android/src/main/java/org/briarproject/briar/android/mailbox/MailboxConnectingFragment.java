package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxConnectingFragment extends Fragment {

	static final String TAG = MailboxConnectingFragment.class.getName();

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_mailbox_connecting,
				container, false);
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.mailbox_setup_title);
	}

}
