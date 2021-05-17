package org.briarproject.briar.android.hotspot;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.api.android.AndroidNotificationManager.ACTION_STOP_HOTSPOT;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotActivity extends BriarActivity {

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

		// TODO observe viewmodel state and show error or HotspotFragment

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
