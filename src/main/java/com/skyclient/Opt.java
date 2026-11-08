package com.skyclient;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a config field that should appear in the menu. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Opt {
    String value();
    String group() default "";
    double min() default 0;
    double max() default 100;
    double step() default 0;
    boolean color() default false;
}
