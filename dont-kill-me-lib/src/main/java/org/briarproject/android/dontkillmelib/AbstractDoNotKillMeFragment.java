package org.briarproject.android.dontkillmelib;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import org.briarproject.android.dontkillmelib.PowerView.OnCheckedChangedListener;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.android.dontkillmelib.PowerUtils.getDozeWhitelistingIntent;
import static org.briarproject.android.dontkillmelib.PowerUtils.showOnboardingDialog;

public abstract class AbstractDoNotKillMeFragment extends Fragment
		implements OnCheckedChangedListener,
		ActivityResultCallback<ActivityResult> {

	public final static String TAG =
			AbstractDoNotKillMeFragment.class.getName();

	private DozeView dozeView;
	private HuaweiProtectedAppsView huaweiProtectedAppsView;
	private HuaweiAppLaunchView huaweiAppLaunchView;
	private XiaomiView xiaomiView;
	private Button next;
	private boolean secondAttempt = false;
	private boolean buttonWasClicked = false;

	private final ActivityResultLauncher<Intent> dozeLauncher =
			registerForActivityResult(new StartActivityForResult(), this);

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		requireActivity().setTitle(getString(R.string.setup_doze_title));
		setHasOptionsMenu(false);
		View v = inflater.inflate(R.layout.fragment_dont_kill_me, container,
				false);
		dozeView = v.findViewById(R.id.dozeView);
		dozeView.setOnCheckedChangedListener(this);
		huaweiProtectedAppsView = v.findViewById(R.id.huaweiProtectedAppsView);
		huaweiProtectedAppsView.setOnCheckedChangedListener(this);
		huaweiAppLaunchView = v.findViewById(R.id.huaweiAppLaunchView);
		huaweiAppLaunchView.setOnCheckedChangedListener(this);
		xiaomiView = v.findViewById(R.id.xiaomiView);
		xiaomiView.setOnCheckedChangedListener(this);
		next = v.findViewById(R.id.next);
		ProgressBar progressBar = v.findViewById(R.id.progress);

		dozeView.setOnButtonClickListener(this::askForDozeWhitelisting);
		next.setOnClickListener(view -> {
			buttonWasClicked = true;
			next.setVisibility(INVISIBLE);
			progressBar.setVisibility(VISIBLE);
			onButtonClicked();
		});

		// restore UI state if button was clicked already
		buttonWasClicked = savedInstanceState != null &&
				savedInstanceState.getBoolean("buttonWasClicked", false);
		if (buttonWasClicked) {
			next.setVisibility(INVISIBLE);
			progressBar.setVisibility(VISIBLE);
		}

		return v;
	}

	protected abstract void onButtonClicked();

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("buttonWasClicked", buttonWasClicked);
	}

	@Override
	public void onActivityResult(ActivityResult result) {
		if (!dozeView.needsToBeShown() || secondAttempt) {
			dozeView.setChecked(true);
		} else if (getContext() != null) {
			secondAttempt = true;
			String s = getString(R.string.setup_doze_explanation);
			showOnboardingDialog(getContext(), s);
		}
	}

	@Override
	public void onCheckedChanged() {
		next.setEnabled(dozeView.isChecked() &&
				huaweiProtectedAppsView.isChecked() &&
				huaweiAppLaunchView.isChecked() &&
				xiaomiView.isChecked());
	}

	@SuppressLint("BatteryLife")
	private void askForDozeWhitelisting() {
		if (getContext() == null) return;
		dozeLauncher.launch(getDozeWhitelistingIntent(getContext()));
	}
}
