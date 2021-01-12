package org.briarproject.briar.android.blog;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.SharingController;
import org.briarproject.briar.android.controller.SharingControllerImpl;
import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;

@Module
public class BlogModule {

	@Module
	public abstract static class BindsModule {
		@Binds
		@IntoMap
		@ViewModelKey(FeedViewModel.class)
		abstract ViewModel bindFeedViewModel(FeedViewModel feedViewModel);

		@Binds
		@IntoMap
		@ViewModelKey(BlogViewModel.class)
		abstract ViewModel bindBlogViewModel(BlogViewModel blogViewModel);
	}

	@ActivityScope
	@Provides
	BlogController provideBlogController(BaseActivity activity,
			BlogControllerImpl blogController) {
		activity.addLifecycleController(blogController);
		return blogController;
	}

	@ActivityScope
	@Provides
	SharingController provideSharingController(
			SharingControllerImpl sharingController) {
		return sharingController;
	}

}
