package org.motechproject.mds.javassist;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.motechproject.mds.domain.Field;
import org.motechproject.mds.testutil.records.Record;
import org.motechproject.mds.testutil.records.RecordChild;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JavassistHelperTest {

    @Mock
    private CtClass ctClass;

    @Mock
    private CtField ctField;

    @Test
    public void shouldCreateClassPathEntries() {
        assertEquals("this/is/a/test/package.class", JavassistHelper.toClassPath("this.is.a.test.package"));
        assertEquals("java/lang/String.class", JavassistHelper.toClassPath(String.class));
        assertEquals("java/lang/String.class", JavassistHelper.toClassPath(String.class.getName()));
    }

    @Test
    public void shouldCreateProperGenericSignatures() {
        assertEquals("Ljava/util/List<Lorg/motechproject/mds/domain/Field;>;",
                JavassistHelper.genericSignature(List.class, Field.class));
        assertEquals("Ljava/util/List<Lorg/motechproject/mds/domain/Field;>;",
                JavassistHelper.genericSignature(List.class.getName(), Field.class.getName()));
    }

    @Test
    public void shouldFindAndRemoveFieldByName() throws NotFoundException {
        assertFalse(JavassistHelper.containsDeclaredField(ctClass, "name"));
        assertNull(JavassistHelper.findDeclaredField(ctClass, "name"));

        when(ctClass.getDeclaredFields()).thenReturn(new CtField[0]);
        assertFalse(JavassistHelper.containsDeclaredField(ctClass, "name"));
        assertNull(JavassistHelper.findDeclaredField(ctClass, "name"));

        when(ctField.getName()).thenReturn("name");
        when(ctClass.getDeclaredFields()).thenReturn(new CtField[]{ctField});

        assertEquals(ctField, JavassistHelper.findDeclaredField(ctClass, "name"));
        assertTrue(JavassistHelper.containsDeclaredField(ctClass, "name"));

        JavassistHelper.removeDeclaredFieldIfExists(ctClass, "name");
        verify(ctClass).removeField(ctField);
    }

    @Test
    public void shouldCreateGenericParamSignatures() {
        assertEquals("Ljava/lang/Integer;", JavassistHelper.toGenericParam(Integer.class));
        assertEquals("Lorg/motechproject/mds/entity/Test;", JavassistHelper.toGenericParam("org.motechproject.mds.entity.Test"));
    }

    @Test
    public void shouldRecognizeCustomInheritance() {
        assertFalse(JavassistHelper.inheritsFromCustomClass(Object.class));
        assertFalse(JavassistHelper.inheritsFromCustomClass(Enum.class));
        assertFalse(JavassistHelper.inheritsFromCustomClass(null));

        assertFalse(JavassistHelper.inheritsFromCustomClass(Record.class));
        assertTrue(JavassistHelper.inheritsFromCustomClass(RecordChild.class));
    }
}
