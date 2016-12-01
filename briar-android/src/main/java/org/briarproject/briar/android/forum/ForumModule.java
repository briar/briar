package org.briarproject.briar.android.forum;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;

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
