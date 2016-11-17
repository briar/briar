package org.briarproject.android.forum;

import org.briarproject.android.ActivityScope;
import org.briarproject.android.BaseActivity;

import dagger.Module;
import dagger.Provides;

@Module
public class ForumModule {

	@ActivityScope
	@Provides
	ForumController provideForumController(BaseActivity activity,
			ForumControllerImpl forumController) {
		activity.addLifecycleController(forumController);
		return forumController;
	}

}
