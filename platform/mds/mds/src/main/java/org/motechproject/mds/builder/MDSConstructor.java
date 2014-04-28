package org.motechproject.mds.builder;

import java.util.Map;

/**
 * This interface provide method to create a class for the given entity. The implementation of this
 * interface should also construct other classes like repository, service interface and
 * implementation for this service interface.
 */
public interface MDSConstructor {

    void constructEntities(boolean buildDDE);

    void updateFields(Long id, Map<String, String> fieldNameChanges);
}
