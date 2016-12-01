package org.briarproject.briar.android.blog;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;

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
