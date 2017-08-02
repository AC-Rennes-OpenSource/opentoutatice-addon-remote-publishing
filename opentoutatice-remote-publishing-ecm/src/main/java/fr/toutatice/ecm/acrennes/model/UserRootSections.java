/**
 * 
 */
package fr.toutatice.ecm.acrennes.model;

import java.util.ArrayList;


/**
 * @author david
 *
 */
public class UserRootSections extends ArrayList<UserRootSectionByMaster> {

    private static final long serialVersionUID = 2190584204756402088L;

    private PublishableDocument publishableDoc;

    /**
     * @return the publishableDoc
     */
    public PublishableDocument getPublishableDoc() {
        return publishableDoc;
    }


    /**
     * @param publishableDoc the publishableDoc to set
     */
    public void setPublishableDoc(PublishableDocument publishableDoc) {
        this.publishableDoc = publishableDoc;
        if (publishableDoc != null && publishableDoc.getModel() != null) {
            this.publishableDoc.setVersionLabel(publishableDoc.getModel().getVersionLabel());
        }
    }


}
