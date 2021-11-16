package org.briarproject.briar.android.mailbox;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public interface MailboxModule {

	@Binds
	@IntoMap
	@ViewModelKey(MailboxPairViewModel.class)
	ViewModel bindMailboxViewModel(
			MailboxPairViewModel mailboxPairViewModel);

}
