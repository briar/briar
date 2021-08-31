package org.briarproject.briar.android.remotewipe;

import org.briarproject.briar.android.remotewipe.activate.ActivateRemoteWipeViewModel;
import org.briarproject.briar.android.remotewipe.revoke.RevokeRemoteWipeViewModel;
import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class RemoteWipeModule {

	@Binds
	@IntoMap
	@ViewModelKey(RemoteWipeSetupViewModel.class)
	abstract ViewModel bindRemoteWipeSetupViewModel(
			RemoteWipeSetupViewModel remoteWipeSetupViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(ActivateRemoteWipeViewModel.class)
	abstract ViewModel bindActivateRemoteWipeViewModel(
			ActivateRemoteWipeViewModel activateRemoteWipeViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(RevokeRemoteWipeViewModel.class)
	abstract ViewModel bindRevokeRemoteWipeViewModel(
			RevokeRemoteWipeViewModel RevokeRemoteWipeViewModel);
}
