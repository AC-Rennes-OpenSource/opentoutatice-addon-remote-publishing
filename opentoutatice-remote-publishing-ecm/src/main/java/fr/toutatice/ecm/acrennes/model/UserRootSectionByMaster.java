/**
 * 
 */
package fr.toutatice.ecm.acrennes.model;

import java.util.AbstractMap.SimpleEntry;

import org.nuxeo.ecm.core.api.DocumentModel;


/**
 * @author david
 *
 */
public class UserRootSectionByMaster extends SimpleEntry<DocumentModel, UserRootSection> {

    private static final long serialVersionUID = 8990450838153452801L;

    /**
     * Constructor.
     * 
     * @param key
     * @param value
     */
    public UserRootSectionByMaster(DocumentModel key, UserRootSection value) {
        super(key, value);
    }

    public DocumentModel getSpace() {
        return this.getKey();
    }

    public UserRootSection getUserRootSection() {
        return this.getValue();
    }

}
