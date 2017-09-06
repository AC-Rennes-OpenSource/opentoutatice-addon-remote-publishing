/**
 * 
 */
package fr.toutatice.ecm.acrennes.publishing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;
import org.nuxeo.ecm.platform.publisher.rules.DefaultValidatorsRule;

import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeCommentsHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.web.publication.ToutaticeCoreProxyWithWorkflowFactory;


/**
 * @author david
 *
 */
public class AcrennesPublishedDocumentWithWorkflowFactory extends ToutaticeCoreProxyWithWorkflowFactory {

    /**
     * Singleton.
     */
    private static AcrennesPublishedDocumentWithWorkflowFactory instance;

    /**
     * Constructor.
     */
    public AcrennesPublishedDocumentWithWorkflowFactory() {
        super();
        if (this.validatorsRule == null) {
            this.validatorsRule = new DefaultValidatorsRule();
        }
    }

    public static synchronized AcrennesPublishedDocumentWithWorkflowFactory getInstance() {
        if (instance == null) {
            instance = new AcrennesPublishedDocumentWithWorkflowFactory();
        }
        return instance;
    }

    /**
     * 
     * @param session
     * @param docToPublish
     * @param section
     * @return
     * @throws ClientException
     */
    public PublishedDocument publish(CoreSession session, DocumentModel docToPublish, DocumentModel section) throws ClientException {
        // Result
        DocumentModel publishedDocument = null;
        PublishedDocument publishedDoc = null;

        if (docToPublish.isProxy()) {
            // Store comments
            Map<DocumentModel, List<DocumentModel>> comments = new HashMap<DocumentModel, List<DocumentModel>>();
            comments.putAll(ToutaticeCommentsHelper.getProxyComments(docToPublish));

            // Publish
            publishedDoc = publishDoc(session, docToPublish, section);
            publishedDocument = ((SimpleCorePublishedDocument) publishedDoc).getProxy();

            // Mark as remote proxy
            if (!publishedDocument.hasFacet(ToutaticeNuxeoStudioConst.CST_FACET_REMOTE_PROXY)) {
                publishedDocument.addFacet(ToutaticeNuxeoStudioConst.CST_FACET_REMOTE_PROXY);
            }

            // Restore comments on published document
            ToutaticeCommentsHelper.setComments(session, publishedDocument, comments);

            // User has not necessary Write permission on publishedDocument
            ToutaticeDocumentHelper.saveDocumentSilently(session, publishedDocument, true);

        } else {
            // Publish
            publishedDoc = publishDoc(session, docToPublish, section);
            publishedDocument = ((SimpleCorePublishedDocument) publishedDoc).getProxy();

            // Mark as remote proxy
            if (!publishedDocument.hasFacet(ToutaticeNuxeoStudioConst.CST_FACET_REMOTE_PROXY)) {
                publishedDocument.addFacet(ToutaticeNuxeoStudioConst.CST_FACET_REMOTE_PROXY);

                // User has not necessary Write permission on proxy
                ToutaticeDocumentHelper.saveDocumentSilently(session, publishedDocument, true);
            }

        }

        return publishedDoc;
    }

    /**
     * 
     * @param session
     * @param docToPublish
     * @param section
     * @return
     */
    public PublishedDocument publishDoc(CoreSession session, DocumentModel docToPublish, DocumentModel section) {

        super.coreSession = session;

        NuxeoPrincipal principal = (NuxeoPrincipal) session.getPrincipal();

        DocumentPublisherUnrestricted runner = new DocumentPublisherUnrestricted(session, docToPublish.getRef(), section.getRef(), principal, null);
        runner.runUnrestricted();
        
        PublishedDocument publishedDocument = runner.getPublishedDocument();
        
        session.save();

        return publishedDocument;
    }

    /**
     * 
     * @param session
     * @param publishedDoc
     */
    public void unPublish(CoreSession session, PublishedDocument publishedDoc) {
        // Just remove proxy
        session.removeDocument(((SimpleCorePublishedDocument) publishedDoc).getProxy().getRef());
        session.save();
    }

}
