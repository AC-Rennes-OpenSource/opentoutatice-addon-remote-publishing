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
package fr.toutatice.ecm.platform.operation;

import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.impl.VersionModelImpl;
import org.nuxeo.ecm.platform.publisher.api.PublicationTree;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.api.PublisherService;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;
import org.nuxeo.runtime.api.Framework;

import fr.toutatice.ecm.acrennes.publishing.AcrennesPublishedDocumentWithWorkflowFactory;
import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.constants.ToutaticeNuxeoStudioConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeSilentProcessRunnerHelper;
import fr.toutatice.ecm.platform.helper.ToutaticePublishHelper;

@Operation(id = RemotePublishDocument.ID, category = Constants.CAT_DOCUMENT, label = "Simple remote publication", description = "Publish the input document into the first target section find. Existing proxy is overrided if the override attribute is set. Return the created proxy.")
public class RemotePublishDocument {
	public static final String ID = "Document.RemotePublishDocument";
	
	private static final Log log = LogFactory.getLog(RemotePublishDocument.class);

	@Context	
	protected CoreSession session;

	@Param(name = "transition", required = false)
	protected String transition;

	@Param(name = "override", required = false, values = "true")
	protected boolean override = true;

	@OperationMethod(collector = DocumentModelCollector.class)
	public DocumentModel run(DocumentModel doc) throws Exception {

		DocumentModel target = null;
		String formerProxyName = null;
		PublishedDocument publishedDocument =null;
		PublisherService ps = Framework.getLocalService(PublisherService.class);

		// récupération du permier espace de publication définit pour le document
		target= ToutaticePublishHelper.getFirstSection(doc,ps,session);
		
		DocumentRef targetRef = target.getRef();
		DocumentRef baseDocRef = doc.getRef();

	
		 /* 
		  * gestion du cycle de vie du document à publier validation du document
		  * automatique la transition est passé en paramêtre
		 */
		if (!doc.isVersion()) {
			log.debug("document en projet le passé à l'état validé ");
			// si il n'y a pas de transition défini alors le document doit être
			// valider manuellement avant la publication
			if (StringUtils.isBlank(transition)) {
				throw new ClientException("Veuillez valider votre document ");
			}
			// si le document est en projet: le valider
			if (ToutaticeNuxeoStudioConst.CST_DOC_STATE_PROJECT.equals(doc.getCurrentLifeCycleState())) {
				doc.setPropertyValue("dc:valid", new Date());
				this.session.saveDocument(doc);
				this.session.followTransition(doc.getRef(), transition);
				doc.refresh(DocumentModel.REFRESH_STATE, null);
			}

			// si le document possède une version/archive 'en projet' (ex: s'il
			// a été enregistré suite à une modification avec une montée de version): le valider
			if (!doc.isCheckedOut()) {
				String label = doc.getVersionLabel();
				VersionModelImpl vm = new VersionModelImpl();
				vm.setLabel(label);
				DocumentModel vdoc = this.session.getDocumentWithVersion(doc.getRef(), vm);
				if (null != vdoc && ToutaticeNuxeoStudioConst.CST_DOC_STATE_PROJECT.equals(vdoc.getCurrentLifeCycleState())) {
					this.session.followTransition(vdoc.getRef(), transition);
				}
			}
		}

		
		if (null != targetRef) {
			/* conservation d'URL: récupérer le nom courant du proxy */
			
			if (doc.isVersion()) {
				String sourceDocId = doc.getSourceId();
				baseDocRef = new IdRef(sourceDocId);
			}
			PublicationTree tree = ToutaticePublishHelper.getCurrentPublicationTreeForPublishing(doc,ps,this.session);

			DocumentModelList proxies = this.session.getProxies(baseDocRef, targetRef);
			for (DocumentModel proxy : proxies) {
				PublishedDocument pd = tree.wrapToPublishedDocument(proxy);
				if (!pd.isPending()) {
					formerProxyName = proxy.getName();
				}
			}
			
            // Publish
            publishedDocument = AcrennesPublishedDocumentWithWorkflowFactory.getInstance().publish(session, doc, target);
			

		} else {
			throw new ClientException("Failed to get the target document reference");
		}
		log.debug("debut de publication ");
        InnerSilentPublish runner = new InnerSilentPublish(session, doc, publishedDocument, target);
		runner.silentRun(true);
		DocumentModel proxy = runner.getProxy();
		log.debug("fin de publication ");
		return proxy;
	}

	private class InnerSilentPublish extends ToutaticeSilentProcessRunnerHelper {

		private DocumentModel doc;
		private DocumentModel newProxy;
		private DocumentModel target;

		private PublishedDocument publishedDocument;

		public DocumentModel getProxy() {
			return this.newProxy;
		}

        public InnerSilentPublish(CoreSession session, DocumentModel doc, PublishedDocument publishedDocument, DocumentModel target) {
			super(session);
			this.doc = doc;
			this.target = target;
			this.publishedDocument = publishedDocument;
		}

		@Override
		public void run() throws ClientException {

			this.newProxy = null;

			String newProxyName = null;
			if (publishedDocument.isPending()) {
				// publication avec workflow d'approbation
				newProxyName = doc.getName() + ToutaticeRPConstants.CST_REMOTE_PROXY_PENDING_NAME_SUFFIX;
			} 

			this.newProxy = ((SimpleCorePublishedDocument) publishedDocument).getProxy();
			if (!this.newProxy.getName().equals(newProxyName)) {
				this.session.move(this.newProxy.getRef(), target.getRef(), newProxyName);
			}
		}

	}
}
