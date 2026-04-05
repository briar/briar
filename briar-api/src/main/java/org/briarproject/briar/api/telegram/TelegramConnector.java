package org.briarproject.briar.api.telegram;
import org.briarproject.nullsafety.NotNullByDefault;
@NotNullByDefault
public interface TelegramConnector {
	boolean isEnabled();
}
