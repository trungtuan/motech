package org.motechproject.testing.osgi.helper;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * A utility class for retrieving services and web application contexts(published as services)
 * from the bundle context. The retrieval is done with retries, their length or duration can be specified.
 */
public final class ServiceRetriever {

    public static final int DEFAULT_WAIT_TIME = 5000;
    public static final int DEFAULT_RETRIES = 10;

    public static WebApplicationContext getWebAppContext(BundleContext bundleContext) {
        return getWebAppContext(bundleContext, bundleContext.getBundle().getSymbolicName());
    }

    public static WebApplicationContext getWebAppContext(BundleContext bundleContext, String bundleSymbolicName) {
        return getWebAppContext(bundleContext, bundleSymbolicName, DEFAULT_WAIT_TIME, DEFAULT_RETRIES);
    }

    public static WebApplicationContext getWebAppContext(BundleContext bundleContext, String bundleSymbolicName,
                                                         int retrievalWaitTime, int retrievalRetries)  {
        WebApplicationContext theContext = null;

        int tries = 0;

        try {
            do {
                ServiceReference[] references =
                        bundleContext.getAllServiceReferences(WebApplicationContext.class.getName(), null);

                if (references != null) {
                    for (ServiceReference ref : references) {
                        if (bundleSymbolicName.equals(ref.getBundle().getSymbolicName())) {
                            theContext = (WebApplicationContext) bundleContext.getService(ref);
                            break;
                        }
                    }
                }

                ++tries;
                Thread.sleep(retrievalWaitTime);
            } while (theContext == null && tries < retrievalRetries);
        } catch (InvalidSyntaxException | InterruptedException e) {
            fail("Unable to retrieve web application");
        }

        assertNotNull("Unable to retrieve the bundle context for " + bundleSymbolicName, theContext);

        return theContext;
    }

    public static  <T> T getService(BundleContext bundleContext, Class<T> clazz) {
        return getService(bundleContext, clazz, DEFAULT_WAIT_TIME, DEFAULT_RETRIES);
    }

    public static Object getService(BundleContext bundleContext, String className) {
        return getService(bundleContext, className, DEFAULT_WAIT_TIME, DEFAULT_RETRIES, false);
    }

    public static Object getService(BundleContext bundleContext, String className, boolean checkAllReferences) {
        return getService(bundleContext, className, DEFAULT_WAIT_TIME, DEFAULT_RETRIES, checkAllReferences);
    }

    public static  <T> T getService(BundleContext bundleContext, Class<T> clazz,
                                    int retrievalWaitTime, int retrievalRetries) {
        return (T) getService(bundleContext, clazz.getName(), retrievalWaitTime, retrievalRetries, false);
    }

    public static Object getService(BundleContext bundleContext, String className,
                                    int retrievalWaitTime, int retrievalRetries, boolean checkAllReferences) {
        Object service = null;

        int tries = 0;

        try {
            do {
                ServiceReference ref = null;
                if (checkAllReferences) {
                    ServiceReference<?>[] allServiceReferences = bundleContext.getAllServiceReferences(className, null);
                    if (allServiceReferences != null) {
                        ref = allServiceReferences[0];
                    }
                } else {
                    ref = bundleContext.getServiceReference(className);
                }
                if (ref != null) {
                    service = bundleContext.getService(ref);
                    break;
                }

                ++tries;
                Thread.sleep(retrievalWaitTime);
            } while (tries < retrievalRetries);
        } catch (InterruptedException | InvalidSyntaxException e) {
            fail("Unable to retrieve service of class " + className);
        }

        assertNotNull("Unable to retrieve the service " + className, service);

        return service;
    }

    private ServiceRetriever() {
    }
}
