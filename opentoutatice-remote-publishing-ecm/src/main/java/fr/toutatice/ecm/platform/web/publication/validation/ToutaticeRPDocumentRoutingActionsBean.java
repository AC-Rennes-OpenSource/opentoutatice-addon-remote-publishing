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
 * dchevrier
 * lbillon
 */
package fr.toutatice.ecm.platform.web.publication.validation;

import static org.jboss.seam.ScopeType.CONVERSATION;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.task.Task;

import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;
import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.web.workflows.ToutaticeDocumentRoutingActionsBean;

/**
 * @author David Chevrier
 * 
 */
@Name("routingActions")
@Scope(CONVERSATION)
@Install(precedence = ExtendedSeamPrecedence.INHERIT_ADD_ON)
public class ToutaticeRPDocumentRoutingActionsBean extends ToutaticeDocumentRoutingActionsBean {

    private static final long serialVersionUID = 8176244997123301627L;

    private static final Log log = LogFactory.getLog(ToutaticeRPDocumentRoutingActionsBean.class);

    public String startValidationWorkflow() throws ClientException {
        DocumentModel validationWf = getValidationWorkflowModel();
        return startWorkflow(validationWf, "toutatice.label.validation.wf.started");
    }

    private DocumentModel getValidationWorkflowModel() throws ClientException {
        String id = getDocumentRoutingService().getRouteModelDocIdWithId(documentManager, ToutaticeRPConstants.CST_WORKFLOW_VALIDATION);
        return getRouteModel(id);
    }

    public String cancelValidationWorkflow() throws ClientException {
        return super.cancelWorkflow(ToutaticeRPConstants.CST_WORKFLOW_VALIDATION);
    }

    public boolean isCancelValidationActionAuthorized() throws ClientException {
        boolean doWorkflowExist = false;
        boolean isUserInitiator = false;

        try {
            doWorkflowExist = isValidationWorkflowRunning();
            NuxeoPrincipal principal = currentUser;
            isUserInitiator = principal.getName().equals(getCurrentWorkflowInitiator());
        } catch (Exception e) {
            log.debug("Failed to execute 'isCancelValidationActionAuthorized()', error: " + e.getMessage());
        }

        return doWorkflowExist && isUserInitiator;
    }

    public boolean isValidationWorkflowRunning() {
        return getValidationWorkflow() != null;
    }

    public DocumentRoute getValidationWorkflow() {
        List<DocumentRoute> routes = getRelatedRoutes();
        return getRunningWorkflowByName(routes, ToutaticeRPConstants.CST_WORKFLOW_VALIDATION);
    }

    @Override
    public Task getValidateTask(String wfName) throws ClientException {
        Task validate = null;

        String taskName = StringUtils.EMPTY;
        if (ToutaticeGlobalConst.CST_WORKFLOW_PROCESS_ONLINE.equals(wfName)) {
            taskName = ToutaticeGlobalConst.CST_WORKFLOW_TASK_ONLINE_VALIDATE;
        } else if(ToutaticeRPConstants.CST_WORKFLOW_VALIDATION.equals(wfName)){
            taskName = ToutaticeRPConstants.CST_WORKFLOW_TASK_VALIDATION_VALIDATE;
        }

        if (StringUtils.isNotBlank(taskName)) {
            List<Task> currentRouteAllTasks = getCurrentRouteAllTasks();
            Iterator<Task> iterator = currentRouteAllTasks.iterator();
            while (iterator.hasNext() && validate == null) {
                Task task = iterator.next();
                if (taskName.equalsIgnoreCase(task.getName())) {
                    validate = task;
                }
            }
        }
        
        return validate;
    }
}
