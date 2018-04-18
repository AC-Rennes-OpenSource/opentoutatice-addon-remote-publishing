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
package fr.toutatice.ecm.platform.web.publication.finder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.SecurityConstants;

import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;

public class ToutaticeRemoteRootSectionsFinder extends ToutaticeRootSectionsFinder {

    private static final Log log = LogFactory.getLog(ToutaticeRemoteRootSectionsFinder.class);

    public static enum ROOT_SECTION_TYPE {
        LOCAL, HERITED, ALL;
    }

    protected List<String> unrestrictedSectionRootFromLocalConfig;
    protected DocumentModel unrestrictedSectionRootParentCfgOwner;

    public ToutaticeRemoteRootSectionsFinder(CoreSession userSession) {
        super(userSession);
    }


    /*
     * Ajout de robustesse:
     * Gérer le cas où une section a été supprimée (définitivement) sans que les espaces de travail qui pointent dessus
     * soient mis à jour en cache et/ou repository (retrait de la section de la métadonnée 'publish:sections').
     * 
     * @see org.nuxeo.ecm.platform.publisher.helper.RootSectionsFinder#getFiltredSectionRoots(java.util.List, boolean)
     */
    @Override
    protected DocumentModelList getFiltredSectionRoots(List<String> rootPaths, boolean onlyHeads) throws NuxeoException {
        List<DocumentRef> filtredDocRef = new ArrayList<DocumentRef>();
        List<DocumentRef> trashedDocRef = new ArrayList<DocumentRef>();

        for (String rootPath : rootPaths) {
            try {
                DocumentRef rootRef = new PathRef(rootPath);
                if (userSession.hasPermission(rootRef, SecurityConstants.READ)) {
                    filtredDocRef.add(rootRef);
                } else {
                    // Nuxeo Jira #5236 : échapper les simples quotes dans les chemins
                    DocumentModelList accessibleSections = userSession.query(buildQuery(rootPath.replace("'", "\\'")));
                    for (DocumentModel section : accessibleSections) {
                        if (onlyHeads && ((filtredDocRef.contains(section.getParentRef())) || (trashedDocRef.contains(section.getParentRef())))) {
                            trashedDocRef.add(section.getRef());
                        } else {
                            filtredDocRef.add(section.getRef());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get the section root '" + rootPath + "', error: " + e.getMessage());
            }
        }

        DocumentModelList documents = userSession.getDocuments(filtredDocRef.toArray(new DocumentRef[filtredDocRef.size()]));
        return filterDocuments(documents);
    }

    @Override
    protected void computeUnrestrictedRoots(CoreSession session) throws NuxeoException {

        if (currentDocument != null) {
            /*
             * Mantis #2993: tout containeur peut recevoir une configuration des sections de publication autorisées.
             * Si le parent possède une configuration vide, il faut poursuivre la recherche d'un parent (jusqu'à l'élément root).
             */
            unrestrictedSectionRootParentCfgOwner = getPublishingParent(session, currentDocument);
            unrestrictedSectionRootFromWorkspaceConfig = new ArrayList<String>();

            if (unrestrictedSectionRootParentCfgOwner != null) {
                DocumentModelList sectionRootsFromWorkspaceConfig = getSectionRootsFromWorkspaceConfig(unrestrictedSectionRootParentCfgOwner, session);
                for (DocumentModel root : sectionRootsFromWorkspaceConfig) {
                    unrestrictedSectionRootFromWorkspaceConfig.add(root.getPathAsString());
                }
            }
        }

        if (unrestrictedDefaultSectionRoot == null) {
            unrestrictedDefaultSectionRoot = Collections.emptyList();
        }

    }

    protected void computeUnrestrictedLocalRoots(CoreSession session) throws NuxeoException {

        if (currentDocument != null) {
            DocumentModelList sectionRootsFromWorkspaceConfig = getSectionRootsFromWorkspaceConfig(currentDocument, session);
            unrestrictedSectionRootFromLocalConfig = new ArrayList<String>();
            for (DocumentModel root : sectionRootsFromWorkspaceConfig) {
                unrestrictedSectionRootFromLocalConfig.add(root.getPathAsString());
            }

            if (log.isDebugEnabled() && unrestrictedSectionRootFromLocalConfig != null) {
                log.debug("#computeUnrestrictedLocalRoots | unrestrictedSectionRootFromLocalConfig: "
                        + StringUtils.join(unrestrictedSectionRootFromLocalConfig, ", "));
            }
        }

    }

    @Override
    public DocumentModelList getSectionRootsForWorkspace(DocumentModel currentDoc, boolean addDefaultSectionRoots) throws NuxeoException {
        return getSectionRootsForWorkspace(currentDoc, addDefaultSectionRoots, ROOT_SECTION_TYPE.ALL);
    }

    public DocumentModelList getSectionRootsForWorkspace(DocumentModel currentDoc, boolean addDefaultSectionRoots, ROOT_SECTION_TYPE sectionListType)
            throws NuxeoException {
        if ((currentDocument == null) || (!currentDocument.getRef().equals(currentDoc.getRef()))) {
            computeUserSectionRoots(currentDoc);
        }

        if (unrestrictedDefaultSectionRoot == null || unrestrictedDefaultSectionRoot.isEmpty()) {
            DocumentModelList defaultSectionRoots = getDefaultSectionRoots(session);
            unrestrictedDefaultSectionRoot = new ArrayList<String>();

            for (DocumentModel root : defaultSectionRoots) {
                unrestrictedDefaultSectionRoot.add(root.getPathAsString());
            }
        }

        List<String> agregatedList = new ArrayList<String>();
        if (ROOT_SECTION_TYPE.ALL.equals(sectionListType) || ROOT_SECTION_TYPE.HERITED.equals(sectionListType)) {
            agregatedList.addAll(unrestrictedSectionRootFromWorkspaceConfig);
        }
        if (ROOT_SECTION_TYPE.ALL.equals(sectionListType) || ROOT_SECTION_TYPE.LOCAL.equals(sectionListType)) {
            agregatedList.addAll(unrestrictedSectionRootFromLocalConfig);
        }

        return getFiltredSectionRoots(agregatedList, true);
    }

    public DocumentModelList getSectionRootsForLocal(DocumentModel currentDoc, boolean addDefaultSectionRoots) throws NuxeoException {
        if ((currentDocument == null) || (!currentDocument.getRef().equals(currentDoc.getRef()))) {
            computeUserSectionRoots(currentDoc);
        }

        return getFiltredSectionRoots(unrestrictedSectionRootFromLocalConfig, true);
    }

    public DocumentModel getParentCfgOwner() {
        return this.unrestrictedSectionRootParentCfgOwner;
    }

    /**
     * Gets first parent having publishing:sections not empty.
     * 
     * @param session
     * @param document
     * @return parent having publishing:sections not empty
     * @throws NuxeoException
     */
    private DocumentModel getPublishingParent(CoreSession session, DocumentModel document) throws NuxeoException {
        // Result
        DocumentModel publishingParent = null;

        // Check current space
        DocumentModel parentDocument = document;

        while (!hasConfiguredSections(parentDocument)) {
            if (ToutaticeNuxeoStudioConst.CST_DOC_TYPE_ROOT.equals(parentDocument.getType())) {
                break;
            }
            parentDocument = session.getDocument(parentDocument.getParentRef());
        }

        // Found
        if (!ToutaticeNuxeoStudioConst.CST_DOC_TYPE_ROOT.equals(parentDocument.getType())) {
            publishingParent = parentDocument;
        }

        return publishingParent;
    }

    /**
     * Checks if document has configured sections.
     * 
     * @param document
     * @return
     */
    private boolean hasConfiguredSections(DocumentModel document) {
        boolean has = false;

        if (document != null && document.hasSchema(SCHEMA_PUBLISHING)) {
            String[] sectionIdsArray = (String[]) document.getPropertyValue(SECTIONS_PROPERTY_NAME);
            has = sectionIdsArray != null && sectionIdsArray.length > 0;
        }

        return has;
    }

    @Override
    public void run() throws NuxeoException {
        computeUnrestrictedRoots(session);
        computeUnrestrictedLocalRoots(session);
    }
}
