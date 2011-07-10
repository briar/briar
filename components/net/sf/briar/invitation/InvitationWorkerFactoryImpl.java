package net.sf.briar.invitation;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.invitation.InvitationWorkerFactory;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class InvitationWorkerFactoryImpl implements InvitationWorkerFactory {

	private final DatabaseComponent databaseComponent;
	private final WriterFactory writerFactory;

	@Inject
	InvitationWorkerFactoryImpl(DatabaseComponent databaseComponent,
			WriterFactory writerFactory) {
		this.databaseComponent = databaseComponent;
		this.writerFactory = writerFactory;
	}

	public Runnable createWorker(InvitationCallback callback,
			InvitationParameters parameters) {
		return new InvitationWorker(callback, parameters, databaseComponent,
				writerFactory);
	}
}
