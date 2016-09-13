package org.briarproject.api.clients;

import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.BaseMessageContext;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class BdfMessageContext extends BaseMessageContext {

	private final BdfDictionary dictionary;

	public BdfMessageContext(@NotNull BdfDictionary dictionary,
			@NotNull Collection<MessageId> dependencies) {

		super(dependencies);
		this.dictionary = dictionary;
	}

	public BdfMessageContext(@NotNull BdfDictionary dictionary) {
		this(dictionary, Collections.<MessageId>emptyList());
	}

	public BdfDictionary getDictionary() {
		return dictionary;
	}

}
