package org.briarproject.briar.android.login;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.util.UiUtils;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_DOZE_WHITELISTING;
import static org.briarproject.briar.android.util.UiUtils.showOnboardingDialog;

@TargetApi(23)
public class DozeFragment extends SetupFragment {

	private final static String TAG = DozeFragment.class.getName();

	private Button dozeButton;
	private ProgressBar progressBar;

	public static DozeFragment newInstance() {
		return new DozeFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		getActivity().setTitle(getString(R.string.setup_doze_title));
		View v = inflater.inflate(R.layout.fragment_setup_doze, container,
						false);
		dozeButton = v.findViewById(R.id.dozeButton);
		progressBar = v.findViewById(R.id.progress);

		dozeButton.setOnClickListener(view -> askForDozeWhitelisting());

		return v;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	protected String getHelpText() {
		return getString(R.string.setup_doze_explanation);
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_DOZE_WHITELISTING) {
			if (!setupController.needsDozeWhitelisting()) {
				dozeButton.setEnabled(false);
				onClick(dozeButton);
			} else {
				showOnboardingDialog(getContext(), getHelpText());
			}
		}
	}

	@SuppressLint("BatteryLife")
	private void askForDozeWhitelisting() {
		Intent i = UiUtils.getDozeWhitelistingIntent(getContext());
		startActivityForResult(i, REQUEST_DOZE_WHITELISTING);
	}

	@Override
	public void onClick(View view) {
		dozeButton.setVisibility(INVISIBLE);
		progressBar.setVisibility(VISIBLE);
		setupController.createAccount();
	}

}
