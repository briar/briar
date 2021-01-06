package org.briarproject.briar.android.forum;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

@Module
public class ForumModule {

	@Module
	public interface BindsModule {
		@Binds
		@IntoMap
		@ViewModelKey(ForumListViewModel.class)
		ViewModel bindForumListViewModel(ForumListViewModel forumListViewModel);

		@Binds
		@IntoMap
		@ViewModelKey(ForumViewModel.class)
		ViewModel bindForumViewModel(ForumViewModel forumViewModel);
	}

	@ActivityScope
	@Provides
	ForumController provideForumController(BaseActivity activity,
			ForumControllerImpl forumController) {
		activity.addLifecycleController(forumController);
		return forumController;
	}

}
