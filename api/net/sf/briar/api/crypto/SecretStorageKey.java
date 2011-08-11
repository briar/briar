package net.sf.briar.api.crypto;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

/**
 * Annotation for injecting the key that is used for encrypting and decrypting
 * secrets stored in the database.
 */
@BindingAnnotation
@Target({ PARAMETER })
@Retention(RUNTIME)
public @interface SecretStorageKey {}
