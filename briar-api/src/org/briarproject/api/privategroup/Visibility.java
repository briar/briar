package org.briarproject.api.privategroup;

public enum Visibility {

	INVISIBLE(0),
	VISIBLE(1),
	REVEALED_BY_YOU(2),
	REVEALED_BY_CONTACT(3);

	int value;

	Visibility(int value) {
		this.value = value;
	}

	public static Visibility valueOf(int value) {
		for (Visibility v : values()) if (v.value == value) return v;
		throw new IllegalArgumentException();
	}

	public int getInt() {
		return value;
	}

}
