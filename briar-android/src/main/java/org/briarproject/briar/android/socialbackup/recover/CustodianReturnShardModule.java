package org.briarproject.briar.android.socialbackup.recover;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class CustodianReturnShardModule {

	@Binds
	@IntoMap
	@ViewModelKey(CustodianReturnShardViewModel.class)
	abstract ViewModel bindCustodianReturnShardViewModel(
			CustodianReturnShardViewModel custodianReturnShardViewModel);

}
