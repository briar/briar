package org.briarproject.briar.android.removabledrive;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.ActivityLaunchers.GetContentAdvanced;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ReceiveFragment extends Fragment {

	final static String TAG = ReceiveFragment.class.getName();

	private final ActivityResultLauncher<String> launcher =
			registerForActivityResult(new GetContentAdvanced(),
					this::onDocumentChosen);

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private RemovableDriveViewModel viewModel;
	private ScrollView scrollView;
	private Button button;
	private ProgressBar progressBar;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity activity = requireActivity();
		getAndroidComponent(activity).inject(this);
		viewModel = new ViewModelProvider(activity, viewModelFactory)
				.get(RemovableDriveViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_transfer_data_receive,
				container, false);

		scrollView = (ScrollView) v;
		progressBar = v.findViewById(R.id.progressBar);
		button = v.findViewById(R.id.fileButton);
		button.setOnClickListener(view ->
				launcher.launch("*/*")
		);
		viewModel.getOldTaskResumedEvent()
				.observeEvent(getViewLifecycleOwner(), this::onOldTaskResumed);
		viewModel.getState()
				.observe(getViewLifecycleOwner(), this::onStateChanged);
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.removable_drive_title_receive);
		// Scroll down in case the screen is small, so the button is visible
		scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
	}

	private void onOldTaskResumed(boolean resumed) {
		if (resumed) {
			Toast.makeText(requireContext(),
					R.string.removable_drive_ongoing, LENGTH_LONG).show();
		}
	}

	private void onStateChanged(TransferDataState state) {
		if (state instanceof TransferDataState.NoDataToSend) {
			throw new IllegalStateException();
		} else if (state instanceof TransferDataState.Ready) {
			button.setEnabled(true);
		} else if (state instanceof TransferDataState.TaskAvailable) {
			button.setEnabled(false);
			progressBar.setVisibility(VISIBLE);
		}
	}

	private void onDocumentChosen(@Nullable Uri uri) {
		if (uri == null) return;
		viewModel.importData(uri);
	}

}
