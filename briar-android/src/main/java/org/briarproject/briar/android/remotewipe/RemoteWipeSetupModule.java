package org.briarproject.briar.android.remotewipe;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class RemoteWipeSetupModule {

	@Binds
	@IntoMap
	@ViewModelKey(RemoteWipeSetupViewModel.class)
	abstract ViewModel bindRemoteWipeSetupViewModel(
			RemoteWipeSetupViewModel remoteWipeSetupViewModel);
}
