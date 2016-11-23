package org.briarproject.bramble.api.nullsafety;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation can be applied to a package or class to indicate that
 * the fields, methods and parameters in that element are non-null by default
 * unless:
 * <ul>
 * <li> There is an explicit nullness annotation
 * <li> The method overrides a method in a superclass (in which case the
 * annotation of the corresponding method or parameter in the superclass
 * applies)
 * <li> There is a default nullness annotation applied to a more tightly
 * nested element.
 * </ul>
 */
@Documented
@Nonnull
@TypeQualifierDefault({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface NotNullByDefault {
}
