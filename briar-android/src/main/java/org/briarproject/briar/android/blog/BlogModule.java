package org.briarproject.briar.android.blog;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.SharingController;
import org.briarproject.briar.android.controller.SharingControllerImpl;

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

	@ActivityScope
	@Provides
	SharingController provideSharingController(
			SharingControllerImpl sharingController) {
		return sharingController;
	}

}
