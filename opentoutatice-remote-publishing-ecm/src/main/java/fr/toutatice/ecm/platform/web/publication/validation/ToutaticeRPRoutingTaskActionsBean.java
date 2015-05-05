package fr.toutatice.ecm.platform.web.publication.validation;

import static org.jboss.seam.ScopeType.CONVERSATION;

import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskImpl;
import org.nuxeo.ecm.webapp.action.MainTabsActions;

import fr.toutatice.ecm.platform.constants.ExtendedSeamPrecedence;
import fr.toutatice.ecm.platform.constants.ToutaticeRPConstants;
import fr.toutatice.ecm.platform.core.constants.ToutaticeGlobalConst;
import fr.toutatice.ecm.platform.core.helper.ToutaticeWorkflowHelper;
import fr.toutatice.ecm.platform.web.workflows.ToutaticeRoutingTaskActionsBean;


@Scope(CONVERSATION)
@Name("routingTaskActions")
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class ToutaticeRPRoutingTaskActionsBean extends ToutaticeRoutingTaskActionsBean {
		
	
	@Override
	public String endTask(Task task) throws ClientException {
		DocumentModel currentDoc = navigationContext.getCurrentDocument();
		Task taskForNotif = new TaskImpl(task.getDocument());
		String wfInitiator = getWorkFlowInitiator();
		//#3071 notification
		String eventName;
		if(isAcceptOnLineButtonCliked()){
			eventName = ToutaticeGlobalConst.CST_EVENT_ONLINE_TASK_APPROVED;
		}else if( ToutaticeRPConstants.CST_WORKFLOW_BUTTON_VALIDATION_ACCEPT.equalsIgnoreCase(super.button)){
			eventName = ToutaticeRPConstants.CST_EVENT_VALIDATION_TASK_APPROVED;
		}else if(ToutaticeRPConstants.CST_WORKFLOW_BUTTON_VALIDATION_REJECT.equalsIgnoreCase(super.button)){
			eventName = ToutaticeRPConstants.CST_EVENT_VALIDATION_TASK_REJECTED;
		}else{
			eventName =ToutaticeGlobalConst.CST_EVENT_ONLINE_TASK_REJECTED;
		}	
				
		super.endTask(task);
		
		ToutaticeWorkflowHelper.notifyRecipients(documentManager, taskForNotif,
				currentDoc, wfInitiator,
				eventName);

		
		return MainTabsActions.DEFAULT_VIEW;
	}

}
