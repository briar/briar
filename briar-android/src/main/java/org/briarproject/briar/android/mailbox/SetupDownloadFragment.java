package org.briarproject.briar.android.mailbox;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;

import org.briarproject.briar.R;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.FOCUS_DOWN;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SetupDownloadFragment extends Fragment {

	static final String TAG = SetupDownloadFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;

	private CameraPermissionManager permissionManager;
	private ScrollView scrollView;

	private final ActivityResultLauncher<String[]> permissionLauncher =
			registerForActivityResult(new RequestMultiplePermissions(), r -> {
				permissionManager.onRequestPermissionResult(r);
				if (permissionManager.checkPermissions()) {
					scanCode();
				}
			});

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
		View v = inflater.inflate(R.layout.fragment_mailbox_setup_download,
				container, false);
		scrollView = v.findViewById(R.id.scrollView);

		permissionManager = new CameraPermissionManager(requireActivity(),
				permissionLauncher::launch);

		Button scanButton = v.findViewById(R.id.scanButton);
		scanButton.setOnClickListener(view -> {
			if (permissionManager.checkPermissions()) {
				scanCode();
			}
		});
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.mailbox_setup_title);
		// Scroll down in case the screen is small, so the button is visible
		scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
		// Permissions may have been granted manually while we were stopped
		permissionManager.resetPermissions();
	}

	private void scanCode() {
		viewModel.onScanButtonClicked();
	}

}
