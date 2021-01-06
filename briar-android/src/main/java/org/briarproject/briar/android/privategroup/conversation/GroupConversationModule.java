package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

@Module
public class GroupConversationModule {

	@Module
	public interface BindsModule {
		@Binds
		@IntoMap
		@ViewModelKey(GroupViewModel.class)
		ViewModel bindGroupViewModel(GroupViewModel groupViewModel);
	}

	@ActivityScope
	@Provides
	GroupController provideGroupController(BaseActivity activity,
			GroupControllerImpl groupController) {
		activity.addLifecycleController(groupController);
		return groupController;
	}
}
