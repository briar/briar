package org.briarproject.bramble.api.system;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for injecting a scheduled executor service
 * that can be used to schedule the execution of tasks.
 * <p>
 * The service should <b>only</b> be used for running tasks on other executors
 * at scheduled times.
 * No significant work should be run by the service itself!
 */
@Qualifier
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface Scheduler {
}