package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import static android.view.View.FOCUS_DOWN;
import static org.briarproject.briar.android.util.UiUtils.showFragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SetupIntroFragment extends Fragment {

	static final String TAG = SetupIntroFragment.class.getName();

	private ScrollView scrollView;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_mailbox_setup_intro,
				container, false);
		scrollView = v.findViewById(R.id.scrollView);
		Button button = v.findViewById(R.id.continueButton);
		button.setOnClickListener(view -> {
			FragmentManager fm = getParentFragmentManager();
			Fragment f = new SetupDownloadFragment();
			showFragment(fm, f, SetupDownloadFragment.TAG);
		});
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.mailbox_setup_title);
		// Scroll down in case the screen is small, so the button is visible
		scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
	}

}
