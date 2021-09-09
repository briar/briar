package org.briarproject.briar.android.removabledrive;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.FOCUS_DOWN;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ChooserFragment extends Fragment {

	public final static String TAG = ChooserFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private RemovableDriveViewModel viewModel;
	private ScrollView scrollView;

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
		View v = inflater.inflate(R.layout.fragment_transfer_data_chooser,
				container, false);

		scrollView = (ScrollView) v;
		Button sendButton = v.findViewById(R.id.sendButton);
		sendButton.setOnClickListener(i -> viewModel.startSendData());

		Button receiveButton = v.findViewById(R.id.receiveButton);
		receiveButton.setOnClickListener(i -> viewModel.startReceiveData());

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.removable_drive_menu_title);
		TransferDataState state = viewModel.getState().getValue();
		if (state instanceof TransferDataState.TaskAvailable) {
			// we can't come back here now to start another task
			// as we only support one per ViewModel instance
			requireActivity().supportFinishAfterTransition();
		} else {
			// Scroll down in case the screen is small, so the button is visible
			scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
		}
	}

}
