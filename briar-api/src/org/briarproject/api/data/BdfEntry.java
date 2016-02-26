package org.briarproject.api.data;

import java.util.Map.Entry;

// This class is not thread-safe
public class BdfEntry implements Entry<String, Object> {

	private final String key;
	private Object value;

	public BdfEntry(String key, Object value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	public Object setValue(Object value) {
		Object oldValue = this.value;
		this.value = value;
		return oldValue;
	}
}
