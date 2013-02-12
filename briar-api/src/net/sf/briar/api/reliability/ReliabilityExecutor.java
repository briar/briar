package net.sf.briar.api.reliability;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/** Annotation for injecting the executor used by reliability layers. */
@BindingAnnotation
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface ReliabilityExecutor {}