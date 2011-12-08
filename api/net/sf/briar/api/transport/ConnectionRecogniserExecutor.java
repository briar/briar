package net.sf.briar.api.transport;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/**
 * Annotation for injecting the executor for recognising incoming connections.
 */
@BindingAnnotation
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface ConnectionRecogniserExecutor {}