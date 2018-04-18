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
package fr.toutatice.ecm.platform.web.publication.validation;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.faces.FacesMessages;
import org.jboss.seam.international.StatusMessage;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.Filter;
import org.nuxeo.ecm.core.api.Sorter;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.ui.web.util.SeamComponentCallHelper;
import org.nuxeo.ecm.webapp.contentbrowser.DocumentActions;
import org.nuxeo.ecm.webapp.helpers.ResourcesAccessor;
import org.nuxeo.ecm.webapp.tree.DocumentTreeNode;
import org.nuxeo.ecm.webapp.tree.DocumentTreeNodeImpl;

import fr.toutatice.ecm.acrennes.builder.UserRootSectionsFinder;
import fr.toutatice.ecm.acrennes.ui.AcrennesUserRootSectionsManager;
import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;
import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeNotifyEventHelper;
import fr.toutatice.ecm.platform.listener.ToutaticeSetPublicationSections;
import fr.toutatice.ecm.platform.web.context.ToutaticeNavigationContext;
import fr.toutatice.ecm.platform.web.document.ToutaticeDocumentActions;
import fr.toutatice.ecm.platform.web.publication.ToutaticeAdministrationPublishActions;
import fr.toutatice.ecm.platform.web.publication.finder.ToutaticeRemoteRootSectionsFinder;


