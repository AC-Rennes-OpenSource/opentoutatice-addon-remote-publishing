/**
 * 
 */
package fr.toutatice.ecm.acrennes.builder;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;

import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.query.helper.ToutaticeEsQueryHelper;



/**
 * @author david
 *
 */
public class PublishableDocumentFinder {

    /** Query to get publishable document. */
    // FIXME: replace ecm:name by ecm:proxyVersionableId so demormalized it
    private static final String PUBLISHABLE_DOC_QUERY = "select * from Document where ttc:webid = '%s' and ecm:isVersion = 1 and ecm:currentLifeCycleState = 'approved' and ecm:mixinType <> 'HiddenInNavigation' order by dc:modified desc";

    /** Publishable document. */
    private DocumentModel publishableDoc;

    /** Singleton. */
    private static PublishableDocumentFinder instance;

    /**
     * Constructor.
     */
    private PublishableDocumentFinder() {
        super();
    }

    public static synchronized PublishableDocumentFinder getInstance() {
        if (instance == null) {
            instance = new PublishableDocumentFinder();
        }
        return instance;
    }

    /**
     * Gets live if in approved state or last version in approved state.
     * 
     * @param session
     * @param document live
     * @return document if in approved state or last version in approved state
     */
    public DocumentModel getPublishableDocument(CoreSession session, DocumentModel document) {
        DocumentModel publishableDoc = null;

        if (ToutaticeNuxeoStudioConst.CST_DOC_STATE_APPROVED.equals(document.getCurrentLifeCycleState())) {
            publishableDoc = document;
        } else {
            String query = String.format(PUBLISHABLE_DOC_QUERY, (String) document.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_TOUTATICE_WEBID));
            DocumentModelList publishableDocs = ToutaticeEsQueryHelper.query(session, query, 1);

            if (CollectionUtils.isNotEmpty(publishableDocs)) {
                publishableDoc = publishableDocs.get(0);
            }
        }

        return publishableDoc;
    }

    /**
     * @return the publishableDoc
     */
    public DocumentModel getPublishableDoc() {
        return publishableDoc;
    }


    /**
     * @param publishableDoc the publishableDoc to set
     */
    public void setPublishableDoc(DocumentModel publishableDoc) {
        this.publishableDoc = publishableDoc;
    }

}
