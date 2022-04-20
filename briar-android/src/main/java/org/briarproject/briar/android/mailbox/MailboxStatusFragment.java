package org.briarproject.briar.android.mailbox;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.bramble.api.mailbox.MailboxStatus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.core.content.ContextCompat.getColor;
import static androidx.core.widget.ImageViewCompat.setImageTintList;
import static androidx.transition.TransitionManager.beginDelayedTransition;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;
import static org.briarproject.briar.android.util.UiUtils.MIN_DATE_RESOLUTION;
import static org.briarproject.briar.android.util.UiUtils.formatDate;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxStatusFragment extends Fragment {

	static final String TAG = MailboxStatusFragment.class.getName();
	private static final int NUM_FAILURES = 4;

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;
	private final Handler handler = new Handler(Looper.getMainLooper());
	@Nullable // UiThread
	private Runnable refresher = null;

	private ImageView imageView;
	private TextView statusTitleView;
	private TextView statusInfoView;
	private Button unlinkButton;
	private ProgressBar unlinkProgress;

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

		imageView = v.findViewById(R.id.imageView);
		statusTitleView = v.findViewById(R.id.statusTitleView);
		statusInfoView = v.findViewById(R.id.statusInfoView);
		viewModel.getStatus()
				.observe(getViewLifecycleOwner(), this::onMailboxStateChanged);

		// TODO
		//  * Implement UI for warning user when mailbox is unreachable #2175
		unlinkButton = v.findViewById(R.id.unlinkButton);
		unlinkProgress = v.findViewById(R.id.unlinkProgress);
		unlinkButton.setOnClickListener(view -> onUnlinkButtonClicked());
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
		@ColorRes int tintRes;
		@DrawableRes int iconRes;
		String title;
		if (status.getAttemptsSinceSuccess() == 0) {
			iconRes = R.drawable.ic_check_circle_outline;
			title = getString(R.string.mailbox_status_connected_title);
			tintRes = R.color.briar_brand_green;
		} else if (status.getAttemptsSinceSuccess() < NUM_FAILURES) {
			iconRes = R.drawable.ic_help_outline_white;
			title = getString(R.string.mailbox_status_problem_title);
			tintRes = R.color.briar_orange_500;
		} else {
			tintRes = R.color.briar_red_500;
			title = getString(R.string.mailbox_status_failure_title);
			iconRes = R.drawable.alerts_and_states_error;
		}
		imageView.setImageResource(iconRes);
		int color = getColor(requireContext(), tintRes);
		setImageTintList(imageView, ColorStateList.valueOf(color));
		statusTitleView.setText(title);

		long lastSuccess = status.getTimeOfLastSuccess();
		String lastConnectionText;
		if (lastSuccess < 0) {
			lastConnectionText =
					getString(R.string.mailbox_status_connected_never);
		} else {
			lastConnectionText = formatDate(requireContext(), lastSuccess);
		}
		String statusInfoText =
				getString(R.string.mailbox_status_connected_info,
						lastConnectionText);
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

	private void onUnlinkButtonClicked() {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(),
				R.style.BriarDialogTheme);
		builder.setTitle(R.string.mailbox_status_unlink_dialog_title);
		builder.setMessage(R.string.mailbox_status_unlink_dialog_message);
		builder.setPositiveButton(R.string.cancel,
				(dialog, which) -> dialog.cancel());
		builder.setNegativeButton(R.string.mailbox_status_unlink_button,
				(dialog, which) -> {
					beginDelayedTransition((ViewGroup) requireView());
					unlinkButton.setVisibility(INVISIBLE);
					unlinkProgress.setVisibility(VISIBLE);
					viewModel.unlink();
				});
		builder.show();
	}

}
