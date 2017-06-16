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
package fr.toutatice.ecm.platform.web.publication.validation;

import static org.jboss.seam.ScopeType.CONVERSATION;
import static org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager.CURRENT_DOCUMENT_SELECTION;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.core.Events;
import org.jboss.seam.international.StatusMessage;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.contentview.seam.ContentViewActions;
import org.nuxeo.ecm.platform.publisher.api.PublicationNode;
import org.nuxeo.ecm.platform.publisher.api.PublicationTree;
import org.nuxeo.ecm.platform.publisher.api.PublicationTreeNotAvailable;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.api.PublisherException;
import org.nuxeo.ecm.platform.publisher.api.PublisherService;
import org.nuxeo.ecm.platform.publisher.api.PublishingEvent;
import org.nuxeo.ecm.platform.publisher.helper.RootSectionFinder;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;
import org.nuxeo.ecm.platform.ui.web.util.ComponentUtils;
import org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager;
import org.nuxeo.ecm.webapp.helpers.EventNames;
import org.nuxeo.runtime.api.Framework;

import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;
import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;
import fr.toutatice.ecm.platform.helper.ToutaticePublishHelper;
import fr.toutatice.ecm.platform.web.publication.ToutaticePublishActionsBean;
import fr.toutatice.ecm.platform.web.publication.finder.ToutaticeRemoteRootSectionsFinder;

