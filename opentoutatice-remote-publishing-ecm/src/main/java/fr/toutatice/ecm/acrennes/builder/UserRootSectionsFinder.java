package fr.toutatice.ecm.acrennes.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.Filter;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.publisher.impl.finder.AbstractRootSectionsFinder;

import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.query.helper.NxqlHelper;
import fr.toutatice.ecm.platform.core.query.helper.ToutaticeEsQueryHelper;


public class UserRootSectionsFinder extends UnrestrictedSessionRunner {

    /** Log. */
    private static final Log log = LogFactory.getLog(UserRootSectionsFinder.class);

    /** Query to get Root sections (configured on a publishing Folder). */
    protected static final String CONFIGURED_ROOT_SECTIONS_QUERY = "select * from Document where ecm:uuid %s and ecm:isVersion = 0 and ecm:currentLifeCycleState <> 'deleted'"
            + " and ecm:mixinType = 'Folderish' and ecm:mixinType <> 'HiddenInNavigation'";

    /** Query to get children of document. */
    protected static final String CHILDREN_QUERY = "select * from Document where ecm:uuid %s and ecm:isVersion = 0 and ecm:currentLifeCycleState <> 'deleted'"
            + " and ecm:mixinType = 'Folderish' and ecm:mixinType <> 'HiddenInNavigation'";

    /** Current document. */
    private DocumentModel currentDoc;

    /** Master Root cache. */
    private static Map<DocumentModel, DocumentModel> masterRootsCache = new ConcurrentHashMap<>();

    /** User's root sections as documents. */
    private static Map<String, DocumentModelList> sectionsByDocCache = new ConcurrentHashMap<>();

    /** User's root sections ids. */
    private List<String> sectionsIds;

    /** Space sections ids. */
    private Set<String> spaceIds;

    /** MasterPublishSpace filter. */
    private static final org.nuxeo.ecm.core.api.Filter filter = new org.nuxeo.ecm.core.api.Filter() {

        private static final long serialVersionUID = 1284642449788813163L;

        @Override
        public boolean accept(DocumentModel docModel) {
            return docModel.hasFacet(FacetNames.MASTER_PUBLISH_SPACE);
        }

    };

    /**
     * Constructor.
     * 
     * @param session
     */
    protected UserRootSectionsFinder(CoreSession session) {
        super(session);
        this.spaceIds = new HashSet<>();
    }

    /**
     * 
     * @param userSession
     * @param document
     * @return
     */
    public DocumentModelList getConfiguredUserRootSections(CoreSession userSession, DocumentModel document) {
        // Logs
        final long b = System.currentTimeMillis();

        // Check cache
        DocumentModelList sections = sectionsByDocCache.get(document.getId());

        if (CollectionUtils.isEmpty(sections)) {
            // Get configured Root sections ids on one of document's publishing parent
            List<String> rootSectionsIds = getUserRootSectionsIds(userSession, document);

            if (CollectionUtils.isNotEmpty(rootSectionsIds)) {

                if (log.isDebugEnabled()) {
                    final long e = System.currentTimeMillis();
                    log.debug("#getConfiguredUserRootSections: after rootSectionsIds: " + String.valueOf(e - b) + " ms");
                }

                // Get documents (implicitly filtered on Read permission)
                String query = String.format(CONFIGURED_ROOT_SECTIONS_QUERY, NxqlHelper.buildInClause(rootSectionsIds));
                sections = ToutaticeEsQueryHelper.unretrictedQuery(userSession, query, rootSectionsIds.size());

                // Set in cache
                sectionsByDocCache.put(document.getId(), sections);

                if (log.isDebugEnabled()) {
                    final long e = System.currentTimeMillis();
                    log.debug("#getConfiguredUserRootSections: end: " + String.valueOf(e - b) + " ms");
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                final long e = System.currentTimeMillis();
                log.debug("#getConfiguredUserRootSections: end: get in CACHE: " + String.valueOf(e - b) + " ms");
            }
        }

        return sections;
    }

    /**
     * Gets Root sections configured on one of document's parent having publishing schema.
     * 
     * @return
     */
    public List<String> getUserRootSectionsIds(CoreSession userSession, DocumentModel document) {
        this.currentDoc = document;
        // Call run() method below
        this.runUnrestricted();

        return this.sectionsIds;
    }

    /**
     * Gets Root sections configured on one of document's parent having publishing schema in unrestricted way.
     */
    @Override
    public void run() throws ClientException {
        // Get list of ids of configured Root sections
        DocumentModel publishingFolder = getPublishingFolder(this.session, this.currentDoc);

        if (publishingFolder != null) {
            this.sectionsIds = Arrays.asList((String[]) publishingFolder.getPropertyValue(AbstractRootSectionsFinder.SECTIONS_PROPERTY_NAME));
        }
    }

    /**
     * @param userSession
     * @param document
     */
    // FIXME: to optimize with Es query but which one??
    protected DocumentModel getPublishingFolder(CoreSession userSession, DocumentModel document) {
        // Log
        final long b = System.currentTimeMillis();

        // Result
        DocumentModel publishingFolder = null;

        Filter hasSectionsFilter = new Filter() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean accept(DocumentModel document) {
                return document.hasSchema(AbstractRootSectionsFinder.SCHEMA_PUBLISHING)
                        && ArrayUtils.isNotEmpty((String[]) document.getPropertyValue(AbstractRootSectionsFinder.SECTIONS_PROPERTY_NAME));
            }
        };

        DocumentModelList publishingFolders = ToutaticeDocumentHelper.getParentList(userSession, document, hasSectionsFilter, true, true, true);

        if (publishingFolders.size() > 0) {
            publishingFolder = publishingFolders.get(0);
        }

        if (log.isDebugEnabled()) {
            final long e = System.currentTimeMillis();
            log.debug("#getPublishingFolder: " + String.valueOf(e - b) + " ms");
        }

        return publishingFolder;
    }

    public boolean storePublishingSpaceId(String spaceId) {
        return this.spaceIds.add(spaceId);
    }

    /**
     * 
     * @param session
     * @param section
     * @param document
     * @return
     */
    public DocumentModel getMatserRoot(CoreSession session, DocumentModel section, DocumentModel document) {
        // Log
        final long b = System.currentTimeMillis();

        // Check cache
        DocumentModel masterRoot = masterRootsCache.get(section);

        if (masterRoot == null) {

            DocumentModelList masterPublishRoots = ToutaticeDocumentHelper.getParentList(session, section, filter, true, true);
            if (masterPublishRoots.size() > 0) {
                masterRoot = masterPublishRoots.get(0);
            } else {
                masterRoot = ToutaticeDocumentHelper.getDomain(session, section, true);
            }

            // Set in cache
            masterRootsCache.put(section, masterRoot);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("MasterRoot | get in CACHE: " + masterRoot.getTitle());
            }
        }

        if (log.isDebugEnabled()) {
            final long e = System.currentTimeMillis();
            log.debug("#getMatserRoot: " + String.valueOf(e - b) + " ms");
        }

        return masterRoot;
    }

    /**
     * Invalidate all cache of sections by document.
     */
    public static void invalidateAllSectionsCache() {
        sectionsByDocCache = new ConcurrentHashMap<>();
    }

    /**
     * Invalidate cache entries for all children of given configured folder.
     */
    public static void invalidateSectionsCache(DocumentModel ws) {
        // Get all children
        DocumentModelList descendants = ToutaticeEsQueryHelper.getDescendants(ws.getCoreSession(), ws, true, false);

        // Invalidate
        for (DocumentModel descendant : descendants) {
            sectionsByDocCache.remove(descendant.getId());
        }

    }

}
