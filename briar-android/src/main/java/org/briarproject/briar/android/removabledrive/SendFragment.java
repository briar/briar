package org.briarproject.briar.android.removabledrive;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SendFragment extends Fragment {

	final static String TAG = SendFragment.class.getName();

	private final ActivityResultLauncher<String> launcher =
			registerForActivityResult(new CreateDocument(),
					this::onDocumentCreated);

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	RemovableDriveViewModel viewModel;
	TextView introTextView;
	Button button;
	ProgressBar progressBar;

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
		View v = inflater.inflate(R.layout.fragment_transfer_data_send,
				container, false);

		introTextView = v.findViewById(R.id.introTextView);
		progressBar = v.findViewById(R.id.progressBar);
		button = v.findViewById(R.id.fileButton);
		button.setOnClickListener(view ->
				launcher.launch(viewModel.getFileName())
		);

		viewModel.getState()
				.observe(getViewLifecycleOwner(), this::onStateChanged);

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.removable_drive_title_send);
	}

	private void onStateChanged(TransferDataState state) {
		if (state instanceof TransferDataState.NoDataToSend) {
			introTextView.setText(R.string.removable_drive_send_no_data);
			button.setVisibility(GONE);
		} else if (state instanceof TransferDataState.Ready) {
			button.setVisibility(VISIBLE);
		} else if (state instanceof TransferDataState.TaskAvailable) {
			button.setEnabled(false);
			if (((TransferDataState.TaskAvailable) state).isOldTask) {
				Toast.makeText(requireContext(),
						R.string.removable_drive_ongoing, LENGTH_LONG).show();
			}
			RemovableDriveTask.State s =
					((TransferDataState.TaskAvailable) state).state;
			if (s.getTotal() > 0L && progressBar.getVisibility() != VISIBLE) {
				progressBar.setVisibility(VISIBLE);
				// FIXME if we ever export more than 2 GB, this won't work
				progressBar.setMax((int) s.getTotal());
			}
			progressBar.setProgress((int) s.getDone());
		}
	}

	private void onDocumentCreated(@Nullable Uri uri) {
		if (uri == null) return;
		viewModel.exportData(uri);
	}

}