@Name("publishActions")
@Scope(CONVERSATION)
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class ToutaticeRemotePublishActionsBean extends ToutaticePublishActionsBean {

	private static final Log log = LogFactory.getLog(ToutaticeRemotePublishActionsBean.class);

	private static final long serialVersionUID = 1L;

	private static final String CST_DEFAULT_DOCUMENT_SELECTION_REJECT_COMMENT = "Rejet de la demande de publication";

	@In(create = true)
	protected transient CoreSession documentManager;

	@In(create = true)
	protected ContentViewActions contentViewActions;

	private transient DocumentModel publishableDocInSection = null;
	private transient Map<String, PublishedDocument> publishedDocInSectionMap = null;
	private transient Map<String, PublishedDocument> publishedDocModelMap = null;
	private transient Map<String, PublicationNode> PublicationNodeMap = null;
	private transient Map<String, PublicationTree> PublicationTreeMap = null;
	private transient Map<String, Boolean> hasValidationTaskMap = null;
	private transient Map<String, Boolean> canManagePublishingMap = null;
	private transient Map<String, Boolean> mapIsPublishedDoc = null;
	private transient Map<String, List<PublishedDocument>> publishedDocsOfTreeMap = null;

	@Override
    public boolean isRemoteProxy(DocumentModel proxy) {
        return proxy.isProxy() && (!StringUtils.endsWith(proxy.getName(), ToutaticeGlobalConst.CST_PROXY_NAME_SUFFIX) || StringUtils.endsWith(proxy.getName(), ToutaticeRPConstants.CST_PROXY_REMOTE_LEGACY_NAME_SUFFIX));
    }

	public boolean isRemoteProxyInSelection() {
		boolean status = false;

		List<DocumentModel> docsList = documentsListsManager.getWorkingList(CURRENT_DOCUMENT_SELECTION);
		if (null != docsList && 0 < docsList.size()) {
			for (DocumentModel doc : docsList) {
				status = isRemoteProxy(doc);
				if (true == status) {
					status = true;
					break;
				}
			}
		}

		return status;
	}

	/**
	 * Vérifie si une version publiable du document est disponible (déjà publiée
	 * ou pas)
	 * 
	 * @return true si une version publiable existe. false sinon.
	 */
	public boolean isPublicationAvailable() {
		boolean status = false;

		try {
			status = (getPublishableDoc() != null) ? true : false;
		} catch (ClientException e) {
			DocumentModel currentDocument = navigationContext.getCurrentDocument();
			String docName = (null != currentDocument) ? currentDocument.getName() : "<unknown>";

            if (log.isDebugEnabled()) {
                log.debug("Failed to evaluate the condition 'isPublicationAvailable', for the document '" + docName + "', error: " + e.getMessage());
            }
		}

		return status;
	}

	public boolean canPublishTo(DocumentModel section) throws ClientException {
		boolean status = false;

        if (log.isDebugEnabled() && section != null) {
            log.debug("ToutaticeRemotePublishActionsBean.canPublishTo | " + section.getPathAsString());
        }

		try {
			DocumentModel publishableDoc = getPublishableDoc();
			if (null != publishableDoc) {
				PublishedDocument publishedDoc = getPublishedDoc(section);
				if (null == publishedDoc) {
					status = true;
				} else {
					DocumentRef srcDocRef = publishedDoc.getSourceDocumentRef();
					DocumentModel publishedDocModel = documentManager.getDocument(srcDocRef);

					if (ToutaticeDocumentHelper.DocumentVersionComparator.isNewer(publishedDocModel, publishableDoc)) {
						status = true;
					}
				}

				if (status == true) {
					PublicationNode sectionNode = wrapToPublicationNode(section);
					status = super.canPublishTo(sectionNode);
				}
			}
		} catch (Exception e) {
			log.error("Failed to execute 'canPublishTo' on section '" + section.getTitle() + "', error: " + e.getMessage());
			status = false;
		}

		return status;
	}

	public boolean canUnpublishFrom(DocumentModel section) throws ClientException {
		boolean status = false;

		try {
			PublishedDocument publishedDoc = getPublishedDoc(section);
			if (null != publishedDoc) {
				status = super.canUnpublish(publishedDoc);
			}
		} catch (Exception e) {
			log.error("Failed to execute 'canUnpublishFrom' on section '" + section.getTitle() + "', error: " + e.getMessage());
		}

		return status;
	}

	public String doPublish(DocumentModel section) throws ClientException {
		String status = doPublishVersion(section);
		removeFromPublishContext(section);
		return status;
	}

	/**
	 * Override doPublish() method so that: 
	 * 1) the latest PUBLISHABLE document is taken into account instead of the live/current version. 
	 * 2) the proxy name is kept unchanged
	 * 
	 * @see org.nuxeo.ecm.platform.publisher.web.PublishActionsBean#doPublish(org.nuxeo.ecm.platform.publisher.api.PublicationTree,
	 *      org.nuxeo.ecm.platform.publisher.api.PublicationNode)
	 */
	public String doPublishVersion(DocumentModel section) throws ClientException {
		PublicationTree tree = getCurrentPublicationTreeForPublishing();
		if (tree == null) {
			return null;
		}

		PublicationNode publicationNode = wrapToPublicationNode(section);
		DocumentModel publishableDoc = getPublishableDoc();

		PublishedDocument publishedDocument;
		try {
			/*
			 * récupérer le document éventuellement déjà publié (qui sera
			 * remplacé lors de l'acceptation de la publication du document
			 * courant, dans l'état 'pending')
			 */
			String formerProxyName = null;
			DocumentModel cd = navigationContext.getCurrentDocument();
			DocumentModel cds = documentManager.getSourceDocument(cd.getRef());
			DocumentModelList proxies = documentManager.getProxies(cds.getRef(), section.getRef());
			if (proxies != null) {
				for (DocumentModel p : proxies) {
					PublishedDocument pd = tree.wrapToPublishedDocument(p);
					if (!pd.isPending()) {
						formerProxyName = p.getName();
					}
				}
			}

			// publier le document
			publishedDocument = tree.publish(publishableDoc, publicationNode, publicationParameters);

			// renommage du proxy pour suivre la convention de nommage (en mode unrestricted car le demandeur n'a pas de droit d'écriture sur le dossier cible)
			UnrestrictedSessionRunner runner = new UnrestrictedSetProxyNameRunner(documentManager, publishedDocument, formerProxyName, section);
			runner.runUnrestricted();
		} catch (PublisherException e) {
			log.error(e, e);
			
			facesMessages.addFromResourceBundle(StatusMessage.Severity.ERROR,
                    messages.get("document_published"), messages.get(e.getMessage()));
			
			return null;
		}

		FacesContext context = FacesContext.getCurrentInstance();
		// Log event
		if (publishedDocument.isPending()) {
			String comment = ComponentUtils.translate(context, "publishing.waiting", publicationNode.getPath(), tree.getConfigName());

			Events.instance().raiseEvent(EventNames.DOCUMENT_SUBMITED_FOR_PUBLICATION);
			notifyEvent(PublishingEvent.documentWaitingPublication.name(), null, comment, null, publishableDoc);
			
			facesMessages.addFromResourceBundle(StatusMessage.Severity.INFO,
                    messages.get("document_submitted_for_publication"), messages.get(publishableDoc.getType()));

		} else {
			String comment = ComponentUtils.translate(context, "publishing.done", publicationNode.getPath(), tree.getConfigName());

			Events.instance().raiseEvent(EventNames.DOCUMENT_PUBLISHED);
			notifyEvent(PublishingEvent.documentPublished.name(), null, comment, null, publishableDoc);
			
			facesMessages.addFromResourceBundle(StatusMessage.Severity.INFO,
                    messages.get("document_published"), messages.get(publishableDoc.getType()));
			
		}

		return null;
	}

	private class UnrestrictedSetProxyNameRunner extends UnrestrictedSessionRunner {

		private SimpleCorePublishedDocument publishedDocument;
		private DocumentModel section;

		protected UnrestrictedSetProxyNameRunner(CoreSession session, PublishedDocument publishedDocument, String formerProxyName, DocumentModel section) {
			super(session);
			this.publishedDocument = (SimpleCorePublishedDocument) publishedDocument;
			this.section = section;
		}

		@Override
		public void run() throws ClientException {
			DocumentRef sdRef = this.publishedDocument.getSourceDocumentRef();
			DocumentModel dm = this.session.getSourceDocument(sdRef);
			String newProxyName = dm.getName();
			if (this.publishedDocument.isPending()) {
				// publication avec workflow d'approbation
				newProxyName = dm.getName() + ToutaticeRPConstants.CST_REMOTE_PROXY_PENDING_NAME_SUFFIX;
			}

			DocumentModel proxy = this.publishedDocument.getProxy();
			if (!proxy.getName().equals(newProxyName)) {
				this.session.move(proxy.getRef(), this.section.getRef(), newProxyName);
			}
		}

	}

	public String unPublishFrom(DocumentModel section) throws ClientException {
		String result = null;

		PublishedDocument publishedDoc = getPublishedDoc(section);
		if (null != publishedDoc) {
			result = super.unPublish(publishedDoc);
			removeFromPublishContext(section);
		}

		return result;
	}

	public DocumentModelList filterEmptySections(DocumentModelList sections) {
		DocumentModelList notEmptySections = new DocumentModelListImpl();

		try {
			if (null != sections) {
				for (DocumentModel section : sections) {
					if (null != getPublishedDoc(section)) {
						notEmptySections.add(section);
					}
				}
			}
		} catch (ClientException ce) {
			log.error("Failed to list the sections containing a published document, error: " + ce.getMessage());
		}
		return notEmptySections;
	}

	public String getPublishedVersion(DocumentModel section) throws ClientException {
		String versionLabel = "";

		PublishedDocument publishedDoc = getPublishedDoc(section);
		if (null != publishedDoc) {
			versionLabel = publishedDoc.getSourceVersionLabel();
		}

		return versionLabel;
	}

	public String getPublishableVersion(DocumentModel section) throws ClientException {
		String versionLabel = "";

		DocumentModel publishableDoc = getPublishableDoc();
		if (canPublishTo(section)) {
			if (null != publishableDoc) {
				versionLabel = publishableDoc.getVersionLabel();
			}
		} else {
			if (null == publishableDoc) {
				versionLabel = messages.get("toutatice.label.publish.not.available");
			}

			if (!hasPublicationPermission(section)) {
				String label = messages.get("toutatice.label.publish.no.permission");
				versionLabel += versionLabel.isEmpty() ? label : ", " + label.toLowerCase();
			}
		}

		return versionLabel;
	}

	/**
	 * [Ticket Mantis #2855: Publication dans les sections: changer l'affichage
	 * de l'onglet Publier vers...] <br/>
	 */
	private boolean hasPublicationPermission(DocumentModel section) {
		boolean status = false;

		try {
			if (documentManager.hasPermission(section.getRef(), SecurityConstants.WRITE) || documentManager.hasPermission(section.getRef(), "CanAskForPublishing")) {
				status = true;
			}
		} catch (ClientException e) {
			log.debug("Failed to check the permission on the publication sections '" + section.getName() + "'");
		}

		return status;
	}

	public String getActionLabel(DocumentModel section) throws ClientException {
		String actionLabel = messages.get("label.publish.publish");

		PublishedDocument publishedDoc = getPublishedDoc(section);
		if (null != publishedDoc) {
			actionLabel = messages.get("label.publish.publish.again");
		}

		return actionLabel;
	}

	public PublishedDocument getPublishedDoc(DocumentModel section) throws ClientException {

		if (null == this.publishedDocInSectionMap) {
			this.publishedDocInSectionMap = new HashMap<String, PublishedDocument>();
		}

		if (!this.publishedDocInSectionMap.containsKey(section.getId())) {
			/*
			 * Initialiser le cache avec le document "NULL": Permet de ne pas
			 * relancer une recherche des documents publiés dans la section et
			 * donc de ne pas rejouer les requêtes SQL en base. Gain de performance.
			 */
			this.publishedDocInSectionMap.put(section.getId(), ToutaticeGlobalConst.NULL_PUBLISHED_DOCUMENT_MODEL);

			/*
			 * Rechercher les documents publiés dans la section
			 */
			PublishedDocument publishedDoc = findPublishedDocInSection(section, null);
			if(publishedDoc!=null){
				this.publishedDocInSectionMap.put(section.getId(), publishedDoc);
			}
		}

		PublishedDocument pd = publishedDocInSectionMap.get(section.getId());
		return (!ToutaticeGlobalConst.NULL_PUBLISHED_DOCUMENT_MODEL.getPath().equals(pd.getPath())) ? pd : null;
	}

	/**
	 * Rechercher si le document est publié dans la section
	 * 
	 * @param section
	 *            espace de publication
	 * @param doc
	 *            document recherché
	 * @param majMap
	 *            mise à jour du 'cache' publishedDocInSectionMap
	 * @throws ClientException
	 * @return true si le document est
	 * 
	 */
	private PublishedDocument findPublishedDocInSection(DocumentModel section, DocumentModel doc) throws ClientException {
		
		List<PublishedDocument> publishedDocList = null;
		PublishedDocument res = null;
		
			String treeName = getPublicationTreeNameFromSectionDocument(section);
			if (StringUtils.isNotBlank(treeName)) {
				if(doc==null){
					publishedDocList = getPublishedDocumentsFor(treeName);
				}else{
					publishedDocList = findPublishedDocsOfTree(treeName, doc);
				}
				if (null != publishedDocList && !publishedDocList.isEmpty()) {
					for (PublishedDocument publishedDoc : publishedDocList) {
						String pdParentPath = publishedDoc.getParentPath();
						if (pdParentPath.equals(section.getPath().toString())) {
							res = publishedDoc;						
							// il ne peut y avoir qu'une version publiée d'un
							// document à la fois
							break;
						}
					}
				}
			}
		
		return res;
	}

	public List<PublishedDocument> getPublishedDocumentsFor(String treeName, DocumentModel doc) throws ClientException {
		if (treeName == null || "".equals(treeName)) {
			return null;
		}
		try {
			PublicationTree tree = publisherService.getPublicationTree(treeName, documentManager, null);
			return tree.getExistingPublishedDocument(new DocumentLocationImpl(doc));
		} catch (PublicationTreeNotAvailable e) {
			return null;
		}
	}

	private String getPublicationTreeNameFromSectionDocument(DocumentModel section) {
		String treeName = null;

		try {
			PublicationNode treeNode = wrapToPublicationNode(section);
			treeName = treeNode.getTreeConfigName();
		} catch (Exception e) {
			log.error("Failed to get the publication tree name for the section '" + section.getName() + "', error: " + e.getMessage());
		}

		return treeName;
	}

	private DocumentModel getPublishableDoc() throws ClientException {
		if (null == publishableDocInSection) {
			DocumentModel currentDoc = navigationContext.getCurrentDocument();
			if (null != currentDoc) {
				if (ToutaticeNuxeoStudioConst.CST_DOC_STATE_APPROVED.equals(currentDoc.getCurrentLifeCycleState())) {
					publishableDocInSection = currentDoc;
				} else {
					publishableDocInSection = ToutaticeDocumentHelper.getLatestDocumentVersion(currentDoc, documentManager);
				}
			}
		}

		return publishableDocInSection;
	}

	private void removeFromPublishContext(DocumentModel section) {
		this.publishedDocInSectionMap.remove(section.getId());
		String treeName = getPublicationTreeNameFromSectionDocument(section);
		this.publishedDocsOfTreeMap.remove(treeName);
	}

	@Observer(value = { EventNames.DOCUMENT_SELECTION_CHANGED, EventNames.DOMAIN_SELECTION_CHANGED, EventNames.CONTENT_ROOT_SELECTION_CHANGED,
			EventNames.DOCUMENT_PUBLICATION_APPROVED, EventNames.DOCUMENT_PUBLICATION_REJECTED, EventNames.DOCUMENT_CHANGED, EventNames.GO_HOME }, create = false)
	public void resetPublishContext() {
		publishableDocInSection = null;
		if (null != this.publishedDocInSectionMap) {
			this.publishedDocInSectionMap.clear();
		}
		if (null != this.publishedDocsOfTreeMap) {
			this.publishedDocsOfTreeMap.clear();
		}
		if (null != this.publishedDocModelMap) {
			this.publishedDocModelMap.clear();
		}
		if (null != this.PublicationNodeMap) {
			this.PublicationNodeMap.clear();
		}
		if (null != this.PublicationTreeMap) {
			this.PublicationTreeMap.clear();
		}
		if (null != this.hasValidationTaskMap) {
			this.hasValidationTaskMap.clear();
		}
		if (null != this.canManagePublishingMap) {
			this.canManagePublishingMap.clear();
		}
		if (null != this.mapIsPublishedDoc) {
			this.mapIsPublishedDoc.clear();
		}

	}

	@Override
	public boolean canUnpublishProxy(DocumentModel proxy) throws ClientException {
		boolean status = false;
		
		PublishedDocument publishedDocument = getPublishedDocumentModel(proxy);
		if (null != publishedDocument && !publishedDocument.isPending()) {
			status = super.canUnpublish(publishedDocument);
		}

		return status;
	}

	/**
	 * 
	 * @param validStates
	 *            valid states
	 * @return true if all documents are in this state else false
	 */
	public boolean checkDocumentLifeCycle(String validStates) {
		boolean res = false;
		String currentLifeCycleState = null;
		List<DocumentModel> currentDocumentSelection = documentsListsManager.getWorkingList(CURRENT_DOCUMENT_SELECTION);
		try {
			for (DocumentModel documentModel : currentDocumentSelection) {

				currentLifeCycleState = documentModel.getCurrentLifeCycleState();
				res = validStates.contains(currentLifeCycleState);
				if (!res) {
					break;
				}
			}

		} catch (ClientException e) {
			log.error("Failed to check currentLifeCycleState, error: " + e.getMessage());
		}
		return res;
	}

	public String getParentFormattedPath(DocumentModel document) throws ClientException {
		String path = "";

		DocumentModel parentModel = getDocumentModelForParent(document);
		if (null != parentModel) {
			path = super.getFormattedPath(parentModel);
		}

		return path;
	}

	public DocumentModel getDocumentModelForParent(DocumentModel document) throws ClientException {
		DocumentRef parentRef = document.getParentRef();
		if (null != parentRef) {
			return documentManager.getDocument(parentRef);
		}
		return null;
	}

	public void unpublishFromProxy(DocumentModel proxy) throws ClientException {
		List<DocumentModel> list = new ArrayList<DocumentModel>();
		list.add(proxy);
		super.unpublish(list);
	}

	/**
	 * Pour evaluer le statut du document dans la section de publication et
	 * utiliser ou non l'action publier/rejeter
	 * 
	 * @return
	 * @throws ClientException
	 */
	public boolean getIsWaitingForPublication() throws ClientException {
		boolean isWaitingForPublication = false;

		try {
			List<DocumentModel> selectedDocuments = documentsListsManager.getWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION);

			if (!(selectedDocuments == null || selectedDocuments.isEmpty())) {
				for (DocumentModel document : selectedDocuments) {
					if (!document.hasFacet(FacetNames.FOLDERISH)) {
						isWaitingForPublication = true;

						PublishedDocument publishedDocument = getPublishedDocumentModel(document);
						PublicationTree tree = getPublicationTreeFor(document);
						boolean canManagePublishing = tree.canManagePublishing(publishedDocument);
						isWaitingForPublication = publishedDocument.isPending() && canManagePublishing;
						if (false == isWaitingForPublication) {
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Failed to check if the document is waiting for publication, error: " + e.getMessage());
		}

		return isWaitingForPublication;
	}

	/**
	 * Pour evaluer le statut du document dans la section de publication et
	 * utiliser ou non l'action publier/rejeter
	 * 
	 * @return
	 * @throws ClientException
	 */
	public boolean getCanUnpublishSectionSelection() throws ClientException {
		boolean status = false;

		List<DocumentModel> selectedDocuments = documentsListsManager.getWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION);
		if (!(selectedDocuments == null || selectedDocuments.isEmpty())) {
			status = true;

			for (DocumentModel document : selectedDocuments) {
				if (isPending(document)) {
					status = false;
					break;
				}
			}
		}

		return status;
	}

	public void approveDocumentsFromCurrentSelection() throws ClientException {
		List<DocumentModel> selectedDocuments = documentsListsManager.getWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION);
		if (!(selectedDocuments == null || selectedDocuments.isEmpty())) {
			for (DocumentModel document : selectedDocuments) {
				approveDocument(document);
			}

			refreshUI();
		}
	}

	public void rejectDocumentsFromCurrentSelection() throws ClientException {
		List<DocumentModel> selectedDocuments = documentsListsManager.getWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION);
		if (!(selectedDocuments == null || selectedDocuments.isEmpty())) {
			this.publishingComment = CST_DEFAULT_DOCUMENT_SELECTION_REJECT_COMMENT;
			for (DocumentModel document : selectedDocuments) {
				rejectDocument(document);
			}

			refreshUI();
		}
	}

	@Override
	public String approveDocument() throws ClientException {
		DocumentModel currentDocument = navigationContext.getCurrentDocument();
		return approveDocument(currentDocument);
	}

	@Override
	public String rejectDocument() throws ClientException {
		DocumentModel currentDocument = navigationContext.getCurrentDocument();
		return rejectDocument(currentDocument);
	}

	public String approveDocument(DocumentModel document) throws ClientException {
		PublicationTree tree = getPublicationTreeFor(document);
		PublishedDocument publishedDocument = tree.wrapToPublishedDocument(document);

		/*
		 * récupérer le document éventuellement déjà publié (qui sera remplacé
		 * lors de l'acceptation de la publication du document courant, dans
		 * l'état 'pending')
		 */
		String formerProxyName = null;
		DocumentModelList proxies = documentManager.getProxies(document.getRef(), document.getParentRef());
		if (proxies != null) {
			for (DocumentModel p : proxies) {
				PublishedDocument pd = tree.wrapToPublishedDocument(p);
				if (!pd.isPending()) {
					formerProxyName = p.getName();
				}
			}
		}

		// valider la publication
		tree.validatorPublishDocument(publishedDocument, publishingComment);

		// renommer le proxy (mise à jour de la propriété système "ecm:name")
		// pour les besoins de la conservation d'URL
		String newProxyName = formerProxyName;
		if (StringUtils.isBlank(newProxyName)) {
			DocumentRef sdRef = ((SimpleCorePublishedDocument) publishedDocument).getSourceDocumentRef();
			DocumentModel dm = documentManager.getSourceDocument(sdRef);
//			newProxyName = dm.getName() + GlobalConst.CST_REMOTE_PROXY_NAME_SUFFIX;
			newProxyName = dm.getName();
		}

		if (!document.getName().equals(newProxyName)) {
			documentManager.move(document.getRef(), document.getParentRef(), newProxyName);
		}

		/*
		 * Il est nécessaire d'appeler la méthode "navigateToId" pour
		 * réinitialiser le context de navigation car le gestionnaire d'URL
		 * utilise un codec qui se base sur le path du document et non son id
		 * pour gérer les vues. Puisque le path a changé (renommage du doc avec
		 * "ecm:name"), si rien n'est fait, une exception du type
		 * "NoSuchDocumentException" sera levée.
		 */
		if (!navigationContext.getCurrentDocument().isFolder())
			navigationContext.navigateToId(document.getId());

		// notifer
		try {
			FacesContext context = FacesContext.getCurrentInstance();
			String comment = publishingComment != null && publishingComment.length() > 0 ? ComponentUtils.translate(context, "publishing.approved.with.comment",
					publishedDocument.getParentPath(), tree.getConfigName(), publishingComment) : ComponentUtils.translate(context, "publishing.approved.without.comment",
					publishedDocument.getParentPath(), tree.getConfigName());

			ApproverWithoutRestriction approver = new ApproverWithoutRestriction(publishedDocument, comment, documentManager);
			if (documentManager.hasPermission(publishedDocument.getSourceDocumentRef(), SecurityConstants.WRITE)) {
				approver.run();
			} else {
				approver.runUnrestricted();
			}
		} catch (Exception e) {
			log.warn("Faied to approve the document '" + document.getTitle() + "'. It may point to a no more existing source document, error: " + e.getMessage());
		}

		Events.instance().raiseEvent(EventNames.DOCUMENT_PUBLISHED);
		Events.instance().raiseEvent(EventNames.DOCUMENT_PUBLICATION_APPROVED);
		return null;
	}

	/**
	 * Refreshes and resets content views that have declared event on
	 * publication {@link PublishingEvent} as a refresh/reset event.
	 */
	@Observer(value = { "documentWaitingPublication", "documentPublicationApproved", "documentPublicationRejected", "documentPublished", "documentUnpublished" })
	public void onDocumentChanged() {
		contentViewActions.refreshOnSeamEvent("documentPublicationEvent");
		contentViewActions.resetPageProviderOnSeamEvent("documentPublicationEvent");
	}

	public String rejectDocument(DocumentModel document) throws ClientException {
		if (StringUtils.isBlank(publishingComment)) {
			facesMessages.addToControl("publishingComment", StatusMessage.Severity.ERROR, messages.get("label.publishing.reject.user.comment.mandatory"));
			return null;
		}

		try {
			PublicationTree tree = getPublicationTreeFor(document);
			PublishedDocument publishedDocument = tree.wrapToPublishedDocument(document);
			tree.validatorRejectPublication(publishedDocument, publishingComment);

			FacesContext context = FacesContext.getCurrentInstance();
			String comment = publishingComment != null && publishingComment.length() > 0 ? ComponentUtils.translate(context, "publishing.rejected.with.comment",
					publishedDocument.getParentPath(), tree.getConfigName(), publishingComment) : ComponentUtils.translate(context, "publishing.rejected.without.comment",
					publishedDocument.getParentPath(), tree.getConfigName());
			RejectWithoutRestrictionRunner runner = new RejectWithoutRestrictionRunner(documentManager, publishedDocument, comment);

			if (documentManager.hasPermission(publishedDocument.getSourceDocumentRef(), SecurityConstants.READ)) {
				runner.run();
			} else {
				runner.runUnrestricted();
			}
		} catch (Exception e) {
			log.warn("Faied to reject the document '" + document.getTitle() + "'. It may point to a no more existing source document, error: " + e.getMessage());
		}

		Events.instance().raiseEvent(EventNames.DOCUMENT_PUBLICATION_REJECTED);
		return navigationContext.navigateToRef(document.getParentRef());
	}

	@Override
	public boolean isPending() throws ClientException {
		return isPending(navigationContext.getCurrentDocument());
	}

	public boolean isPending(DocumentModel document) throws ClientException {
		PublishedDocument publishedDocument = getPublishedDocumentModel(document);
		return (null != publishedDocument) ? publishedDocument.isPending() : false;
	}

	public void unPublish(DocumentModel document) throws ClientException {
		if (isPending(document)) {
			setPublishingComment(CST_DEFAULT_DOCUMENT_SELECTION_REJECT_COMMENT);
			rejectDocument(document);
		} else {
			unPublish(getPublishedDocumentModel(document));
		}
	}
	
	/**
	 * 
	 * @return true si au moins un des documents sélectionnés est publié
	 * @throws ClientException
	 */
	public boolean isAtLeastOnePublished() throws ClientException {
		boolean status = false;
		
		if (mapIsPublishedDoc == null) {
			mapIsPublishedDoc = new HashMap<String, Boolean>();
		}
		List<DocumentModel> docsList = documentsListsManager.getWorkingList(CURRENT_DOCUMENT_SELECTION);
		if (null != docsList && 0 < docsList.size()) {

			for (DocumentModel doc : docsList) {
				if (!mapIsPublishedDoc.containsKey(doc.getId())) {
					// recuperation des sections(ou espaces) de publication
					RootSectionFinder rsf = publisherService.getRootSectionFinder(documentManager);
					DocumentModelList lstEspPublication = ((ToutaticeRemoteRootSectionsFinder) rsf).getSectionRootsForWorkspace(doc, true,
							ToutaticeRemoteRootSectionsFinder.ROOT_SECTION_TYPE.valueOf("ALL"));

					// s'il n'y a des sections non vide => le document est
					// publié
					for (DocumentModel section : lstEspPublication) {
						status = (findPublishedDocInSection(section, doc)!=null);
						mapIsPublishedDoc.put(doc.getId(),status);
						if (status) {
							break;
						}
					}										
				}else if(mapIsPublishedDoc.get(doc.getId())){
					status = true;
					break;
				}
			}
		}
		return status;
	}

	/**
	 * Sur-définition pour filtrer les documents publiés "en local" dans le
	 * cadre de la publication "interne" (pour la visibilité des documents sur
	 * le portail)
	 * 
	 * @see org.nuxeo.ecm.platform.publisher.web.PublishActionsBean#getPublishedDocumentsFor(java.lang.String)
	 */
	@Override
	public List<PublishedDocument> getPublishedDocumentsFor(String treeName) throws ClientException {
		List<PublishedDocument> filteredList = new ArrayList<PublishedDocument>();

		try {
			if (null == this.publishedDocsOfTreeMap) {
				this.publishedDocsOfTreeMap = new HashMap<String, List<PublishedDocument>>();
			}

			if (!this.publishedDocsOfTreeMap.containsKey(treeName)) {
				/*
				 * Initialiser le cache avec la liste vide: Permet de ne pas
				 * relancer une recherche des documents publiés dans un arbre et
				 * donc de ne pas rejouer les requêtes SQL en base. Gain de
				 * performance.
				 */
				DocumentModel currentDoc = this.navigationContext.getCurrentDocument();
				filteredList = findPublishedDocsOfTree(treeName, currentDoc);
				
				this.publishedDocsOfTreeMap.put(treeName, filteredList);
			}
		} catch (Exception e) {
			log.error("Failed to get the published documents for the tree name '" + treeName + "', error: " + e.getMessage());
		}

		return this.publishedDocsOfTreeMap.get(treeName);
	}

	private List<PublishedDocument> findPublishedDocsOfTree(String treeName, DocumentModel doc) throws ClientException {
		List<PublishedDocument> filteredList = new ArrayList<PublishedDocument>();
		DocumentModel docSrc = this.documentManager.getDocument(new IdRef(doc.getSourceId()));
		String sourceDocParentPath = docSrc.getPath().removeLastSegments(1).toString();

		List<PublishedDocument> rawList = getPublishedDocumentsFor(treeName, doc);
		for (PublishedDocument publishedDoc : rawList) {
			String publishedDocParentPath = publishedDoc.getParentPath();

			if (!publishedDocParentPath.equals(sourceDocParentPath)) {
				filteredList.add(publishedDoc);
			}
		}
		return filteredList;
	}	

	private PublishedDocument getPublishedDocumentModel(DocumentModel document) {
    	/**
    	 * Ticket Redmine http://redmine.toutatice.fr/issues/1766
    	 * Seuls des documents de type proxy peuvent être concernés. 
    	 */
		if (!document.isProxy()) {
			return null;
		}
		
		if (null == publishedDocModelMap) {
			publishedDocModelMap = new HashMap<String, PublishedDocument>();
		}

		try {
			if (null == publishedDocModelMap.get(document.getId())) {
				PublicationTree tree = getPublicationTreeFor(document);
				PublishedDocument publishedDocument = tree.wrapToPublishedDocument(document);
				if (null != publishedDocument) {
					publishedDocModelMap.put(document.getId(), publishedDocument);
				}
			}
		} catch (Exception e) {
			log.error("Failed to get the published document model of the document '" + document.getName() + "', error: " + e.getMessage());
		}

		return publishedDocModelMap.get(document.getId());
	}

	public void refreshUI() throws ClientException {
		// refresh the section content view that lists the published documents
		// into the section
		documentsListsManager.resetWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION);
		Events.instance().raiseEvent(EventNames.DOCUMENT_CHILDREN_CHANGED);
	}

	/**
	 * Methodes pour encapsuler les appels au service Publisher (caching)
	 * 
	 */

	private PublicationTree getPublicationTreeFor(DocumentModel document) throws ClientException {
		if (null == PublicationTreeMap) {
			PublicationTreeMap = new HashMap<String, PublicationTree>();
		}

		if (null == PublicationTreeMap.get(document.getId())) {
			PublicationTree tree = publisherService.getPublicationTreeFor(document, documentManager);
			PublicationTreeMap.put(document.getId(), tree);
		}

		return PublicationTreeMap.get(document.getId());
	}

	@Override
	public boolean canManagePublishing() throws ClientException {
		if (null == canManagePublishingMap) {
			canManagePublishingMap = new HashMap<String, Boolean>();
		}

		DocumentModel document = navigationContext.getCurrentDocument();
		if (null == canManagePublishingMap.get(document.getId())) {
			boolean status = super.canManagePublishing();
			canManagePublishingMap.put(document.getId(), status);
		}

		return canManagePublishingMap.get(document.getId());
	}

	@Override
	public boolean hasValidationTask() throws ClientException {
		if (null == hasValidationTaskMap) {
			hasValidationTaskMap = new HashMap<String, Boolean>();
		}

		DocumentModel document = navigationContext.getCurrentDocument();
		if (null == hasValidationTaskMap.get(document.getId())) {
			boolean status = super.hasValidationTask();
			hasValidationTaskMap.put(document.getId(), status);
		}

		return hasValidationTaskMap.get(document.getId());
	}

	private PublicationNode wrapToPublicationNode(DocumentModel document) throws ClientException {
		if (null == PublicationNodeMap) {
			PublicationNodeMap = new HashMap<String, PublicationNode>();
		}

		if (null == PublicationNodeMap.get(document.getId())) {
			PublicationNode node = publisherService.wrapToPublicationNode(document, documentManager);
			PublicationNodeMap.put(document.getId(), node);
		}

		return PublicationNodeMap.get(document.getId());
	}

	private class RejectWithoutRestrictionRunner extends UnrestrictedSessionRunner {

		PublishedDocument publishedDocument;
		DocumentModel sourceDocument;
		String comment;
		DocumentModel liveVersion;

		public RejectWithoutRestrictionRunner(CoreSession session, PublishedDocument publishedDocument, String comment) {
			super(session);
			this.publishedDocument = publishedDocument;
			this.comment = comment;
		}

		@Override
		public void run() throws ClientException {
			sourceDocument = session.getDocument(publishedDocument.getSourceDocumentRef());
			liveVersion = session.getDocument(new IdRef(sourceDocument.getSourceId()));
			notifyRejectToSourceDocument();
		}

		private void notifyRejectToSourceDocument() throws ClientException {
			notifyEvent(PublishingEvent.documentPublicationRejected.name(), null, comment, null, sourceDocument);
			if (!sourceDocument.getRef().equals(liveVersion.getRef())) {
				notifyEvent(PublishingEvent.documentPublicationRejected.name(), null, comment, null, liveVersion);
			}
		}
	}
	
	
	/**
	 * retrouve la premier section de publication et vérifie que le document peut-être publié sans contrôle sur la validation.
	 * @return 
	 * @throws ClientException
	 */
	public boolean getCanPublishToFirstSection() throws ClientException{
		DocumentModel section = null;
		DocumentModel doc = navigationContext.getCurrentDocument();
		PublisherService ps = Framework.getLocalService(PublisherService.class);
		
		section = ToutaticePublishHelper.getFirstSection(doc,ps,documentManager);
		
		boolean status = false;
		
		PublishedDocument publishedDoc = getPublishedDoc(section);
		if (null == publishedDoc) {
			status = true;
		} else {
			DocumentRef srcDocRef = publishedDoc.getSourceDocumentRef();
			DocumentModel publishedDocModel = documentManager.getDocument(srcDocRef);

			if (ToutaticeDocumentHelper.DocumentVersionComparator.isNewer(publishedDocModel, doc)) {
				status = true;
			}
		}		
		
		return status;
	}

}
