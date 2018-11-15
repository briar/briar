package org.briarproject.bramble.api.sync.validation;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

/**
 * Responsible for managing message validators and passing them messages to
 * validate.
 */
@NotNullByDefault
public interface ValidationManager {

	/**
	 * Registers the {@link MessageValidator} for the given client. This method
	 * should be called before
	 * {@link LifecycleManager#startServices(SecretKey)}.
	 */
	void registerMessageValidator(ClientId c, int majorVersion,
			MessageValidator v);

	/**
	 * Registers the {@link IncomingMessageHook} for the given client. The hook
	 * will be called once for each incoming message that passes validation.
	 * This method should be called before
	 * {@link LifecycleManager#startServices(SecretKey)}.
	 */
	void registerIncomingMessageHook(ClientId c, int majorVersion,
			IncomingMessageHook hook);
}
