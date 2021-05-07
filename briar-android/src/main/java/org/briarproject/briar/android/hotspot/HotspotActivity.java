package org.briarproject.briar.android.hotspot;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;

import static android.widget.Toast.LENGTH_SHORT;

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
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.hotspot_help_action, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		} else if (item.getItemId() == R.id.action_help) {
			Toast.makeText(this, "Not yet implemented", LENGTH_SHORT).show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
