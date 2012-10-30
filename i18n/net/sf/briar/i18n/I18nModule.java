package net.sf.briar.i18n;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.i18n.I18n;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class I18nModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(FontManager.class).to(FontManagerImpl.class).in(Singleton.class);
		bind(I18n.class).to(I18nImpl.class).in(Singleton.class);
	}
}
