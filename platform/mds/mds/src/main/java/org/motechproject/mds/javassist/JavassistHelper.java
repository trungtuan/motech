package org.motechproject.mds.javassist;


import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.osgi.framework.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Helper class for javassist related tasks. Helps with generic signature generation,
 * plus methods related with analyzing and loading javassist class representations.
 */
public final class JavassistHelper {

    public static String genericSignature(Class<?> typeClass, Class<?> genericParam) {
        return genericSignature(typeClass.getName(), genericParam.getName());
    }

    public static String genericSignature(Class<?> typeClass, String genericParam) {
        return genericSignature(typeClass.getName(), genericParam);
    }

    public static String genericSignature(String typeClass, String genericParam) {
        return String.format("L%s<%s>;", Descriptor.toJvmName(typeClass), toGenericParam(genericParam));
    }

    public static String toClassPath(Class<?> clazz) {
        return toClassPath(clazz.getName());
    }

    public static String toClassPath(String clazz) {
        return toClassPath(clazz, true);
    }

    public static String toClassPath(String clazz, boolean extension) {
        return String.format("%s%s", Descriptor.toJvmName(clazz), extension ? ".class" : "");
    }

    public static String toGenericParam(Class<?> clazz) {
        return toGenericParam(clazz.getName());
    }

    public static String toGenericParam(String clazz) {
        return Descriptor.of(clazz);
    }

    public static CtClass loadClass(Bundle bundle, String className, ClassPool classPool) throws IOException {
        // return if already loaded
        CtClass existing = classPool.getOrNull(className);
        if (existing != null) {
            return existing;
        }

        // load from the bundle
        CtClass clazz = null;

        URL classUrl = bundle.getResource(toClassPath(className));
        if (classUrl != null) {
            try (InputStream classInputStream = classUrl.openStream()) {
                clazz = classPool.makeClass(classInputStream);
            }
        }

        return clazz;
    }

    public static CtField findDeclaredField(CtClass ctClass, String fieldName) {
        CtField[] declaredFields = ctClass.getDeclaredFields();

        if (ArrayUtils.isNotEmpty(declaredFields)) {
            for (CtField field : declaredFields) {
                if (StringUtils.equals(fieldName, field.getName())) {
                    return field;
                }
            }
        }

        return null;
    }

    public static CtField findField(CtClass ctClass, String fieldName) {
        try {
            return ctClass.getField(fieldName);
        } catch (NotFoundException e) {
            return null;
        }
    }

    public static boolean containsDeclaredField(CtClass ctClass, String fieldName) {
        return findDeclaredField(ctClass, fieldName) != null;
    }

    public static boolean containsField(CtClass ctClass, String fieldName) {
        return findField(ctClass, fieldName) != null;
    }

    public static void removeDeclaredFieldIfExists(CtClass ctClass, String fieldName) {
        CtField field = findDeclaredField(ctClass, fieldName);
        if (field != null) {
            try {
                ctClass.removeField(field);
            } catch (NotFoundException e) {
                throw new IllegalStateException("Field was removed from class before we could do it - possible concurrency issue");
            }
        }
    }

    public static void removeFieldIfExists(CtClass ctClass, String fieldName) {
        CtField field = findField(ctClass, fieldName);
        if (field != null) {
            try {
                ctClass.removeField(field);
            } catch (NotFoundException e) {
                throw new IllegalStateException("Field was removed from class before we could do it - possible concurrency issue");
            }
        }
    }

    public static CtMethod findDeclaredMethod(CtClass ctClass, String methodName) {
        CtMethod[] declaredMethods = ctClass.getDeclaredMethods();

        if (ArrayUtils.isNotEmpty(declaredMethods)) {
            for (CtMethod method : declaredMethods) {
                if (StringUtils.equals(method.getName(), methodName)) {
                    return method;
                }
            }
        }

        return null;
    }

    public static CtMethod findMethod(CtClass ctClass, String methodName) {
        CtMethod[] methods = ctClass.getMethods();

        if (ArrayUtils.isNotEmpty(methods)) {
            for (CtMethod method : methods) {
                if (StringUtils.equals(method.getName(), methodName)) {
                    return method;
                }
            }
        }

        return null;
    }

    public static boolean containsDeclaredMethod(CtClass ctClass, String methodName) {
        return findDeclaredMethod(ctClass, methodName) != null;
    }

    public static boolean containsMethod(CtClass ctClass, String methodName) {
        return findMethod(ctClass, methodName) != null;
    }

    public static void removeDeclaredMethodIfExists(CtClass ctClass, String methodName) {
        CtMethod method = findDeclaredMethod(ctClass, methodName);
        if (method != null) {
            try {
                ctClass.removeMethod(method);
            } catch (NotFoundException e) {
                throw new IllegalStateException("Method was removed from class before we could do it - possible concurrency issue");
            }
        }
    }

    public static void removeMethodIfExists(CtClass ctClass, String methodName) {
        CtMethod method = findMethod(ctClass, methodName);
        if (method != null) {
            try {
                ctClass.removeMethod(method);
            } catch (NotFoundException e) {
                throw new IllegalStateException("Method was removed from class before we could do it - possible concurrency issue");
            }
        }
    }

    public static boolean hasInterface(CtClass ctClass, CtClass ctInterface) throws NotFoundException {
        CtClass[] interfaces = ctClass.getInterfaces();
        if (ArrayUtils.isNotEmpty(interfaces)) {
            for (CtClass declaredInterface : interfaces) {
                if (declaredInterface.equals(ctInterface)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean inheritsFromCustomClass(Class<?> clazz) {
        return clazz != null && clazz.getSuperclass() != null &&
                !Object.class.getName().equals(clazz.getSuperclass().getName()) &&
                !Enum.class.getName().equals(clazz.getSuperclass().getName());
    }

    private JavassistHelper() {
    }
}
