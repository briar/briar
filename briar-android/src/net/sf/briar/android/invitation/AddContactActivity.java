package net.sf.briar.android.invitation;

import static java.util.logging.Level.WARNING;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import net.sf.briar.android.BriarActivity;
import net.sf.briar.api.android.BundleEncrypter;
import net.sf.briar.api.android.ReferenceManager;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.invitation.InvitationListener;
import net.sf.briar.api.invitation.InvitationState;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.invitation.InvitationTaskFactory;
import android.os.Bundle;

import com.google.inject.Inject;

public class AddContactActivity extends BriarActivity
implements InvitationListener {

	private static final Logger LOG =
			Logger.getLogger(AddContactActivity.class.getName());

	@Inject private BundleEncrypter bundleEncrypter;
	@Inject private CryptoComponent crypto;
	@Inject private DatabaseComponent db;
	@Inject @DatabaseExecutor private Executor dbExecutor;
	@Inject private InvitationTaskFactory invitationTaskFactory;
	@Inject private ReferenceManager referenceManager;

	// All of the following must be accessed on the UI thread
	private AddContactView view = null;
	private InvitationTask task = null;
	private long taskHandle = -1;
	private String networkName = null;
	private boolean useBluetooth = false;
	private int localInvitationCode = -1, remoteInvitationCode = -1;
	private int localConfirmationCode = -1, remoteConfirmationCode = -1;
	private boolean connectionFailed = false;
	private boolean localCompared = false, remoteCompared = false;
	private boolean localMatched = false, remoteMatched = false;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(state == null || !bundleEncrypter.decrypt(state)) {
			// This is a new activity or the app has restarted
			setView(new NetworkSetupView(this));
		} else {
			// Restore the activity's state
			networkName = state.getString("net.sf.briar.NETWORK_NAME");
			useBluetooth = state.getBoolean("net.sf.briar.USE_BLUETOOTH");
			taskHandle = state.getLong("net.sf.briar.TASK_HANDLE", -1);
			task = referenceManager.getReference(taskHandle,
					InvitationTask.class);
			if(task == null) {
				// No background task - we must be in an initial or final state
				localInvitationCode = state.getInt("net.sf.briar.LOCAL_CODE");
				remoteInvitationCode = state.getInt("net.sf.briar.REMOTE_CODE");
				connectionFailed = state.getBoolean("net.sf.briar.FAILED");
				if(state.getBoolean("net.sf.briar.MATCHED")) {
					localCompared = remoteCompared = true;
					localMatched = remoteMatched = true;
				}
				// Set the appropriate view for the state
				if(localInvitationCode == -1) {
					setView(new NetworkSetupView(this));
				} else if(remoteInvitationCode == -1) {
					setView(new InvitationCodeView(this));
				} else if(connectionFailed) {
					setView(new ConnectionFailedView(this));
				} else if(localMatched && remoteMatched) {
					setView(new ContactAddedView(this));
				} else {
					setView(new CodesDoNotMatchView(this));
				}
			} else {
				// A background task exists - listen to it and get its state
				InvitationState s = task.addListener(this);
				localInvitationCode = s.getLocalInvitationCode();
				remoteInvitationCode = s.getRemoteInvitationCode();
				localConfirmationCode = s.getLocalConfirmationCode();
				remoteConfirmationCode = s.getRemoteConfirmationCode();
				connectionFailed = s.getConnectionFailed();
				localCompared = s.getLocalCompared();
				remoteCompared = s.getRemoteCompared();
				localMatched = s.getLocalMatched();
				remoteMatched = s.getRemoteMatched();
				// Set the appropriate view for the state
				if(localInvitationCode == -1) {
					setView(new NetworkSetupView(this));
				} else if(remoteInvitationCode == -1) {
					setView(new InvitationCodeView(this));
				} else if(localConfirmationCode == -1) {
					setView(new ConnectionView(this));
				} else if(connectionFailed) {
					setView(new ConnectionFailedView(this));
				} else if(!localCompared) {
					setView(new ConfirmationCodeView(this));
				} else if(!remoteCompared) {
					setView(new WaitForContactView(this));
				} else if(localMatched && remoteMatched) {
					setView(new ContactAddedView(this));
				} else {
					setView(new CodesDoNotMatchView(this));
				}
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		view.populate();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		state.putString("net.sf.briar.NETWORK_NAME", networkName);
		state.putBoolean("net.sf.briar.USE_BLUETOOTH", useBluetooth);
		state.putInt("net.sf.briar.LOCAL_CODE", localInvitationCode);
		state.putInt("net.sf.briar.REMOTE_CODE", remoteInvitationCode);
		state.putBoolean("net.sf.briar.FAILED", connectionFailed);
		state.putBoolean("net.sf.briar.MATCHED", localMatched && remoteMatched);
		if(task != null) state.putLong("net.sf.briar.TASK_HANDLE", taskHandle);
		bundleEncrypter.encrypt(state);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(task != null) task.removeListener(this);
	}

	void setView(AddContactView view) {
		this.view = view;
		view.init(this);
		setContentView(view);
	}

	void reset(AddContactView view) {
		task = null;
		taskHandle = -1;
		networkName = null;
		useBluetooth = false;
		localInvitationCode = -1;
		localConfirmationCode = remoteConfirmationCode = -1;
		connectionFailed = false;
		localCompared = remoteCompared = false;
		localMatched = remoteMatched = false;
		setView(view);
	}

	void setNetworkName(String networkName) {
		this.networkName = networkName;
	}

	String getNetworkName() {
		return networkName;
	}

	void setUseBluetooth(boolean useBluetooth) {
		this.useBluetooth = useBluetooth;
	}

	boolean getUseBluetooth() {
		return useBluetooth;
	}

	int getLocalInvitationCode() {
		if(localInvitationCode == -1)
			localInvitationCode = crypto.generateInvitationCode();
		return localInvitationCode;
	}

	void remoteInvitationCodeEntered(int code) {
		setView(new ConnectionView(this));
		// FIXME: These calls are blocking the UI thread for too long
		task = invitationTaskFactory.createTask(localInvitationCode, code);
		taskHandle = referenceManager.putReference(task, InvitationTask.class);
		task.addListener(AddContactActivity.this);
		task.addListener(new ReferenceCleaner(referenceManager, taskHandle));
		task.connect();
	}

	int getLocalConfirmationCode() {
		return localConfirmationCode;
	}

	void remoteConfirmationCodeEntered(int code) {
		if(code == remoteConfirmationCode) {
			localMatched = true;
			if(remoteMatched) setView(new ContactAddedView(this));
			else if(remoteCompared) setView(new CodesDoNotMatchView(this));
			else setView(new WaitForContactView(this));
			task.localConfirmationSucceeded();
		} else {
			setView(new CodesDoNotMatchView(this));
			task.localConfirmationFailed();
		}
	}

	void addContactAndFinish(final String nickname) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					db.addContact(nickname);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
		finish();
	}

	public void connectionSucceeded(final int localCode, final int remoteCode) {
		runOnUiThread(new Runnable() {
			public void run() {
				localConfirmationCode = localCode;
				remoteConfirmationCode = remoteCode;
				setView(new ConfirmationCodeView(AddContactActivity.this));
			}
		});
	}

	public void connectionFailed() {
		runOnUiThread(new Runnable() {
			public void run() {
				connectionFailed = true;
				setView(new ConnectionFailedView(AddContactActivity.this));
			}
		});
	}

	public void remoteConfirmationSucceeded() {
		runOnUiThread(new Runnable() {
			public void run() {
				remoteCompared = true;
				remoteMatched = true;
				if(localMatched)
					setView(new ContactAddedView(AddContactActivity.this));
			}
		});
	}

	public void remoteConfirmationFailed() {
		runOnUiThread(new Runnable() {
			public void run() {
				remoteCompared = true;
				if(localMatched)
					setView(new CodesDoNotMatchView(AddContactActivity.this));
			}
		});
	}

	/**
	 * Cleans up the reference to the invitation task when the task completes.
	 * This class is static to prevent memory leaks.
	 */
	private static class ReferenceCleaner implements InvitationListener {

		private final ReferenceManager referenceManager;
		private final long handle;

		private ReferenceCleaner(ReferenceManager referenceManager,
				long handle) {
			this.referenceManager = referenceManager;
			this.handle = handle;
		}

		public void connectionSucceeded(int localCode, int remoteCode) {
			// Wait for remote confirmation to succeed or fail
		}

		public void connectionFailed() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		public void remoteConfirmationSucceeded() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}

		public void remoteConfirmationFailed() {
			referenceManager.removeReference(handle, InvitationTask.class);
		}
	}
}
