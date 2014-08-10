package org.motechproject.mds.annotations.internal;

import org.apache.commons.collections.Predicate;
import org.motechproject.mds.annotations.Ignore;
import org.motechproject.mds.reflections.ReflectionsUtil;
import org.motechproject.mds.util.MemberUtil;

import java.lang.reflect.Method;

class MethodPredicate implements Predicate {

    @Override
    public boolean evaluate(Object object) {
        boolean match = object instanceof Method;

        if (match) {
            Method method = (Method) object;
            boolean isNotFromObject = method.getDeclaringClass() != Object.class;
            boolean isGetter = MemberUtil.isGetter(method);
            boolean isSetter = MemberUtil.isSetter(method);
            boolean hasIgnoreAnnotation = ReflectionsUtil.hasAnnotationClassLoaderSafe(
                    method, method.getDeclaringClass(), Ignore.class);

            match = (isNotFromObject && (isGetter || isSetter)) && !hasIgnoreAnnotation;
        }

        return match;
    }
}
