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
 * 
 */
package fr.toutatice.ecm.platform.web.publication.validation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;

import fr.toutatice.ecm.platform.web.publication.ToutaticeCoreProxyWithWorkflowFactory;

public class ToutaticeRPCoreProxyWithWorkflowFactory extends ToutaticeCoreProxyWithWorkflowFactory {

    private static final Log log = LogFactory.getLog(ToutaticeRPCoreProxyWithWorkflowFactory.class);

    @Override
    protected boolean isPublished(PublishedDocument publishedDocument, CoreSession session) throws NuxeoException {
        boolean status = false;

        DocumentModel proxy = ((SimpleCorePublishedDocument) publishedDocument).getProxy();
        try {
            status = !isPublishedDocWaitingForPublication(proxy, session);
        } catch (Exception e) {
            log.error("Failed to get the published status from the document '" + proxy.getName() + "', error: " + e.getMessage());
        }

        return status;
    }

    @Override
    protected boolean hasValidationTask(DocumentModel proxy, NuxeoPrincipal currentUser) throws NuxeoException {
        boolean isValidator = isValidator(proxy, currentUser);
        boolean isDocWaitingForPublication = isPublishedDocWaitingForPublication(proxy, coreSession);
        return isDocWaitingForPublication && isValidator;
    }

}
