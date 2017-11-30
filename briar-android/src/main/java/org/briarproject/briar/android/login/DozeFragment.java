package org.briarproject.briar.android.login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.login.PowerView.OnCheckedChangedListener;
import org.briarproject.briar.android.util.UiUtils;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_DOZE_WHITELISTING;
import static org.briarproject.briar.android.util.UiUtils.showOnboardingDialog;

@NotNullByDefault
public class DozeFragment extends SetupFragment
		implements OnCheckedChangedListener {

	private final static String TAG = DozeFragment.class.getName();

	private DozeView dozeView;
	private HuaweiView huaweiView;
	private Button next;
	private ProgressBar progressBar;
	private boolean secondAttempt = false;

	public static DozeFragment newInstance() {
		return new DozeFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		getActivity().setTitle(getString(R.string.setup_doze_title));
		setHasOptionsMenu(false);
		View v = inflater.inflate(R.layout.fragment_setup_doze, container,
						false);
		dozeView = v.findViewById(R.id.dozeView);
		dozeView.setOnCheckedChangedListener(this);
		huaweiView = v.findViewById(R.id.huaweiView);
		huaweiView.setOnCheckedChangedListener(this);
		next = v.findViewById(R.id.next);
		progressBar = v.findViewById(R.id.progress);

		dozeView.setOnButtonClickListener(this::askForDozeWhitelisting);
		next.setOnClickListener(this);

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
		if (dozeView.isChecked() && huaweiView.isChecked()) {
			next.setEnabled(true);
		} else {
			next.setEnabled(false);
		}
	}

	@SuppressLint("BatteryLife")
	private void askForDozeWhitelisting() {
		if (getContext() == null) return;
		Intent i = UiUtils.getDozeWhitelistingIntent(getContext());
		startActivityForResult(i, REQUEST_DOZE_WHITELISTING);
	}

	@Override
	public void onClick(View view) {
		next.setVisibility(INVISIBLE);
		progressBar.setVisibility(VISIBLE);
		setupController.createAccount();
	}

}
