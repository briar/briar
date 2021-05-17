package org.briarproject.briar.android.remotewipe.activate;

import android.app.Application;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public class ActivateRemoteWipeViewModel extends AndroidViewModel {
	@Inject
	public ActivateRemoteWipeViewModel(
			@NonNull Application application) {
		super(application);
	}
}
