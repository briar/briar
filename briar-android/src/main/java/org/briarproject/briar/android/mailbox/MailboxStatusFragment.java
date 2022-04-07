package org.briarproject.briar.android.mailbox;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.transition.TransitionManager.beginDelayedTransition;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.util.UiUtils.MIN_DATE_RESOLUTION;
import static org.briarproject.briar.android.util.UiUtils.formatDate;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxStatusFragment extends Fragment {

	static final String TAG = MailboxStatusFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;
	private final Handler handler = new Handler(Looper.getMainLooper());
	@Nullable // UiThread
	private Runnable refresher = null;

	private TextView statusInfoView;

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

		Button checkButton = v.findViewById(R.id.checkButton);
		ProgressBar checkProgress = v.findViewById(R.id.checkProgress);
		checkButton.setOnClickListener(view -> {
			beginDelayedTransition((ViewGroup) v);
			checkButton.setVisibility(INVISIBLE);
			checkProgress.setVisibility(VISIBLE);
			observeOnce(viewModel.checkConnection(), this, result -> {
				beginDelayedTransition((ViewGroup) v);
				checkButton.setVisibility(VISIBLE);
				checkProgress.setVisibility(INVISIBLE);
			});
		});

		statusInfoView = v.findViewById(R.id.statusInfoView);
		viewModel.getStatus()
				.observe(getViewLifecycleOwner(), this::onMailboxStateChanged);

		// TODO
		//  * detect problems and show them #2175
		//  * add "Unlink" button confirmation dialog and functionality #2173
		Button unlinkButton = v.findViewById(R.id.unlinkButton);
		unlinkButton.setOnClickListener(view -> Toast.makeText(requireContext(),
				"NOT IMPLEMENTED", Toast.LENGTH_SHORT).show());
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.mailbox_status_title);
		refresher = this::refreshLastConnection;
		handler.postDelayed(refresher, MIN_DATE_RESOLUTION);
	}

	@Override
	public void onStop() {
		super.onStop();
		handler.removeCallbacks(refresher);
		refresher = null;
	}

	private void onMailboxStateChanged(MailboxStatus status) {
		long lastSuccess = status.getTimeOfLastSuccess();
		String lastConnectionText;
		if (lastSuccess < 0) {
			lastConnectionText =
					getString(R.string.mailbox_status_connected_never);
		} else {
			lastConnectionText = formatDate(requireContext(), lastSuccess);
		}
		String statusInfoText = getString(
				R.string.mailbox_status_connected_info, lastConnectionText);
		statusInfoView.setText(statusInfoText);
	}

	@UiThread
	private void refreshLastConnection() {
		MailboxStatus status = viewModel.getStatus().getValue();
		if (status != null) onMailboxStateChanged(status);
		if (refresher != null) {
			handler.postDelayed(refresher, MIN_DATE_RESOLUTION);
		}
	}

}
