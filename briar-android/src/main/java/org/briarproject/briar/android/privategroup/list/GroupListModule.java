package org.briarproject.briar.android.privategroup.list;

import org.briarproject.briar.android.activity.ActivityScope;

import dagger.Module;
import dagger.Provides;

@Module
public class GroupListModule {

	@ActivityScope
	@Provides
	GroupListController provideGroupListController(
			GroupListControllerImpl groupListController) {
		return groupListController;
	}
}
