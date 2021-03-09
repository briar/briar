package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.BackupMetadata;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ExistingBackupFragment extends BaseFragment {

	private static final String THRESHOLD = "threshold";
	public static final String TAG = ExistingBackupFragment.class.getName();
    private TextView textViewBackupMetadata;


	public static ExistingBackupFragment newInstance(
			BackupMetadata backupMetadata) {
		Bundle bundle = new Bundle();
//		backupMetadata.getCustodians();
		bundle.putInt(THRESHOLD, backupMetadata.getThreshold());
		ExistingBackupFragment fragment = new ExistingBackupFragment();
		fragment.setArguments(bundle);
		return fragment;
	}
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requireActivity().setTitle(R.string.title_distributed_backup);

	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable
			ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_existing_backup,
				container, false);
		Bundle args = requireArguments();

        textViewBackupMetadata = view.findViewById(R.id.textViewBackupMetadata);
        textViewBackupMetadata.setText(String.format("Threshold is %d", args.getInt(THRESHOLD)));
		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
//		listener = (ShardsSentDismissedListener) context;
	}


	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

}
