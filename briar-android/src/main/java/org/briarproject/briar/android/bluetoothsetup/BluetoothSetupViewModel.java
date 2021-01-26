package org.briarproject.briar.android.bluetoothsetup;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

@NotNullByDefault
public class BluetoothSetupViewModel extends DbViewModel {

	private final static Logger LOG =
			getLogger(BluetoothSetupViewModel.class.getName());

	@Inject
	BluetoothSetupViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
	}
}
