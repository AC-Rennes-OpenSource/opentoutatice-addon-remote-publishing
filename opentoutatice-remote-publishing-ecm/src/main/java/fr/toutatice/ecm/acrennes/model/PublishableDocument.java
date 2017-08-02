/**
 * 
 */
package fr.toutatice.ecm.acrennes.model;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;


/**
 * @author david
 *
 */
public class PublishableDocument {

    /** Model of publishable document. */
    private DocumentModel model;

    /** Label of version of publishable document. */
    private String versionLabel;

    /** Errors on publishable document by section. */
    private Map<DocumentModel, Error> errors;

    /**
     * Error types.
     */
    public static enum Error {
        notExists("toutatice.label.publish.not.available"), noPerm("toutatice.label.publish.no.permission");

        private String messageKey;

        private Error(String messageKey) {
            this.messageKey = messageKey;
        }


        /**
         * @return the messageKey
         */
        public String getMessageKey() {
            return messageKey;
        }


        /**
         * @param messageKey the messageKey to set
         */
        public void setMessageKey(String messageKey) {
            this.messageKey = messageKey;
        }

    }

    public PublishableDocument(DocumentModel model) {
        super();
        this.model = model;
        this.errors = new HashMap<>();
    }


    /**
     * @return the model
     */
    public DocumentModel getModel() {
        return model;
    }



    /**
     * @param model the model to set
     */
    public void setModel(DocumentModel model) {
        this.model = model;
    }


    /**
     * @return the errors
     */
    public Map<DocumentModel, Error> getErrors() {
        return errors;
    }


    /**
     * @param errors the errors to set
     */
    public void setErrors(Map<DocumentModel, Error> errors) {
        this.errors = errors;
    }


    /**
     * @return the versionLabel
     */
    public String getVersionLabel() {
        return this.versionLabel;
    }


    /**
     * @param versionLabel the versionLabel to set
     */
    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }


    /**
     * @return the hasError
     */
    public boolean hasError(DocumentModel section) {
        return this.errors.get(section) != null;
    }



    /**
     * @return the error
     */
    public Error getError(DocumentModel section) {
        return this.errors.get(section);
    }


    /**
     * @param error the error to set
     */
    public void setError(DocumentModel section, Error error) {
        this.errors.put(section, error);
    }


}
