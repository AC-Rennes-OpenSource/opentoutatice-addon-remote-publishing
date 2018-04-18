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

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.Filter;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.LifeCycleFilter;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeNotifyEventHelper;
import fr.toutatice.ecm.platform.core.helper.ToutaticeSilentProcessRunnerHelper;

public class ToutaticePropagateSectionsAsynchronouslyListener implements PostCommitEventListener {

	private static final Log log = LogFactory.getLog(ToutaticePropagateSectionsAsynchronouslyListener.class);

	private static final String[] ACCEPTED_LIFE_CYCLE_STATES = new String[] {ToutaticeNuxeoStudioConst.CST_DOC_STATE_PROJECT, ToutaticeNuxeoStudioConst.CST_DOC_STATE_APPROVED};

	@Override
	public void handleEvent(EventBundle events) throws NuxeoException {

        for (Event event : events) {
            if (ToutaticeGlobalConst.CST_EVENT_PROPAGATE_SECTIONS.equals(event.getName())) {
            	handleEventPropagateSections(event);
            }
        }
    }

    /**
     * Propage les sections du document courant à tous les documents qui ont une liste de sections vide.
     * 
     * @param event l'événement déclencheur 
     * @throws NuxeoException erreur pendant le processus de propagation
     */
	public void handleEventPropagateSections(Event event) throws NuxeoException {
    	boolean runInUnrestrictedMode = false;
    	boolean doAppendSections = false;
    	String sectionId = ToutaticeGlobalConst.CST_EVENT_OPTION_VALUE_ALL;
    	
		if (event.getContext() instanceof DocumentEventContext) {
			DocumentEventContext  eventContext = (DocumentEventContext) event.getContext();
			CoreSession session = eventContext.getCoreSession();
			DocumentModel document = eventContext.getSourceDocument();
			Map<String, Serializable> options = eventContext.getProperties();
			
			try {
				// lire les options de l'évènement
				if (null != options) {
					runInUnrestrictedMode = (Boolean) (options.containsKey(ToutaticeGlobalConst.CST_EVENT_OPTION_KEY_RUN_UNRESTRICTED) ? options.get(ToutaticeGlobalConst.CST_EVENT_OPTION_KEY_RUN_UNRESTRICTED) : false);
					doAppendSections = (Boolean) (options.containsKey(ToutaticeGlobalConst.CST_EVENT_OPTION_KEY_APPEND) ? options.get(ToutaticeGlobalConst.CST_EVENT_OPTION_KEY_APPEND) : false);
					sectionId = (String) (options.containsKey(ToutaticeGlobalConst.CST_EVENT_OPTION_KEY_SECTION_ID) ? options.get(ToutaticeGlobalConst.CST_EVENT_OPTION_KEY_SECTION_ID) : ToutaticeGlobalConst.CST_EVENT_OPTION_VALUE_ALL);
				}
				
				// récupérer les sections à propager (toutes les sections du parent ou bien juste la section nouvellement créée)
				String[] sectionIdsList = null;
				if (!ToutaticeGlobalConst.CST_EVENT_OPTION_VALUE_ALL.equals(sectionId)) {
					sectionIdsList = new String[] {sectionId};
				} else {
					sectionIdsList = ToutaticeDocumentHelper.filterLifeCycleStateDocuments(session, 
							(String[]) document.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME), 
							(List<String>) Arrays.asList(ACCEPTED_LIFE_CYCLE_STATES), null);
				}

				//  propager les sections en mode "silencieux" (pas de modification des meta-données dublincore)
				if (null != sectionIdsList && 0 < sectionIdsList.length) {
					PropagateSectionsRunner runner = new PropagateSectionsRunner(session, document, sectionIdsList, doAppendSections);
					runner.silentRun(runInUnrestrictedMode);

					// notifier la fin du traitement
					ToutaticeNotifyEventHelper.notifyAuditEvent(session, 
							ToutaticeRPConstants.CST_AUDIT_EVENT_PROPAGATE_PROCESS_COMPLETED, 
							document, 
							String.format("Propagation des sections depuis '%s', mises à jour '%s'", document.getTitle(), runner.getNbrUpdatedDocs()));
				} else {
					// notifier la fin du traitement
				    ToutaticeNotifyEventHelper.notifyAuditEvent(session, 
				    		ToutaticeRPConstants.CST_AUDIT_EVENT_PROPAGATE_PROCESS_COMPLETED, 
							document, 
							String.format("Propagation des sections depuis '%s', mises à jour '%s'", document.getTitle(), 0));
				}
			} catch (Exception e) {
				log.error("Failed to propagate the publication sections for the document '" + document.getTitle() + "', error: " + e.getMessage());
			}
			
			log.info("Fin du processus de propagation des sections depuis l'espace de travail '" + document.getTitle() + "'");
		}
    }

    /**
     * Classe pour exécuter les opérations de propagation des sections dans un mode "silencieux" (pas de modification des données dublincore)
     * (il ne s'agit pas d'un mode d'exécution unrestricted)
     */
    private class PropagateSectionsRunner extends ToutaticeSilentProcessRunnerHelper {
    	
    	private String[] sectionIdsList;
    	private DocumentModel baseDocument;
    	private int nbrUpdatedDocs;
    	private boolean doAppendSections;
    	
		public PropagateSectionsRunner(CoreSession session, DocumentModel baseDocument, String[] sectionIdsList, boolean doAppendSections) {
    		super(session);
    		this.baseDocument = baseDocument;
    		this.sectionIdsList = sectionIdsList;
    		this.nbrUpdatedDocs = 0;
    		this.doAppendSections = doAppendSections;
    	}
		
		public int getNbrUpdatedDocs() {
			return nbrUpdatedDocs;
		}
    	
		@Override
		public void run() throws NuxeoException {
			Filter documentFilter = null;
			
			// récupérer la liste des documents à mettre à jour
			String query = "SELECT * FROM Document WHERE ecm:path STARTSWITH '%s' AND ecm:mixinType = 'Folderish' AND ecm:primaryType NOT IN ('SectionRoot', 'Section') AND ecm:mixinType != 'HiddenInNavigation' AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted' "  + 
							"AND ecm:isProxy = 0";

			if (this.doAppendSections) {
				// ajouter à la liste des sections autorisées
				documentFilter = new PublishingDocfilter();
			} else {
				// sinon, sélectionner uniquement les espaces de travail qui n'ont pas de sections autorisées configurées
				documentFilter = new EmptySectionsFieldfilter(this.session);
			}
			
			String path = this.baseDocument.getPath().toString().replace("'", "\\'");
			DocumentModelList childDocumentList = this.session.query(String.format(query, path), documentFilter);
			this.nbrUpdatedDocs = childDocumentList.size();
			
			// mettre à jour les sections pour chaque document fils
			for (DocumentModel childDocument : childDocumentList) {
				updateSectionList(childDocument, this.sectionIdsList, this.doAppendSections);
				session.saveDocument(childDocument);
			}
		}
    	
    }
    
    private void updateSectionList(DocumentModel document, String[] sectionIdsList, boolean doAppend) throws NuxeoException {
    	try {
    		String[] documentSectionIds = (String[]) document.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME);
    		
    		if (null != documentSectionIds && 0 < documentSectionIds.length && doAppend) {
    			// ajouter les sections (& suppression des doublons)
    			Map<String, String> sectionsIdMap = new HashMap<String, String>();
    			
    			for (int i = 0; i < documentSectionIds.length; i++) {
    				sectionsIdMap.put(documentSectionIds[i], "");
    			}
    			
    			for (int i = 0; i < sectionIdsList.length; i++) {
    				sectionsIdMap.put(sectionIdsList[i], "");
    			}
    			
    			document.setPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME, sectionsIdMap.keySet().toArray(new String[sectionsIdMap.size()]));
    		} else {
    			document.setPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME, sectionIdsList);
    		}
    	} catch (Exception e) {
    		log.error("Failed to set the sections list to the document '" + document.getTitle() + "', error: " + e.getMessage());
    	}
    }
    
    /**
     * Permet de filtrer les documents qui on leur liste de sections déjà initialisée
     */
    @SuppressWarnings("unchecked")
    private class EmptySectionsFieldfilter implements Filter {

		private static final long serialVersionUID = 976112236321715258L;
				
		CoreSession session;
		Filter lcFilter;

		public EmptySectionsFieldfilter(CoreSession session) {
			this.session = session;
			this.lcFilter = new LifeCycleFilter((List<String>) Arrays.asList(ACCEPTED_LIFE_CYCLE_STATES), null);
		}
		
		@Override
		public boolean accept(DocumentModel docModel) {
			boolean status = true;
			
			try {
				if (!docModel.hasSchema(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_PUBLISHING)) {
					status = false;
				} else {
					String[] sectionIdsList = (String[]) docModel.getPropertyValue(ToutaticeNuxeoStudioConst.CST_DOC_XPATH_NUXEO_SECTIONS_PROPERTY_NAME);
					if (sectionIdsList != null && sectionIdsList.length > 0) {
						// ignorer les sections supprimées
						for (String sectionId : sectionIdsList) {
							DocumentModel section = session.getDocument(new IdRef(sectionId));
							if (this.lcFilter.accept(section)) {
								status = false;
								break;
							}
						}
					}
				}
			} catch (Exception e) {
				status = false;
			}
			
			return status;
		}
    	
    }

    /**
     * Permet de filtrer les documents qui n'ont pas le schéma 'publishing'
     */
    private class PublishingDocfilter implements Filter {

		private static final long serialVersionUID = 1L;

		@Override
		public boolean accept(DocumentModel document) {
			return document.hasSchema(ToutaticeNuxeoStudioConst.CST_DOC_SCHEMA_PUBLISHING);
		}
    }

}
