package org.briarproject.briar.android.socialbackup.recover;

import org.briarproject.briar.android.viewmodel.ViewModelKey;
import org.briarproject.briar.api.socialbackup.recovery.RestoreAccount;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;


@Module
public abstract class OwnerReturnShardModule {

	@Binds
	@IntoMap
	@ViewModelKey(OwnerReturnShardViewModel.class)
	abstract ViewModel bindOwnerReturnShardViewModel(
			OwnerReturnShardViewModel ownerReturnShardViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(RestoreAccountViewModel.class)
	abstract ViewModel bindRestoreAccountViewModel(
			RestoreAccountViewModel restoreAccountViewModel);
}
