package fr.toutatice.ecm.platform.web.publication.validation;

import static org.jboss.seam.ScopeType.CONVERSATION;

import java.util.ArrayList;
import java.util.List;

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
import fr.toutatice.ecm.platform.core.helper.ToutaticeWorkflowHelper;
import fr.toutatice.ecm.platform.web.workflows.ToutaticeRoutingTaskActionsBean;

@Scope(CONVERSATION)
@Name("routingTaskActions")
@Install(precedence = ExtendedSeamPrecedence.ADD_ON)
public class ToutaticeRPRoutingTaskActionsBean extends ToutaticeRoutingTaskActionsBean {

	private static final long serialVersionUID = 7828257388908697920L;
	private static final List<String> TOUTATICE_RP_WF_ACTIONS = new ArrayList<String>() {
		private static final long serialVersionUID = -7017362685680104930L;

		{
			add(ToutaticeRPConstants.CST_WORKFLOW_BUTTON_VALIDATION_ACCEPT);
			add(ToutaticeRPConstants.CST_WORKFLOW_BUTTON_VALIDATION_REJECT);
		}
	};

	@Override
	public String endTask(Task task) throws ClientException {
		String view = MainTabsActions.DEFAULT_VIEW;
		
		if (TOUTATICE_RP_WF_ACTIONS.contains(getClickedButton())) {
			DocumentModel currentDoc = navigationContext.getCurrentDocument();
			Task taskForNotif = new TaskImpl(task.getDocument());
			String wfInitiator = getWorkFlowInitiator();
			String eventName;
			
			if (ToutaticeRPConstants.CST_WORKFLOW_BUTTON_VALIDATION_ACCEPT.equalsIgnoreCase(getClickedButton())) {
				eventName = ToutaticeRPConstants.CST_EVENT_VALIDATION_TASK_APPROVED;
			} else {
				eventName = ToutaticeRPConstants.CST_EVENT_VALIDATION_TASK_REJECTED;
			}	
			
			super.endTask(task);
			
			ToutaticeWorkflowHelper.notifyRecipients(documentManager, taskForNotif, currentDoc, wfInitiator, eventName);
		} else {
			//no-op. only forward processing to mother class
			view = super.endTask(task);
		}
        
        return view;
	}

}
