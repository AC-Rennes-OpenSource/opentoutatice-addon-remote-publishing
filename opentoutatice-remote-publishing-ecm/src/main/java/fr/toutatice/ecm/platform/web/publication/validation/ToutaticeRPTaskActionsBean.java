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

import java.util.List;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.publisher.task.CoreProxyWithWorkflowFactory;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.ui.web.invalidations.AutomaticDocumentBasedInvalidation;

import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;
import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.helper.ToutaticeWorkflowHelper;
import fr.toutatice.ecm.platform.web.workflows.ToutaticeTaskActionsBean;

/**
 * @author David Chevrier
 */
@Name("taskActions")
@Scope(ScopeType.CONVERSATION)
@AutomaticDocumentBasedInvalidation
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class ToutaticeRPTaskActionsBean extends ToutaticeTaskActionsBean {

    private static final long serialVersionUID = 3730629195809387779L;
    
    public boolean isCoreProxyWorkflowPending() throws NuxeoException{
        List<Task> currentDocumentTasks = super.getCurrentDocumentTasks();
        for(Task task : currentDocumentTasks){
           if(CoreProxyWithWorkflowFactory.PUBLISH_TASK_TYPE.equals(task.getType())){
               return ((Boolean) task.isOpened()).booleanValue();
           }
        }
        return false;
    }
    
    public String getValidateTaskName() {
        return ToutaticeRPConstants.CST_WORKFLOW_TASK_VALIDATION_VALIDATE;
    }

    public Task getValidateTask() throws NuxeoException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        return ToutaticeWorkflowHelper.getTaskByName(ToutaticeRPConstants.CST_WORKFLOW_TASK_VALIDATION_VALIDATE, documentManager, currentDocument);
    }
    
    public boolean isValidateTask(Task task) throws NuxeoException {
        if (task != null) {
            return ToutaticeRPConstants.CST_WORKFLOW_TASK_VALIDATION_VALIDATE.equals(task.getName());
        }
        return false;
    }
    
    public boolean isValidateActionAuthorized() throws NuxeoException {
        return isTaskActionAuthorized(ToutaticeRPConstants.CST_WORKFLOW_TASK_VALIDATION_VALIDATE);
    }

}
