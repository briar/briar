package org.briarproject.api.clients;

import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.BaseMessageContext;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

public class BdfMessageContext extends BaseMessageContext {

	private final BdfDictionary dictionary;

	public BdfMessageContext(BdfDictionary dictionary,
			Collection<MessageId> dependencies) {

		super(dependencies);
		this.dictionary = dictionary;
	}

	public BdfMessageContext(BdfDictionary dictionary) {
		this(dictionary, null);
	}

	public BdfDictionary getDictionary() {
		return dictionary;
	}

}
