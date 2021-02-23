package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.db.DbException;

/**
 * Thrown when an attempt is made to create a social account backup but a
 * backup already exists. This exception may occur due to concurrent updates
 * and does not indicate a database error.
 */
public class BackupExistsException extends DbException {
}
