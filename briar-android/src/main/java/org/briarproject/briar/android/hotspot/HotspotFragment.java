package org.briarproject.briar.android.hotspot;

import android.os.Bundle;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotFragment extends AbstractTabsFragment {

	public final static String TAG = HotspotFragment.class.getName();

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		// no need to call into the ViewModel here
		connectedButton.setOnClickListener(v -> {
			getParentFragmentManager().beginTransaction()
					.setCustomAnimations(R.anim.step_next_in,
							R.anim.step_previous_out, R.anim.step_previous_in,
							R.anim.step_next_out)
					.replace(R.id.fragmentContainer, new WebsiteFragment(),
							WebsiteFragment.TAG)
					.addToBackStack(WebsiteFragment.TAG)
					.commit();
		});
	}

	@Override
	protected Fragment getFirstFragment() {
		return ManualHotspotFragment.newInstance(true);
	}

	@Override
	protected Fragment getSecondFragment() {
		return QrHotspotFragment.newInstance(true);
	}

}
