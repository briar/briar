package org.briarproject.android;

import android.app.Activity;
import android.content.SharedPreferences;

import org.briarproject.android.controller.BriarController;
import org.briarproject.android.controller.BriarControllerImpl;
import org.briarproject.android.controller.ConfigController;
import org.briarproject.android.controller.ConfigControllerImpl;
import org.briarproject.android.controller.DbController;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.NavDrawerController;
import org.briarproject.android.controller.NavDrawerControllerImpl;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.PasswordControllerImpl;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.SetupControllerImpl;

import dagger.Module;
import dagger.Provides;

import static android.content.Context.MODE_PRIVATE;
import static org.briarproject.android.BriarService.BriarServiceConnection;

@Module
public class ActivityModule {

	private final BaseActivity activity;

	public ActivityModule(BaseActivity activity) {
		this.activity = activity;
	}

	@ActivityScope
	@Provides
	BaseActivity provideBaseActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	Activity provideActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	SetupController provideSetupController(
			SetupControllerImpl setupController) {
		return setupController;
	}

	@ActivityScope
	@Provides
	ConfigController provideConfigController(
			ConfigControllerImpl configController) {
		return configController;
	}

	@ActivityScope
	@Provides
	SharedPreferences provideSharedPreferences(Activity activity) {
		return activity.getSharedPreferences("db", MODE_PRIVATE);
	}

	@ActivityScope
	@Provides
	PasswordController providePasswordController(
			PasswordControllerImpl passwordController) {
		return passwordController;
	}

	@ActivityScope
	@Provides
	protected BriarController provideBriarController(
			BriarControllerImpl briarController) {
		activity.addLifecycleController(briarController);
		return briarController;
	}

	@ActivityScope
	@Provides
	DbController provideDBController(DbControllerImpl dbController) {
		return dbController;
	}

	@ActivityScope
	@Provides
	NavDrawerController provideNavDrawerController(
			NavDrawerControllerImpl navDrawerController) {
		activity.addLifecycleController(navDrawerController);
		return navDrawerController;
	}

	@ActivityScope
	@Provides
	BriarServiceConnection provideBriarServiceConnection() {
		return new BriarServiceConnection();
	}

}
