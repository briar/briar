package org.briarproject.briar.android.socialbackup.creation;

import org.briarproject.briar.android.activity.ActivityScope;

import dagger.Module;
import dagger.Provides;

@Module
public class CreateBackupModule {

	@ActivityScope
	@Provides
	CreateBackupController provideCreateGroupController(
			CreateBackupControllerImpl createBackupController) {
		return createBackupController;
	}

}
