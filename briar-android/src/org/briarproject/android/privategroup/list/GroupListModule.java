package org.briarproject.android.privategroup.list;

import org.briarproject.android.ActivityScope;

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
