package org.briarproject.bramble.api.event;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for injecting the executor for broadcasting events and running
 * tasks that need to run in a defined order with respect to events. Also used
 * for annotating methods that should run on the event executor.
 * <p>
 * The contract of this executor is that tasks are run in the order they're
 * submitted, tasks are not run concurrently, and submitting a task will never
 * block. Tasks must not block. Tasks submitted during shutdown are discarded.
 */
@Qualifier
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface EventExecutor {
}
