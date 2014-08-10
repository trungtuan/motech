package org.motechproject.server.osgi;

import org.eclipse.gemini.blueprint.OsgiException;
import org.eclipse.gemini.blueprint.util.OsgiBundleUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * The PlatformActivator is responsible for starting up MOTECH. Formerly this code lived in the WAR archive
 * (OSGiFrameworkService). It was moved to its own bundle, so that it can be reused during PAX integration tests.
 * The activator first starts 3rd party bundles, then MDS and its dependencies. After MDS is started it continues to start
 * platform bundles. When it gets the startup event from the server-bundle(meaning Motech is initialized) it will start
 * other modules.
 */
public class PlatformActivator implements BundleActivator {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformActivator.class);

    private boolean httpServiceRegistered;
    private boolean startupEventReceived;
    private boolean platformStarted;

    private BundleContext bundleContext;

    private final Object lock = new Object();

    private Map<BundleType, List<Bundle>> bundlesByType = new HashMap<>();

    @Override
    public void start(BundleContext context) throws InvalidSyntaxException, ClassNotFoundException {
        this.bundleContext = context;

        // first categorize the bundles in a map, for our own convenience
        categorizeBundles();

        // we register the listeners for services and events
        registerListeners();

        startHttp();

        // start all 3rd party libraries
        startBundles(BundleType.THIRD_PARTY_BUNDLE);

        // start the http bridge
        startBundles(BundleType.HTTP_BUNDLE);

        // start platform bundles on which MDS depends on
        startBundles(BundleType.PLATFORM_BUNDLE_PRE_MDS);

        // start MDS
        startBundles(BundleType.MDS_BUNDLE);

        // continues in postMdsStart() after MDS gets started

        // in case there is no MDS bundle(test environment), we continue with startup right away
        if (!bundlesByType.containsKey(BundleType.MDS_BUNDLE)) {
            postMdsStartup();
        }
    }

    private void postMdsStartup() throws ClassNotFoundException {
        LOG.info("MDS started, continuing startup");

        // we start bundles required for web-security start
        startBundles(BundleType.PLATFORM_BUNDLE_PRE_WS);

        // we start web-security itself
        startBundles(BundleType.WS_BUNDLE);

        // make sure security is started
        if (bundlesByType.containsKey(BundleType.WS_BUNDLE)) {
            verifyBundleState(Bundle.ACTIVE, PlatformConstants.SECURITY_BUNDLE_SYMBOLIC_NAME);
        }

        // we start other platform bundles
        startBundles(BundleType.PLATFORM_BUNDLE_POST_WS);

        platformStarted();

        LOG.info("MOTECH Platform started");
    }

    @Override
    public void stop(BundleContext context) {
        LOG.info("MOTECH Platform bundle stopped");
    }

    private void registerListeners() throws InvalidSyntaxException, ClassNotFoundException {
        // HTTP service and the startup event coming from the server-bundle are required for booting up modules
        registerHttpServiceListener();
        registerStartupListener();

        // We want to also know when MDS starts
        registerMdsStartupListener();
    }

    private void registerHttpServiceListener() throws InvalidSyntaxException {
        bundleContext.addServiceListener(new ServiceListener() {
            @Override
            public void serviceChanged(ServiceEvent event) {
                if (event.getType() == ServiceEvent.REGISTERED) {
                    LOG.info("Http service registered");
                    httpServiceRegistered();
                }
            }
        }, String.format("(&(%s=%s))", Constants.OBJECTCLASS, HttpService.class.getName()));
    }

    private void registerStartupListener() throws ClassNotFoundException {
        Dictionary<String, String[]> properties = new Hashtable<>();
        properties.put(EventConstants.EVENT_TOPIC, new String[]{ PlatformConstants.STARTUP_TOPIC });

        bundleContext.registerService(EventHandler.class.getName(), new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                allowStartup();
            }
        }, properties);
    }

    private void registerMdsStartupListener() {
        Dictionary<String, String[]> properties = new Hashtable<>();
        properties.put(EventConstants.EVENT_TOPIC, new String[]{ PlatformConstants.MDS_STARTUP_TOPIC});

        bundleContext.registerService(EventHandler.class.getName(), new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                try {
                    postMdsStartup();
                } catch (ClassNotFoundException e) {
                    throw new OsgiException(e);
                }
            }
        }, properties);
    }

    private void startHttp() {
        try {
            Bundle httpBundle = OsgiBundleUtils.findBundleBySymbolicName(bundleContext, PlatformConstants.HTTP_BRIDGE_BUNDLE);
            if (httpBundle != null) {
                startBundle(httpBundle, BundleType.HTTP_BUNDLE);
            } else {
                LOG.warn("Felix http bundle unavailable, http endpoints will not be active");
            }
        } catch (BundleException e) {
            LOG.error("Error while starting the http bundle", e);
        }
    }

    private void startBundles(BundleType bundleType) {
        LOG.info("Starting bundles of type {}", bundleType.name());

        List<Bundle> bundlesToStart = bundlesByType.get(bundleType);

        if (bundlesToStart != null) {
            for (Bundle bundle : bundlesToStart) {
                if (shouldStartBundle(bundle)) {
                    try {
                        startBundle(bundle, bundleType);
                    } catch (BundleException e) {
                        LOG.error("Error while starting bundle " + bundle.getSymbolicName(), e);
                    }
                }
            }
        }
    }

    private void startBundle(Bundle bundle, BundleType bundleType) throws BundleException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting {} {}", new String[]{bundleType.name(), bundle.getSymbolicName()});
        }
        bundle.start();
    }

    private boolean shouldStartBundle(Bundle bundle) {
        return !PlatformConstants.PLATFORM_BUNDLE_SYMBOLIC_NAME.equals(bundle.getSymbolicName()) &&
                (bundle.getState() == Bundle.INSTALLED || bundle.getState() == Bundle.RESOLVED);
    }

    private void httpServiceRegistered() {
        synchronized (lock) {
            httpServiceRegistered = true;
        }
        startupModules();
    }

    private void allowStartup() {
        synchronized (lock) {
            startupEventReceived = true;
        }
        startupModules();
    }

    private void platformStarted() {
        synchronized (lock) {
            platformStarted = true;
        }
        startupModules();
    }

    private void startupModules() {
        synchronized (lock) {
            if (httpServiceRegistered && startupEventReceived && platformStarted) {
                startBundles(BundleType.MOTECH_MODULE);
            }
        }
    }

    private void categorizeBundles() {
        for (Bundle bundle : bundleContext.getBundles()) {
            BundleType type = BundleType.forBundle(bundle);

            if (!bundlesByType.containsKey(type)) {
                bundlesByType.put(type, new ArrayList<Bundle>());
            }

            bundlesByType.get(type).add(bundle);
        }
    }

    private void verifyBundleState(int targetBundleValue, String bundleSymbolicName) {
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundleSymbolicName.equals(bundle.getSymbolicName())) {
                if (bundle.getState() == targetBundleValue) {
                    return;
                }
            }
        }
        throw new OsgiException("Bundle: " + bundleSymbolicName + " did not start properly");
    }
}
