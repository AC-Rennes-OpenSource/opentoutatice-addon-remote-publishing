/*
 * (C) Copyright 2014 Académie de Rennes (http://www.ac-rennes.fr/), OSIVIA (http://www.osivia.com) and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * 
 * Contributors:
 * mberhaut1
 */
package fr.toutatice.ecm.platform.document;

import static org.jboss.seam.ScopeType.CONVERSATION;
import static org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager.CURRENT_DOCUMENT_SELECTION;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.core.Events;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.opentoutatice.ecm.tinymce.attached.blobs.bean.OttcDocumentActionsBean;

import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;
import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeNotifyEventHelper;
import fr.toutatice.ecm.platform.web.context.ToutaticeNavigationContext;
import fr.toutatice.ecm.platform.web.publication.validation.ToutaticeRemotePublishActionsBean;

/**
 * @author oadam
 * 
 */
@Name("documentActions")
@Scope(CONVERSATION)
@Install(precedence = ExtendedSeamPrecedence.INHERIT_ADD_ON)
public class ToutaticeRPDocumentActionsBean extends OttcDocumentActionsBean {

	private static final long serialVersionUID = 8652740421940377204L;
	
	private static final Log log = LogFactory.getLog(ToutaticeRPDocumentActionsBean.class);

    @In(create = true)
    protected transient ToutaticeRemotePublishActionsBean publishActions;
    
    /**
     * Determine si l'action "seeLatestValidDocumentVersion" doit être présentée.
     * 
     * <h4>Conditions</h4> <li>le document doit posséder une version valide</li> <li>le document ne doit pas être la version valide</li>
     * 
     * @return true si l'action doit être présentée. false sinon.
     */
    public boolean isSeeLatestValidDocumentVersionActionAuthorized() {
        boolean status = false;
        DocumentModel currentDoc = null;

        // Récupérer la dernière version validée
        try {
            currentDoc = navigationContext.getCurrentDocument();
            DocumentModel validDocument = ToutaticeDocumentHelper.getLatestDocumentVersion(currentDoc, documentManager);
            if (null != validDocument) {
                status = !currentDoc.getVersionLabel().equals(validDocument.getVersionLabel());
            }
        } catch (Exception e) {
            String docName = (null != currentDoc) ? currentDoc.getName() : "unknown";
            log.debug("Failed to get the latest valid version of document '" + docName + "', error: " + e.getMessage());
        }

        return status;
    }

    public String viewArchivedVersion() throws NuxeoException {
        return viewArchivedVersion(navigationContext.getCurrentDocument());
    }

    public String viewArchivedVersion(DocumentModel document) throws NuxeoException {
        String output = "";

        try {
            DocumentModel archivedDocument = ToutaticeDocumentHelper.getLatestDocumentVersion(document, documentManager);
            if (null != archivedDocument) {
                output = navigationContext.navigateToDocument(document, ToutaticeDocumentHelper.getVersionModel(archivedDocument));
            }
        } catch (DocumentException e) {
            throw new NuxeoException(e);
        }

        return output;
    }

    @Override
    public boolean isRemoteProxy() {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        return currentDocument.isProxy() && (!StringUtils.endsWith(currentDocument.getName(), ToutaticeGlobalConst.CST_PROXY_NAME_SUFFIX) || StringUtils.endsWith(currentDocument.getName(), ToutaticeRPConstants.CST_PROXY_REMOTE_LEGACY_NAME_SUFFIX));
    }
    
    public void unPublishDocumentSelection() throws NuxeoException {
    	List<DocumentModel> currentDocumentSelection = documentsListsManager.getWorkingList(CURRENT_DOCUMENT_SELECTION);
        
        if (null != currentDocumentSelection && !currentDocumentSelection.isEmpty()) {
        	DocumentModel currentFolder = navigationContext.getCurrentDocument();
        	try {
        		for (DocumentModel document : currentDocumentSelection) {
        			DocumentModelList proxies = documentManager.getProxies(document.getRef(), currentFolder.getRef());
        			publishActions.unPublish(proxies.get(0));
        		}
        	} catch (Exception e) {
        		log.error("Failed to set offline the selection from the document: '" + currentFolder.getTitle() + "', error: " + e.getMessage());
        	}
        	
        	// Add audit log to the current folder history
        	ToutaticeNotifyEventHelper.notifyAuditEvent(documentManager, 
        			ToutaticeRPConstants.CST_RP_AUDDIT_EVENT_UNPUBLISH_SELECTION_PROCESS_COMPLETED, 
        			currentFolder,
        			null);
        	
        	// Rafraîchir la liste de sélection
        	documentsListsManager.resetWorkingList(CURRENT_DOCUMENT_SELECTION);
        	
        	// Rafraîchir le content view
        	Events.instance().raiseEvent(EventNames.DOCUMENT_CHILDREN_CHANGED, currentFolder);
        }
    }
    
    /**
     * @return le nom de l'espace de publication dans le portail pour la section
     */
    public String getPublicationAreaNameOfSection(DocumentModel section) {
        String areaName = getPublicationAreaName(section);

        if (CST_DEFAULT_PUBLICATON_AREA_TITLE.equals(areaName)) {
            /*
             * La section n'appartient pas à un espace de publication mais à un élément
             * de type SectionRoot. Prendre son nom.
             */
            DocumentModel sectionRoot = ((ToutaticeNavigationContext) navigationContext).getSectionPublicationArea(section);
            if (sectionRoot != null) {
                try {
                    areaName = sectionRoot.getTitle();
                } catch (NuxeoException e) {
                    log.error("Failed to get the domain title, error: " + e.getMessage());
                    areaName = CST_DEFAULT_PUBLICATON_AREA_TITLE;
                }
            } else {
                /*
                 * La section n'appartient pas à un espace de publication ni à une SectionRoot. Prendre
                 * le nom du domaine à la place
                 */
                DocumentModel domain = ((ToutaticeNavigationContext) navigationContext).getDocumentDomain(section);
                if (domain != null) {
                    try {
                        areaName = domain.getTitle();
                    } catch (NuxeoException e) {
                        log.error("Failed to get the domain title, error: " + e.getMessage());
                        areaName = CST_DEFAULT_PUBLICATON_AREA_TITLE;
                    }
                }
            }
        }

        return areaName;
    }

}