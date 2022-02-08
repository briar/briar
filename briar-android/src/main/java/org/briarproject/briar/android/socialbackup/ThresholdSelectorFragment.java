package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.magmacollective.darkcrystal.secretsharingwrapper.SecretSharingWrapper;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

public class ThresholdSelectorFragment extends BaseFragment {

	public static final String TAG = ThresholdSelectorFragment.class.getName();
	private static final String NUMBER_CUSTODIANS = "numberCustodians";

	private int numberOfCustodians;
	private int threshold;
	private int recommendedThreshold;
	private SeekBar seekBar;
	private TextView thresholdRepresentation;
	private TextView message;
	private TextView mOfn;

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SocialBackupSetupViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SocialBackupSetupViewModel.class);
	}

	public static ThresholdSelectorFragment newInstance(int numberCustodians) {
		Bundle bundle = new Bundle();
		bundle.putInt(NUMBER_CUSTODIANS, numberCustodians);
		ThresholdSelectorFragment fragment = new ThresholdSelectorFragment();
		fragment.setArguments(bundle);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_define_threshold);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_select_threshold,
				container, false);
		Bundle args = requireArguments();
		numberOfCustodians = args.getInt(NUMBER_CUSTODIANS);
		seekBar = view.findViewById(R.id.seekBar);
		thresholdRepresentation =
				view.findViewById(R.id.textViewThresholdRepresentation);
		message = view.findViewById(R.id.textViewMessage);
		mOfn = view.findViewById(R.id.textViewmOfn);

		if (numberOfCustodians > 3) {
			seekBar.setMax(numberOfCustodians -3);
			seekBar.setOnSeekBarChangeListener(new SeekBarListener());
			recommendedThreshold =
					SecretSharingWrapper.defaultThreshold(numberOfCustodians);
			threshold = recommendedThreshold;
			seekBar.setProgress(threshold - 2);
		} else {
			seekBar.setEnabled(false);
			threshold = 2;
			seekBar.setMax(numberOfCustodians);
			seekBar.setProgress(threshold);
			TextView t = view.findViewById(R.id.title_threshold);
			t.setText(R.string.threshold_disabled);
		}
		thresholdRepresentation.setText(buildThresholdRepresentationString());
		setmOfnText();
		return view;
//        return super.onCreateView(inflater, container, savedInstanceState);
	}

	private void setmOfnText() {
		mOfn.setText(String.format(
				getString(R.string.threshold_m_of_n), threshold,
				numberOfCustodians));
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
	}


	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.define_threshold_actions, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_threshold_defined:
				viewModel.setThreshold(threshold);
				showSuccessDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void showSuccessDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(),
				R.style.BriarDialogTheme);
		builder.setTitle(R.string.backup_created);
		builder.setMessage(R.string.backup_done_info);
		builder.setPositiveButton(R.string.ok,
				(dialog, which) -> viewModel.onSuccessDismissed());
		builder.setIcon(R.drawable.ic_baseline_done_outline_24);
				AlertDialog dialog = builder.create();
		dialog.show();
	}

	private String buildThresholdRepresentationString() {
		String thresholdRepresentationText = "";
		for (int i = 0; i < threshold; i++) {
			thresholdRepresentationText += getString(R.string.filled_bullet);
		}
		for (int i = 0; i < (numberOfCustodians - threshold); i++) {
			thresholdRepresentationText += getString(R.string.linear_bullet);
		}
		return thresholdRepresentationText;
	}

	private class SeekBarListener implements SeekBar.OnSeekBarChangeListener {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			threshold = progress + 2;

			thresholdRepresentation.setText(
					buildThresholdRepresentationString()
			);
			setmOfnText();

			int sanityLevel = SecretSharingWrapper
					.thresholdSanity(threshold, numberOfCustodians);
			int text = R.string.threshold_secure;
			if (threshold == recommendedThreshold)
				text = R.string.threshold_recommended;
			if (sanityLevel < -1) text = R.string.threshold_low_insecure;
			if (sanityLevel > 0) text = R.string.threshold_high_insecure;
			message.setText(text);
			// TODO change colour of thresholdRepresentation to green/red based on sanityLevel
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			// do nothing
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// do nothing
		}

	}

}
