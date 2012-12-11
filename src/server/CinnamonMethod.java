package server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A CinnamonMethod will be exposed as part of the public API. Note that it must conform to the 
 * following rules.
 * <ul>
 * 	<li>Return type: server.interfaces.Response</li>
 * 	<li>Param: Map<String, ?></li>
 * </ul>
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CinnamonMethod {
	String checkTrigger() default "false";
}
