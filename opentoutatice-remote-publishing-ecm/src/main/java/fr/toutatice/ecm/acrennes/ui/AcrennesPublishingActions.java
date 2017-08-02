/**
 * 
 */
package fr.toutatice.ecm.acrennes.ui;

import static org.jboss.seam.ScopeType.CONVERSATION;

import java.io.Serializable;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.Create;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.core.Events;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.api.PublishingEvent;
import org.nuxeo.ecm.platform.publisher.web.PublishActionsBean;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.platform.ui.web.util.SeamComponentCallHelper;
import org.nuxeo.ecm.webapp.helpers.EventNames;

import fr.toutatice.ecm.acrennes.publishing.AcrennesPublishedDocumentWithWorkflowFactory;
import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;


/**
 * @author david
 *
 */
@Name("acrennesPublishingActions")
@Scope(CONVERSATION)
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class AcrennesPublishingActions implements Serializable {

    private static final long serialVersionUID = -4158103274671992562L;

    private static final Log log = LogFactory.getLog(AcrennesPublishingActions.class);

    @In(create = true)
    protected transient CoreSession documentManager;

    @In(create = true, required = false)
    protected transient FacesMessages facesMessages;

    @In(create = true)
    protected Map<String, String> messages;

    protected transient AcrennesPublishedDocumentWithWorkflowFactory factory;

    @Create
    public void init() {
        if (this.factory == null) {
            this.factory = AcrennesPublishedDocumentWithWorkflowFactory.getInstance();
        }
    }

    /**
     * 
     * @param publishableDoc
     * @param section
     * @return
     */
    public String publish(DocumentModel publishableDoc, DocumentModel section) {
        PublishedDocument publishedDocument = this.factory.publish(this.documentManager, publishableDoc, section);

        // To recompute model
        resetModel();

        notify(publishedDocument, section, publishableDoc);
        return null;
    }

    /**
     * 
     * @param publishedDoc
     * @return
     * @throws InterruptedException
     */
    public String unPublish(PublishedDocument publishedDoc) throws InterruptedException {
        this.factory.unPublish(this.documentManager, publishedDoc);

        // To recompute model
        resetModel();

        Events.instance().raiseEvent(EventNames.DOCUMENT_CHILDREN_CHANGED);
        return null;
    }

    /**
     * 
     */
    protected void resetModel() {
        AcrennesUserRootSectionsManager sectionsManager = (AcrennesUserRootSectionsManager) SeamComponentCallHelper.getSeamComponentByName("sectionsManager");
        sectionsManager.setSections(null);
    }

    /**
     * 
     * @param publishedDocument
     * @param section
     * @param publishableDoc
     */
    public void notify(PublishedDocument publishedDocument, DocumentModel section, DocumentModel publishableDoc) {
        // For logs
        final long b = System.currentTimeMillis();

        FacesContext context = FacesContext.getCurrentInstance();
        // Log event
        if (publishedDocument.isPending()) {
            String comment = ComponentUtils.translate(context, "publishing.waiting", section.getPath());

            Events.instance().raiseEvent(EventNames.DOCUMENT_SUBMITED_FOR_PUBLICATION);
            PublishActionsBean.notifyEvent(this.documentManager, PublishingEvent.documentWaitingPublication.name(), null, comment, null, publishableDoc);

            facesMessages.addFromResourceBundle(StatusMessage.Severity.INFO, messages.get("document_submitted_for_publication"),
                    messages.get(publishableDoc.getType()));

        } else {
            String comment = ComponentUtils.translate(context, "publishing.done", section.getPath());

            Events.instance().raiseEvent(EventNames.DOCUMENT_PUBLISHED);
            PublishActionsBean.notifyEvent(this.documentManager, PublishingEvent.documentPublished.name(), null, comment, null, publishableDoc);

            facesMessages.addFromResourceBundle(StatusMessage.Severity.INFO, messages.get("document_published"), messages.get(publishableDoc.getType()));

        }

        if (log.isDebugEnabled()) {
            final long e = System.currentTimeMillis();
            log.debug("#notify : " + String.valueOf(e - b) + " ms");
        }
    }

}
