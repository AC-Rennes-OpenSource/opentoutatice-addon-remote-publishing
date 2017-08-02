/**
 * 
 */
package fr.toutatice.ecm.acrennes.builder;

import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.impl.core.SectionPublicationTree;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;

import fr.toutatice.ecm.acrennes.model.PublishableDocument;
import fr.toutatice.ecm.acrennes.model.UserRootSection;
import fr.toutatice.ecm.acrennes.model.UserRootSectionByMaster;
import fr.toutatice.ecm.acrennes.model.UserRootSections;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.publish.PublishedDocumentsFinder;


/**
 * @author david
 *
 */
public class UserRootSectionsBuilder {

    /** Log. */
    private static final Log log = LogFactory.getLog(UserRootSectionsBuilder.class);

    /** Root sections finder. */
    private UserRootSectionsFinder sectionsFinder;

    /** Published documents finder. */
    private PublishedDocumentsFinder publishedDocsFinder;

    /** Publishable document finder. */
    private PublishableDocumentFinder publishableDocFinder;

    /** user's session. */
    private CoreSession userSession;

    /** Roos types. */
    public static final String[] MASTER_ROOT_TYPES = {"Root", "SectionRoot"};

    /**
     * Constructor.
     * 
     * @param session
     */
    public UserRootSectionsBuilder(CoreSession session) {
        this.userSession = session;

        this.sectionsFinder = new UserRootSectionsFinder(session);
        this.publishedDocsFinder = PublishedDocumentsFinder.getInstance();
        this.publishableDocFinder = PublishableDocumentFinder.getInstance();
    }

    /**
     * Builds model to for toutatice_document_publish.xhtml view.
     * 
     * @param document
     * @return model
     */
    public UserRootSections buildSectionsModel(DocumentModel document) {
        // Log
        final long b = System.currentTimeMillis();

        UserRootSections userSections = new UserRootSections();

        // Get root sections
        DocumentModelList sections = this.sectionsFinder.getConfiguredUserRootSections(this.userSession, document);

        if (CollectionUtils.isNotEmpty(sections)) {
            // Get list of published documents and their section
            Map<DocumentRef, PublishedDocument> publishedDocsIn = this.publishedDocsFinder.find(this.userSession, document);

            // Get publishable document (only one)
            DocumentModel publishableDocModel = this.publishableDocFinder.getPublishableDocument(this.userSession, document);
            PublishableDocument publishableDoc = new PublishableDocument(publishableDocModel);

            // Building
            for (DocumentModel section : sections) {
                UserRootSection userSection = new UserRootSection(section);

                // Set published document
                PublishedDocument publishedDocument = publishedDocsIn.get(section.getRef());
                userSection.setPublishedDoc(publishedDocument);

                // Set can publish status
                boolean canPublish = setCanPublishTo(publishedDocument, publishableDoc, section, document);
                userSection.setCanPublishTo(canPublish);

                // Set of publishable document here cause it can have changed in #setCanPublishTo
                userSections.setPublishableDoc(publishableDoc);

                // Set can unpubuplish status
                boolean canUnPublish = setCanUnpublishFrom(publishedDocument, section);
                userSection.setCanUnPublishFrom(canUnPublish);

                // Build UserRootSectionByMaster
                DocumentModel matserRoot = this.sectionsFinder.getMatserRoot(this.userSession, section, document);
                UserRootSectionByMaster userSectionMaster = new UserRootSectionByMaster(matserRoot, userSection);

                // Fill userSections
                userSections.add(userSectionMaster);

            }

        }

        if (log.isDebugEnabled()) {
            final long e = System.currentTimeMillis();
            log.debug("#buildModel: " + String.valueOf(e - b) + " ms");
        }

        return userSections;
    }

    /**
     * 
     * @param publishedDocument
     * @param publishableDoc
     * @param section
     * @param document
     * @return
     */
    private boolean setCanPublishTo(PublishedDocument publishedDocument, PublishableDocument publishableDoc, DocumentModel section, DocumentModel document) {
        // Publishable document must exists
        boolean can = publishableDoc.getModel() != null;

        if (can) {
            // Section must not be a Root or SectionRoot
            can = !ArrayUtils.contains(MASTER_ROOT_TYPES, section.getType());

            // Current document must not be locked
            can &= this.userSession.getLockInfo(document.getRef()) == null;

            // User must have permission on section
            boolean userHasPerm = this.userSession.hasPermission(section.getRef(), SectionPublicationTree.CAN_ASK_FOR_PUBLISHING);
            if (!userHasPerm) {
                publishableDoc.setError(section, PublishableDocument.Error.noPerm);
            }

            can &= userHasPerm;

            if (can) {
                // No document published
                can = publishedDocument == null;

                // Document yet published
                if (!can) {
                    // Publishable document must be older than published document (target version)
                    DocumentModel yetPublishedDoc = this.userSession.getDocument(publishedDocument.getSourceDocumentRef());
                    can = ToutaticeDocumentHelper.DocumentVersionComparator.isNewer(yetPublishedDoc, publishableDoc.getModel());
                }

            }
        } else {
            publishableDoc.setError(section, PublishableDocument.Error.notExists);
        }

        return can;
    }

    /**
     * 
     * @param publishedDocument
     * @param section
     * @return
     */
    private boolean setCanUnpublishFrom(PublishedDocument publishedDocument, DocumentModel section) {
        // Published document exists
        boolean can = publishedDocument != null && publishedDocument instanceof SimpleCorePublishedDocument;
        // User must have Write permission on section
        return can && this.userSession.hasPermission(section.getRef(), SecurityConstants.WRITE);
    }

}
