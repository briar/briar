package org.briarproject.briar.android.viewmodel;

import org.briarproject.briar.android.contact.add.remote.AddContactViewModel;
import org.briarproject.briar.android.contact.add.remote.PendingContactListViewModel;
import org.briarproject.briar.android.conversation.ConversationViewModel;
import org.briarproject.briar.android.conversation.ImageViewModel;
import org.briarproject.briar.android.conversation.RemovableDriveViewModel;

import javax.inject.Singleton;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class ViewModelModule {

	@Binds
	@IntoMap
	@ViewModelKey(ConversationViewModel.class)
	abstract ViewModel bindConversationViewModel(
			ConversationViewModel conversationViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(ImageViewModel.class)
	abstract ViewModel bindImageViewModel(
			ImageViewModel imageViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(AddContactViewModel.class)
	abstract ViewModel bindAddContactViewModel(
			AddContactViewModel addContactViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(PendingContactListViewModel.class)
	abstract ViewModel bindPendingRequestsViewModel(
			PendingContactListViewModel pendingContactListViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(RemovableDriveViewModel.class)
	abstract ViewModel bindRemovableDriveViewModel(
			RemovableDriveViewModel removableDriveViewModel);

	@Binds
	@Singleton
	abstract ViewModelProvider.Factory bindViewModelFactory(
			ViewModelFactory viewModelFactory);

}
