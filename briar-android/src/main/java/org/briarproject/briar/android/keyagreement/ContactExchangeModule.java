package org.briarproject.briar.android.keyagreement;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class ContactExchangeModule {

	@Binds
	@IntoMap
	@ViewModelKey(ContactExchangeViewModel.class)
	abstract ViewModel bindContactExchangeViewModel(
			ContactExchangeViewModel contactExchangeViewModel);

}
