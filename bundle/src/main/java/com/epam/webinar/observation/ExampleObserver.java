package com.epam.webinar.observation;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.felix.scr.annotations.*;
import org.apache.jackrabbit.api.observation.JackrabbitEvent;
import org.apache.sling.jcr.api.SlingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = false, immediate = true, label = "Example Event handler")
@Service(value = { EventListener.class })
public class ExampleObserver implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(ExampleObserver.class);

    /*
     * A combination of one or more event type constants encoded as a bitmask
     * 
     * Available JCR Events:
     * 
     * Event.NODE_ADDED Event.NODE_MOVED Event.NODE_REMOVED Event.PERSIST Event.PROPERTY_ADDED
     * Event.PROPERTY_REMOVED Event.PROPERTY_CHANGED
     */
    private final int events = Event.PROPERTY_ADDED | Event.NODE_ADDED;

    // Only events whose associated node is at absPath (or within its subtree, if isDeep is true)
    // will be received. It is permissible to register a listener for a path where no node currently
    // exists.
    private final String absPath = "/content";
    private final boolean isDeep = true;

    // Additionally, if noLocal is true, then events generated by the session through which the
    // listener was registered are ignored. Otherwise, they are not ignored.
    private final boolean noLocal = false;
    private final String[] uuids = null;

    // Only events whose associated node has one of the node types (or a subtype of one of the node
    // types) in this list will be received. If his parameter is null then no node type-related
    // restriction is placed on events received.
    private final String[] nodeTypes = null;

    @Reference
    private SlingRepository repository;

    private Session adminSession;

    private ObservationManager observationManager;

    @Activate
    public void activate() throws RepositoryException {
        adminSession = repository.loginAdministrative(null);

        // Get JCR ObservationManager from Workspace
        observationManager = adminSession.getWorkspace().getObservationManager();

        // Register the JCR Listener

        /** This is the KEY element where this listener is registered **/
        LOG.info("Register event listener.");
        observationManager.addEventListener(this, events, absPath, isDeep, uuids, nodeTypes, noLocal);
    }

    @Deactivate
    public void deactivate() throws RepositoryException {

        try {
            // Un-register event handler
            LOG.info("Remove event listener.");
            observationManager.removeEventListener(this);
        } finally {
            if (adminSession != null) {
                adminSession.logout();
            }
        }
    }

    @Override
    public void onEvent(EventIterator events) {
        try {
            LOG.info("Event fire...");
            LOG.info("has next {}", events.hasNext());
            while (events.hasNext()) {
                Event event = events.nextEvent();
                // IMPORTANT!
                //
                // JCR Events are NOT cluster-aware and this event listener will be invoked on every
                // node in the cluster.

                // Check if this event was spawned from the server this event handler is running on
                // or from another
                LOG.info("has next {}", event instanceof JackrabbitEvent && ((JackrabbitEvent) event).isExternal());
                if (event instanceof JackrabbitEvent && ((JackrabbitEvent) event).isExternal()) {
                    // Event did NOT originate from this server

                    // Skip, Let only the originator process;

                    // This is usual to avoid having the same processing happening for every node in
                    // a cluster. This
                    // is almost always the case when the EventListener modifies the JCR.

                    // A possible use-case for handling the event on EVERY member of a cluster would
                    // be clearing out an
                    // in memory (Service-level) cache.

                    return;
                } else {
                    // Event originated from THIS server
                    // Continue processing this Event
                    LOG.info("node type {}", event.getType());

                    if (Event.NODE_ADDED == event.getType()) {
                        LOG.info("Node has been added : {}", event.getPath());

                    } else if (Event.PROPERTY_ADDED == event.getType()) {
                        LOG.info("Property has been added : {}", event.getPath());
                    }
                }

            }
        } catch (RepositoryException e) {
            LOG.error("Error while treating events", e);
        }
    }
}