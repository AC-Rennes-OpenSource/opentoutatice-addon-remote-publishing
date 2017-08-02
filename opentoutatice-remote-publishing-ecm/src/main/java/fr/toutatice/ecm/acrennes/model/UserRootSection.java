/**
 * 
 */
package fr.toutatice.ecm.acrennes.model;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;


/**
 * @author david
 *
 */
public class UserRootSection {
    
    /** Section. */
    private DocumentModel section;

    /** Published document in section. */
    private PublishedDocument publishedDoc;

    /** Publication permissions. */
    private boolean canPublishTo = false;

    /** Unpublication permissions. */
    private boolean canUnPublishFrom = false;

    /**
     * Constructor.
     */
    public UserRootSection() {
        super();
    }
    
    /**
     * Constructor.
     */
    public UserRootSection(DocumentModel section) {
        super();
        this.section = section;
    }

    /**
     * @return the section
     */
    public DocumentModel getSection() {
        return section;
    }


    /**
     * @param section the section to set
     */
    public void setSection(DocumentModel section) {
        this.section = section;
    }


    /**
     * @return the publishedDoc
     */
    public PublishedDocument getPublishedDoc() {
        return publishedDoc;
    }


    /**
     * @param publishedDoc the publishedDoc to set
     */
    public void setPublishedDoc(PublishedDocument publishedDoc) {
        this.publishedDoc = publishedDoc;
    }


    /**
     * @return the canPublish
     */
    public boolean canPublishTo() {
        return canPublishTo;
    }


    /**
     * @param canPublish the canPublish to set
     */
    public void setCanPublishTo(boolean canPublish) {
        this.canPublishTo = canPublish;
    }


    /**
     * @return the canUnPublish
     */
    public boolean canUnPublishFrom() {
        return canUnPublishFrom;
    }


    /**
     * @param canUnPublish the canUnPublish to set
     */
    public void setCanUnPublishFrom(boolean canUnPublish) {
        this.canUnPublishFrom = canUnPublish;
    }

}
