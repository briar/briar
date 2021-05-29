package org.briarproject.briar.android.hotspot;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.fragment.ErrorFragment;
import org.briarproject.briar.android.hotspot.HotspotState.HotspotError;
import org.briarproject.briar.android.hotspot.HotspotState.HotspotStarted;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.android.util.UiUtils.showFragment;
import static org.briarproject.briar.api.android.AndroidNotificationManager.ACTION_STOP_HOTSPOT;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotActivity extends BriarActivity
		implements BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private HotspotViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(HotspotViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		viewModel.getState().observe(this, hotspotState -> {
			if (hotspotState instanceof HotspotStarted) {
				FragmentManager fm = getSupportFragmentManager();
				String tag = HotspotFragment.TAG;
				// check if fragment is already added
				// to not lose state on configuration changes
				if (fm.findFragmentByTag(tag) == null) {
					showFragment(fm, new HotspotFragment(), tag);
				}
			} else if (hotspotState instanceof HotspotError) {
				String error = ((HotspotError) hotspotState).getError();
				Fragment f = ErrorFragment.newInstance(error);
				showFragment(getSupportFragmentManager(), f, ErrorFragment.TAG);
			}
		});

		if (state == null) {
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.fragmentContainer, new HotspotIntroFragment(),
							HotspotIntroFragment.TAG)
					.commit();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (ACTION_STOP_HOTSPOT.equals(intent.getAction())) {
			// also closes hotspot
			supportFinishAfterTransition();
		}
	}

}
