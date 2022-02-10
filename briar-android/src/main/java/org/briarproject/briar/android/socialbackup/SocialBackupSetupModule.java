package org.briarproject.briar.android.socialbackup;

import org.briarproject.briar.android.socialbackup.recover.CustodianReturnShardViewModel;
import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class SocialBackupSetupModule {

	@Binds
	@IntoMap
	@ViewModelKey(SocialBackupSetupViewModel.class)
	abstract ViewModel bindSocialBackupSetupViewModel(
			SocialBackupSetupViewModel socialBackupSetupViewModel);

}
