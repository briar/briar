package org.briarproject.briar.android.socialbackup.recover;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.account.DozeView;
import org.briarproject.briar.android.account.HuaweiView;
import org.briarproject.briar.android.account.PowerView;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.util.UiUtils;

import androidx.annotation.Nullable;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_DOZE_WHITELISTING;
import static org.briarproject.briar.android.util.UiUtils.showOnboardingDialog;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RestoreAccountDozeFragment extends RestoreAccountFragment
		implements PowerView.OnCheckedChangedListener {

	private final static String TAG = org.briarproject.briar.android.account.DozeFragment.class.getName();

	private DozeView dozeView;
	private HuaweiView huaweiView;
	private Button next;
	private boolean secondAttempt = false;

	public static RestoreAccountDozeFragment newInstance() {
		return new RestoreAccountDozeFragment();
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		requireActivity().setTitle(getString(R.string.setup_doze_title));
		setHasOptionsMenu(false);
		View v = inflater.inflate(R.layout.fragment_setup_doze, container,
				false);
		dozeView = v.findViewById(R.id.dozeView);
		dozeView.setOnCheckedChangedListener(this);
		huaweiView = v.findViewById(R.id.huaweiView);
		huaweiView.setOnCheckedChangedListener(this);
		next = v.findViewById(R.id.next);
		ProgressBar progressBar = v.findViewById(R.id.progress);

		dozeView.setOnButtonClickListener(this::askForDozeWhitelisting);
		next.setOnClickListener(this);

		viewModel.getIsCreatingAccount()
				.observe(getViewLifecycleOwner(), isCreatingAccount -> {
					if (isCreatingAccount) {
						next.setVisibility(INVISIBLE);
						progressBar.setVisibility(VISIBLE);
					}
				});

		return v;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	protected String getHelpText() {
		return getString(R.string.setup_doze_explanation);
	}

	@Override
	public void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_DOZE_WHITELISTING) {
			if (!dozeView.needsToBeShown() || secondAttempt) {
				dozeView.setChecked(true);
			} else if (getContext() != null) {
				secondAttempt = true;
				showOnboardingDialog(getContext(), getHelpText());
			}
		}
	}

	@Override
	public void onCheckedChanged() {
		next.setEnabled(dozeView.isChecked() && huaweiView.isChecked());
	}

	@SuppressLint("BatteryLife")
	private void askForDozeWhitelisting() {
		if (getContext() == null) return;
		Intent i = UiUtils.getDozeWhitelistingIntent(getContext());
		startActivityForResult(i, REQUEST_DOZE_WHITELISTING);
	}

	@Override
	public void onClick(View view) {
		viewModel.dozeExceptionConfirmed();
	}
}
