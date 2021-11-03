package org.briarproject.bramble.api.plugin;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for injecting the socks port for the Tor plugin.
 */
@Qualifier
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface TorSocksPort {
}
