package com.ecillie.fbomanager.platform.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface BoundedPage {

	String[] allowedFilters() default {};

	String[] allowedSorts();

	String[] defaultSort();

	String tieBreaker();
}
