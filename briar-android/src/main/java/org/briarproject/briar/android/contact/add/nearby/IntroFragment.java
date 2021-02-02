package org.briarproject.briar.android.contact.add.nearby;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

import static android.view.View.FOCUS_DOWN;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class IntroFragment extends BaseFragment {

	public static final String TAG = IntroFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ContactExchangeViewModel viewModel;

	private ScrollView scrollView;

	public static IntroFragment newInstance() {
		Bundle args = new Bundle();
		IntroFragment fragment = new IntroFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(ContactExchangeViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_keyagreement_id, container,
				false);
		scrollView = v.findViewById(R.id.scrollView);
		View button = v.findViewById(R.id.continueButton);
		button.setOnClickListener(view -> viewModel.onContinueClicked());
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

}
