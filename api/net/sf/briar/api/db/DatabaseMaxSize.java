package net.sf.briar.api.db;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/** Annotation for injecting the maximum size in bytes of the database. */
@BindingAnnotation
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface DatabaseMaxSize {}
