package org.briarproject.api.clients;

import org.briarproject.api.db.DbException;

/**
 * Thrown when a database operation is attempted as part of a protocol session
 * and the operation is not applicable to the current protocol state.
 */
public class ProtocolStateException extends DbException {

}