@Name("adminPublishActions")
@Scope(ScopeType.CONVERSATION)
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class ToutaticeAdministrationRemotePublishActions extends ToutaticeAdministrationPublishActions implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(ToutaticeAdministrationRemotePublishActions.class);

    private static final String[] ACCEPTED_LIFE_CYCLE_STATES = new String[]{ToutaticeNuxeoStudioConst.CST_DOC_STATE_PROJECT,
            ToutaticeNuxeoStudioConst.CST_DOC_STATE_APPROVED};

    @In(create = true)
    protected transient FacesMessages facesMessages;

    @In(create = true)
    protected transient ResourcesAccessor resourcesAccessor;

    @In(create = true)
    protected DocumentActions documentActions;

    /**
     * Resets document/sections cache (needed to be coherent with new model for toutatice_document_publish.xhtml view)
     * when a section is added.
     */
    @Override
    public String addSection(String sectionId) throws NuxeoException {
        String res = super.addSection(sectionId);
        resetUserRootSectionsModel();
        return res;
    }

    /**
     * Resets document/sections cache (needed to be coherent with new model for toutatice_document_publish.xhtml view)
     * when a section is removed.
     */
    @Override
    public String removeSection(String sectionId) throws NuxeoException {
        String res = super.removeSection(sectionId);
        resetUserRootSectionsModel();
        return res;
    }

    /**
     * Recompute model when section (just created) is added to the sections of a publishing Folder
     * {@link ToutaticeSetPublicationSections#handleEvent(org.nuxeo.ecm.core.event.Event)}
     */
    @Observer(value = {ToutaticeGlobalConst.CST_EVENT_SECTION_MODIFICATION})
    public void resetUserRootSectionsModelOnEvent() {
        resetUserRootSectionsModel();
    }


    /**
     * Resets document/sections cache (needed to be coherent with new model for toutatice_document_publish.xhtml view)
     * and reset model to be recomputed.
     */
    protected void resetUserRootSectionsModel() {
        // Reset cache entry
        UserRootSectionsFinder.invalidateSectionsCache(this.navigationContext.getCurrentDocument());
        // To recompute model
        AcrennesUserRootSectionsManager sectionsManager = (AcrennesUserRootSectionsManager) SeamComponentCallHelper.getSeamComponentByName("sectionsManager");
        sectionsManager.setSections(null);
    }


    /*
     * [Mantis #2805]
     * Objectif:
     * Ne pas faire apparaître l'action d'ajout/sélection sur les documents de type 'PortalSite'
     * dans la liste des sections configurables pour un espace de travail (mais seulement ses
     * sections filles).
     */
    @Override
    public boolean canAddSection(DocumentModel section) throws NuxeoException {
        boolean status = false;
        if (section != null) {
            if (!section.hasFacet(FacetNames.MASTER_PUBLISH_SPACE)) {
                status = super.canAddSection(section);
            }
        }

        return status;
    }

    /*
     * [Mantis #2805]
     * Objectif:
     * Mettre en place un page provider pour filtrer les documents à présenter
     * dans l'arbre de publication qui apparaît pour la configuration des
     * sections de publication autorisées pour un espace de travail.
     */
    @Override
    protected DocumentTreeNode getDocumentTreeNode(DocumentModel document) {
        DocumentTreeNode dtn = null;

        if (document != null) {
            Filter filter = null;
            Sorter sorter = null;
            String pageProviderName = null;
            try {
                pageProviderName = getTreeManager().getPageProviderName(PUBLICATION_TREE_PLUGIN_NAME);
                sorter = getTreeManager().getSorter(PUBLICATION_TREE_PLUGIN_NAME);
            } catch (Exception e) {
                log.error("Could not fetch filter, sorter or node type for tree ", e);
            }

            dtn = new DocumentTreeNodeImpl(document.getSessionId(), document, filter, null, sorter, pageProviderName);
        }

        return dtn;
    }

    public String getRelativePath(DocumentModel document) {
        DocumentModel sectionPublicationArea = ((ToutaticeNavigationContext) navigationContext).getSectionPublicationArea(document);
        if (ToutaticeGlobalConst.NULL_DOCUMENT_MODEL.getType().equals(sectionPublicationArea.getType())) {
            sectionPublicationArea = null;
        }
        List<String> pathSegments = ((ToutaticeDocumentActions) documentActions).getDocumentPathSegments(document, sectionPublicationArea);
        return super.formatPathFragments(pathSegments);
    }

    public DocumentModel getParentCfgOwner(DocumentModel document) {
        return ((ToutaticeRemoteRootSectionsFinder) getRootFinder()).getParentCfgOwner();
    }

    /*
     * Utilisation temporaire de la méthode parente
     * (intégration à minima publi distante)
     */
    @Override
    public DocumentModelList getSelectedSections() {
        return getSelectedSections("ALL");
    }

    public DocumentModelList getSelectedSections(String type) {
        DocumentModelList list = new DocumentModelListImpl();

        try {
            DocumentModel currentDocument = navigationContext.getCurrentDocument();
            return ((ToutaticeRemoteRootSectionsFinder) getRootFinder()).getSectionRootsForWorkspace(currentDocument, true,
                    ToutaticeRemoteRootSectionsFinder.ROOT_SECTION_TYPE.valueOf(type));
        } catch (Exception e) {
            log.warn("Failed to list the sections, error: " + e.getMessage());
        }

        return list;
    }

    /**
     * [Ticket Mantis #2792] <br/>
     * Ajouter une action au niveau d'un espace de travail "héritage des sections de publication" pour l'utilisateur disposant de droits d'aministration.
     * Cela ne remplace pas la liste existante mais l'ajoute.
     */
    @SuppressWarnings("unchecked")
    public void applySectionsHeritage() {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        if (!currentDocument.hasSchema(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_PUBLISHING)) {
            return;
        }

        try {
            // rechercher l'espace parent du document courant
            DocumentModelList parentsList = ToutaticeDocumentHelper.getParentList(documentManager, currentDocument, new filterPublicationSpace(), false);

            if (null != parentsList && 0 < parentsList.size()) {
                // lecture des sections du parent (les sections "actives" seulement)
                DocumentModel parent = parentsList.get(0);
                String[] parentSectionIds = ToutaticeDocumentHelper.filterLifeCycleStateDocuments(documentManager,
                        (String[]) parent.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME),
                        (List<String>) Arrays.asList(ACCEPTED_LIFE_CYCLE_STATES), null);

                // affecter ces sections à l'espace de travail nouvellement créé (concaténation)
                if (null != parentSectionIds && 0 < parentSectionIds.length) {
                    String[] currentDocumentSectionIds = (String[]) currentDocument
                            .getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME);

                    if (null != currentDocumentSectionIds && 0 < currentDocumentSectionIds.length) {
                        // ajouter les sections du parent (& suppression des doublons)
                        Map<String, String> sectionsIdMap = new HashMap<String, String>();

                        for (int i = 0; i < currentDocumentSectionIds.length; i++) {
                            sectionsIdMap.put(currentDocumentSectionIds[i], "");
                        }
                        for (int i = 0; i < parentSectionIds.length; i++) {
                            sectionsIdMap.put(parentSectionIds[i], "");
                        }

                        currentDocument.setPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME,
                                sectionsIdMap.keySet().toArray(new String[sectionsIdMap.size()]));
                    } else {
                        // affectation simple des sections du parent
                        currentDocument.setPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME, parentSectionIds);
                    }

                    documentManager.saveDocument(currentDocument);

                    // rafraîchir l'affichage
                    getRootFinder().reset();
                    // Recompute sections model for children of ws
                    resetUserRootSectionsModel();

                    // notifier l'utilisateur de la fin de l'opération
                    Object[] params = {parentSectionIds.length};
                    facesMessages.add(StatusMessage.Severity.INFO, resourcesAccessor.getMessages().get("acaren.info.admin.publication.sections.heritage"),
                            params);
                } else {
                    // notifier l'utilisateur de la fin de l'opération
                    facesMessages.add(StatusMessage.Severity.INFO, resourcesAccessor.getMessages().get("acaren.info.admin.publication.sections.heritage.none"));
                }
            } else {
                // notifier l'utilisateur de la fin de l'opération
                facesMessages.add(StatusMessage.Severity.INFO, resourcesAccessor.getMessages().get("acaren.info.admin.publication.sections.heritage.nospace"));
            }
        } catch (Exception e) {
            log.error("Failed to apply the section heritage, error: " + e.getMessage());
        }
    }

    private class filterPublicationSpace implements Filter {

        private static final long serialVersionUID = 1L;

        @Override
        public boolean accept(DocumentModel document) {
            return document.hasSchema(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_PUBLISHING);
        }
    }

    /**
     * [Ticket Mantis #2792] <br/>
     * propager les sections d'un espace de travail à ses espaces enfants seulement si l'enfant à une liste vide.
     */
    public void propagateSections() {
        try {
            DocumentModel currentDocument = navigationContext.getCurrentDocument();

            // vérifier que la liste de sections à propager n'est pas vide
            String[] sectionIdsList = (String[]) currentDocument.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME);

            if (null != sectionIdsList && 0 < sectionIdsList.length) {
                // lancer le processus asynchrone
                ToutaticeNotifyEventHelper.notifyEvent(documentManager, ToutaticeGlobalConst.CST_EVENT_PROPAGATE_SECTIONS, currentDocument, null);

                // notifier l'utilisateur du démarrage de l'opération
                facesMessages.add(StatusMessage.Severity.INFO, resourcesAccessor.getMessages().get("toutatice.info.admin.publication.sections.propagate"));
                log.info("Démarrage du processus de propagation des sections depuis le document '" + currentDocument.getTitle() + "'");
            } else {
                // notifier l'utilisateur que la liste de sections est vide
                facesMessages
                        .add(StatusMessage.Severity.WARN, resourcesAccessor.getMessages().get("toutatice.info.admin.publication.sections.propagate.empty"));
            }
        } catch (NuxeoException e) {
            log.error("Failed to launch the process to propagate the sections, error: " + e.getMessage());
        }
    }

    @Observer(value = {ToutaticeGlobalConst.CST_EVENT_SECTION_MODIFICATION}, create = false)
    public void resetRootSectionsContext() {
        getRootFinder().reset();
    }

}
