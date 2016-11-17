package org.briarproject.android.blogs;

import org.briarproject.android.ActivityScope;
import org.briarproject.android.BaseActivity;

import dagger.Module;
import dagger.Provides;

@Module
public class BlogModule {

	@ActivityScope
	@Provides
	BlogController provideBlogController(BaseActivity activity,
			BlogControllerImpl blogController) {
		activity.addLifecycleController(blogController);
		return blogController;
	}

	@ActivityScope
	@Provides
	FeedController provideFeedController(FeedControllerImpl feedController) {
		return feedController;
	}
}
