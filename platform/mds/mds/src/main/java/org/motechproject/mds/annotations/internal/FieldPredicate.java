package org.motechproject.mds.annotations.internal;

import org.apache.commons.collections.Predicate;
import org.motechproject.mds.annotations.Ignore;
import org.motechproject.mds.reflections.ReflectionsUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

class FieldPredicate implements Predicate {
    private Processor<? extends Annotation> processor;

    public FieldPredicate(Processor<? extends Annotation> processor) {
        this.processor = processor;
    }

    @Override
    public boolean evaluate(Object object) {
        boolean match = object instanceof Field;

        if (match) {
            Field field = (Field) object;

            boolean hasAnnotation = ReflectionsUtil.hasAnnotationClassLoaderSafe(
                    field, field.getDeclaringClass(), processor.getAnnotationType());
            boolean hasIgnoreAnnotation = ReflectionsUtil.hasAnnotationClassLoaderSafe(
                    field, field.getDeclaringClass(), Ignore.class);
            boolean isPublic = isPublic(field.getModifiers());
            boolean isStatic = isStatic(field.getModifiers());

            match = (hasAnnotation || isPublic) && !hasIgnoreAnnotation && !isStatic;
        }

        return match;
    }
}
