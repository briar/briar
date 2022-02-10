package org.briarproject.briar.android.socialbackup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.BackupMetadata;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

public class ExistingBackupFragment extends BaseFragment {

	private static final String THRESHOLD = "threshold";
	private static final String CUSTODIANS = "custodians";
	public static final String TAG = ExistingBackupFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SocialBackupSetupViewModel viewModel;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(SocialBackupSetupViewModel.class);
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
		BackupMetadata backupMetadata = viewModel.getBackupMetadata();
		List<Author> custodians = backupMetadata.getCustodians();

		StringBuilder custodianNamesString = new StringBuilder();
		for (Author custodian : custodians) {
			custodianNamesString
					.append("• ")
					.append(custodian.getName())
					.append("\n");
		}

		TextView textViewThreshold = view.findViewById(R.id.textViewThreshold);
		textViewThreshold.setText(getString(R.string.existing_backup_explain,
				backupMetadata.getThreshold()));
		TextView textViewCustodians =
				view.findViewById(R.id.textViewCustodians);
		textViewCustodians.setText(custodianNamesString);
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

}
