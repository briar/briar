package org.briarproject.api.db;

public class DbException extends Exception {

	private static final long serialVersionUID = 3706581789209939441L;

	public DbException() {}

	public DbException(Throwable t) {
		super(t);
	}
}
