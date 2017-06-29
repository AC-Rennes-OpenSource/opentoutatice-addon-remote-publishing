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
 *   mberhaut1
 *    
 */
package fr.toutatice.ecm.platform.helper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.publisher.api.PublicationTree;
import org.nuxeo.ecm.platform.publisher.api.PublicationTreeNotAvailable;
import org.nuxeo.ecm.platform.publisher.api.PublisherService;
import org.nuxeo.ecm.platform.publisher.helper.RootSectionFinder;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;

import fr.toutatice.ecm.platform.web.publication.finder.ToutaticeRemoteRootSectionsFinder;

public class ToutaticePublishHelper {

	private static final Log log = LogFactory.getLog(ToutaticePublishHelper.class);

    public static PublicationTree getCurrentPublicationTreeForPublishing(DocumentModel doc, PublisherService ps, CoreSession session) throws ClientException {
        PublicationTree currentPublicationTree = null;
        if (log.isDebugEnabled()) {
            log.debug(" ----> getCurrentPublicationTreeForPublishing ");
        }

        String currentPublicationTreeNameForPublishing = null;

        List<String> publicationTrees = new ArrayList<String>(ps.getAvailablePublicationTree());

        publicationTrees = filterEmptyTrees(publicationTrees, doc, ps, session);
        if (!publicationTrees.isEmpty()) {
            currentPublicationTreeNameForPublishing = publicationTrees.get(0);
        }

        if (currentPublicationTreeNameForPublishing != null) {
            try {
                currentPublicationTree = ps.getPublicationTree(currentPublicationTreeNameForPublishing, session, null, doc);
            } catch (PublicationTreeNotAvailable e) {
                currentPublicationTree = null;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(" <---- getCurrentPublicationTreeForPublishing : " + currentPublicationTree.getName());
        }
        return currentPublicationTree;
    }

	public static DocumentModel getFirstSection(DocumentModel doc, PublisherService ps, CoreSession session) throws ClientException {

		ToutaticeRemoteRootSectionsFinder rootFinder = (ToutaticeRemoteRootSectionsFinder) ps.getRootSectionFinder(session);
		DocumentModel target = null;
		// récupération du permier espace de publication définit pour le
		// document

		DocumentModelList targetList = rootFinder.getSectionRootsForWorkspace(doc, true,
		        ToutaticeRemoteRootSectionsFinder.ROOT_SECTION_TYPE.valueOf("ALL"));
		if (targetList != null && !targetList.isEmpty()) {
			target = targetList.get(0);
			if (log.isDebugEnabled()) {
				log.debug(" -> premier espace de publication définit " + target.getName());
			}
		} else {
			throw new ClientException("Aucun espace de publication n'est défini pour ce document ");
		}
		return target;
	}

    public static List<String> filterEmptyTrees(Collection<String> trees, DocumentModel doc, PublisherService ps, CoreSession session)
            throws PublicationTreeNotAvailable, ClientException {
        List<String> filteredTrees = new ArrayList<String>();

        for (String tree : trees) {

            PublicationTree pTree = ps.getPublicationTree(tree, session, null, doc);
            if (pTree != null) {
                if (pTree.getTreeType().equals("ToutaticeRootSectionsPublicationTree")) {
                    if (pTree.getChildrenNodes().size() > 0) {
                        filteredTrees.add(tree);
                    }
                } else {
                    filteredTrees.add(tree);
                }
            }
        }
        return filteredTrees;
    }

	public static DocumentModelList getSelectedSections(String type, NavigationContext navigationContext, RootSectionFinder rsf) {
		DocumentModelList list = new DocumentModelListImpl();

		try {
			DocumentModel currentDocument = navigationContext.getCurrentDocument();
			return ((ToutaticeRemoteRootSectionsFinder) rsf).getSectionRootsForWorkspace(currentDocument, true,
			        ToutaticeRemoteRootSectionsFinder.ROOT_SECTION_TYPE.valueOf(type));
		} catch (Exception e) {
			log.warn("Failed to list the sections, error: " + e.getMessage());
		}

		return list;
	}

}
