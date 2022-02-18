package org.briarproject.briar.android.mailbox;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.util.UiUtils.formatDate;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxStatusFragment extends Fragment {

	static final String TAG = MailboxStatusFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity activity = requireActivity();
		getAndroidComponent(activity).inject(this);
		viewModel = new ViewModelProvider(activity, viewModelFactory)
				.get(MailboxViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_mailbox_status,
				container, false);
	}

	@Override
	public void onViewCreated(View v, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);
		MailboxState.IsPaired state =
				(MailboxState.IsPaired) viewModel.getState().getLastValue();
		requireNonNull(state); // TODO check assumption
		TextView statusInfoView = v.findViewById(R.id.statusInfoView);
		long lastSuccess = state.mailboxStatus.getTimeOfLastSuccess();
		String lastConnectionText;
		if (lastSuccess < 0) {
			lastConnectionText = getString(R.string.pref_lock_timeout_never);
		} else {
			lastConnectionText = formatDate(requireContext(), lastSuccess);
		}
		String statusInfoText = getString(
				R.string.mailbox_status_connected_info, lastConnectionText);
		statusInfoView.setText(statusInfoText);
		// TODO
		//  * react to status changes
		//  * detect problems and show them
		//  * update connection time periodically like conversation timestamps
		//  * add "Check connection" button
		//  * add "Unlink" button with confirmation dialog
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.mailbox_status_title);
	}

}
