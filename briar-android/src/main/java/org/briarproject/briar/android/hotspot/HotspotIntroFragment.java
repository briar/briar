package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static androidx.transition.TransitionManager.beginDelayedTransition;
import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotIntroFragment extends Fragment {

	public final static String TAG = HotspotIntroFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private HotspotViewModel viewModel;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		getAndroidComponent(requireContext()).inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(HotspotViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_hotspot_intro, container, false);

		Button startButton = v.findViewById(R.id.startButton);
		ProgressBar progressBar = v.findViewById(R.id.progressBar);
		TextView progressTextView = v.findViewById(R.id.progressTextView);

		startButton.setOnClickListener(button -> {
			beginDelayedTransition((ViewGroup) v);
			startButton.setVisibility(INVISIBLE);
			progressBar.setVisibility(VISIBLE);
			progressTextView.setVisibility(VISIBLE);
			// TODO remove below, tell viewModel to start hotspot instead
			v.postDelayed(() -> {
				getParentFragmentManager().beginTransaction()
						.setCustomAnimations(R.anim.step_next_in,
								R.anim.step_previous_out,
								R.anim.step_previous_in,
								R.anim.step_next_out)
						.replace(R.id.fragmentContainer, new HotspotFragment(),
								HotspotFragment.TAG)
						.addToBackStack(HotspotFragment.TAG)
						.commit();
			}, 1500);
		});

		return v;
	}

}
