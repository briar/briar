package net.sf.briar.api.db;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/**
 * Annotation for injecting the password from which the database encryption
 * key is derived.
 */
@BindingAnnotation
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface DatabasePassword {}
