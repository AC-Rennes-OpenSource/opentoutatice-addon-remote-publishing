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
package fr.toutatice.ecm.platform.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.seam.core.Events;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.Filter;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;

public class ToutaticeSetPublicationSections implements EventListener {

//	private static final Log log = LogFactory.getLog(AcarenSetPublicationSections.class);
	
	@Override
	public void handleEvent(Event event) throws ClientException {
		if (event.getContext() instanceof DocumentEventContext) {
			DocumentEventContext  eventContext = (DocumentEventContext) event.getContext();
			DocumentModel document = eventContext.getSourceDocument();
			
			boolean isMutable = !document.isImmutable();
			boolean hasPublishingShema = document.hasSchema(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_PUBLISHING);
			boolean isSection = ToutaticeNuxeoStudioConst.CST_DOC_TYPE_SECTION.equals(document.getType());
			
			if (isMutable && (hasPublishingShema || isSection)) {
				CoreSession session = eventContext.getCoreSession();
				
				// rechercher l'espace de travail parent (possède la métadonnée de sauvegarde des sections de publication autorisées)
				DocumentModelList parentsList = ToutaticeDocumentHelper.getParentList(session, document, new filterPublicationSpace(), false);
				
				if (null != parentsList && 0 < parentsList.size()) {
					// lecture des sections du parent
					DocumentModel parent = parentsList.get(0);
					String[] parentSectionIds = (String[]) parent.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME);
					
					if (hasPublishingShema) {
						/**
						 * Création d'un espace de travail
						 */
						
						// propager les sections du parent à ce nouvel espace
						if (null != parentSectionIds && 0 < parentSectionIds.length) {
							document.setPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME, parentSectionIds);
							session.saveDocument(document);
						}
					} else {
						/**
						 * Création d'une section dans un espace de publication:
						 */
						
						// ajouter cette nouvelle section automatiquement sur l'espace de travail parent (si l'utilisateur qui a céé la section possède un droit d'écriture sur le parent)
						if (session.hasPermission(eventContext.getPrincipal(), parent.getRef(), SecurityConstants.WRITE)) {
							List<String> newParentSectionIds = new ArrayList<String>();
							
							if (null != parentSectionIds && 0 < parentSectionIds.length) {
								newParentSectionIds.addAll(Arrays.asList(parentSectionIds));
							}
							
							newParentSectionIds.add(document.getId());
							parent.setPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME, newParentSectionIds.toArray(new String[newParentSectionIds.size()]));
							session.saveDocument(parent);

							// Faire en sorte que l'onglet d'administration des sections réactualise les sections configurées
							Events.instance().raiseEvent(ToutaticeGlobalConst.CST_EVENT_SECTION_MODIFICATION, "");
						}
						
						/* [Mantis #2861] Ajouter la section nouvellement créée à la liste des sections autorisées des espaces de travail fils
						* lancer le processus asynchrone
						* 
						*  Note: implémentation commentée car elle implique un mode system (unrestricted) alors qu'il ne faudrait pas
						*/
//						Map<String, Serializable> options = new HashMap<String, Serializable>();
//						options.put(GlobalConst.CST_EVENT_OPTION_KEY_APPEND, true);
//						options.put(GlobalConst.CST_EVENT_OPTION_KEY_SECTION_ID, document.getId());
//						
//						AcarenNotifyEventHelper.notifyEvent(session, 
//								GlobalConst.CST_EVENT_PROPAGATE_SECTIONS, 
//								parent, 
//								options);
//						
//						log.info("Démarrage du processus de propagation des sections depuis le document '" + parent.getTitle() + "'");						
					}
				}
			}
		}
		
	}
	
    private class filterPublicationSpace implements Filter {

		private static final long serialVersionUID = 1L;

		@Override
		public boolean accept(DocumentModel document) {
			return document.hasSchema(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_PUBLISHING);
		}
    }
    
}