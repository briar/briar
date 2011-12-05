package net.sf.briar.api.protocol;

import java.security.GeneralSecurityException;

public interface UnverifiedBatch {

	Batch verify() throws GeneralSecurityException;
}
