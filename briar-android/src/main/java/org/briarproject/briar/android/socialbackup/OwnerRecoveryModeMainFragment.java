package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class OwnerRecoveryModeMainFragment extends BaseFragment {

	protected ScanQrButtonListener listener;

	public static final String NUM_RECOVERED = "num_recovered";

	public static final String TAG =
			OwnerRecoveryModeMainFragment.class.getName();

	public static OwnerRecoveryModeMainFragment newInstance(int numRecovered) {
		Bundle args = new Bundle();
		args.putInt(NUM_RECOVERED, numRecovered);
		OwnerRecoveryModeMainFragment fragment =
				new OwnerRecoveryModeMainFragment();
		fragment.setArguments(args);
		return fragment;
	}

	private int numShards;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_recovery_mode);

		Bundle args = requireArguments();
		numShards = args.getInt(NUM_RECOVERED);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_recovery_owner_main,
				container, false);

		TextView textViewCount = view.findViewById(R.id.textViewShardCount);
		textViewCount.setText(String.format("%d", numShards));

		Button button = view.findViewById(R.id.button);
		button.setOnClickListener(e -> listener.scanQrButtonClicked());
		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (ScanQrButtonListener) context;
	}

}
