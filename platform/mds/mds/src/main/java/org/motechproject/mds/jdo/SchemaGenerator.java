package org.motechproject.mds.jdo;


import org.apache.commons.io.IOUtils;
import org.datanucleus.NucleusContext;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.store.schema.SchemaAwareStoreManager;
import org.motechproject.mds.service.JarGeneratorService;
import org.motechproject.mds.util.ClassName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * The schema generator class is responsible for generating the table schema
 * for entities upon start. Schema for all entity classes has to be generated,
 * otherwise issues might arise in foreign key generation for example.
 * This code runs in the generated entities bundle.
 */
public class SchemaGenerator implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaGenerator.class);

    private JDOPersistenceManagerFactory persistenceManagerFactory;

    public SchemaGenerator(JDOPersistenceManagerFactory persistenceManagerFactory) {
        this.persistenceManagerFactory = persistenceManagerFactory;
    }

    @Override
    public void afterPropertiesSet() {
        generateSchema();
    }

    public void generateSchema() {
        try {
            Set<String> classNames = classNames();

            if (!classNames.isEmpty()) {
                SchemaAwareStoreManager storeManager = getStoreManager();
                storeManager.createSchema(classNames, new Properties());
            }
        } catch (Exception e) {
            LOG.error("Error while creating initial entity schema", e);
        }
    }

    private Set<String> classNames() throws IOException {
        Set<String> classNames = new HashSet<>();

        ClassPathResource resource = new ClassPathResource(JarGeneratorService.ENTITY_LIST_FILE);

        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                for (Object line : IOUtils.readLines(in)) {
                    String className = (String) line;

                    classNames.add(className);
                    classNames.add(ClassName.getHistoryClassName(className));
                    classNames.add(ClassName.getTrashClassName(className));
                }
            }
        } else {
            LOG.warn("List of entity ClassNames is unavailable");
        }

        return classNames;
    }

    private SchemaAwareStoreManager getStoreManager() {
        NucleusContext nucleusContext = persistenceManagerFactory.getNucleusContext();
        return (SchemaAwareStoreManager) nucleusContext.getStoreManager();
    }
}
