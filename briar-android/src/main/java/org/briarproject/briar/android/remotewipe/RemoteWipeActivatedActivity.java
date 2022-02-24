package org.briarproject.briar.android.remotewipe;

import android.os.Bundle;

import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;


public class RemoteWipeActivatedActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	RemoteWipeActivatedViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(RemoteWipeActivatedViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		viewModel.getConfirmSent()
				.observeEvent(this, confirmed -> {
					if (confirmed) {
						signOut(true, true);
					}
				});

		viewModel.sendConfirmMessages();
	}
}
