package de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.in.www1.artemis.security.Role;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('EDITOR')")
@EnforceRoleInCourse(Role.EDITOR)
public @interface EnforceAtLeastEditorInCourse {

    /**
     * The name of the field in the method parameters that contains the course id.
     * This is used to extract the course id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the course id
     */
    String resourceIdFieldName() default "courseId";
}
