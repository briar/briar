package org.briarproject.briar.headless;

import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.account.HeadlessAccountModule;
import org.briarproject.bramble.system.DesktopSecureRandomModule;
import org.briarproject.briar.BriarCoreEagerSingletons;
import org.briarproject.briar.BriarCoreModule;

import javax.inject.Singleton;

import dagger.Component;

@Component(modules = {
		BrambleCoreModule.class,
		BriarCoreModule.class,
		DesktopSecureRandomModule.class,
		HeadlessAccountModule.class,
		HeadlessModule.class
})
@Singleton
public interface BriarHeadlessApp
		extends BrambleCoreEagerSingletons, BriarCoreEagerSingletons {
	Router router();
}