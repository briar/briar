package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ProgressBar;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxViewModel viewModel;
	private ProgressBar progressBar;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(MailboxViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mailbox);

		progressBar = findViewById(R.id.progressBar);
		if (viewModel.getState().getValue() == null) {
			progressBar.setVisibility(VISIBLE);
		}

		if (savedInstanceState == null) {
			viewModel.getState().observe(this, state -> {
				if (state instanceof MailboxState.NotSetup) {
					onNotSetup();
				}
			});
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

	private void onNotSetup() {
		progressBar.setVisibility(INVISIBLE);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragmentContainer, new SetupIntroFragment(),
						SetupIntroFragment.TAG)
				.commit();
	}

}
