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
package fr.toutatice.ecm.platform.constants;

public interface ToutaticeRPConstants {
    
    String CST_REMOTE_PROXY_PENDING_NAME_SUFFIX = ".pending";
    String CST_AUDIT_EVENT_PROPAGATE_PROCESS_COMPLETED = "propagateProcessCompleted";
    String CST_RP_AUDDIT_EVENT_UNPUBLISH_SELECTION_PROCESS_COMPLETED = "UnpublishSelectionCompleted";
    
    String CST_WORKFLOW_PROCESS_LEGACY_VALIDATION_APPROBATION = "review_approbation";
    String CST_WORKFLOW_PROCESS_VALIDATION_APPROBATION = "acaren_validation_approbation";
    String CST_WORKFLOW_PROCESS_VALIDATION_PARALLEL = "review_parallel";
    String CST_WORKFLOW_TASK_LEGACY_VALIDATE = "validate";
    String CST_WORKFLOW_TASK_LEGACY_REJECT = "reject";
    String CST_WORKFLOW_TASK_VALIDATE = "validate_validation";
    String CST_WORKFLOW_TASK_REJECT = "reject_validation";
    String CST_WORKFLOW_TASK_CHOOSE_PARTICIPANT = "choose-participant";
	String CST_PROXY_REMOTE_LEGACY_NAME_SUFFIX = ".remote.proxy";
	
	String CST_WORKFLOW_VALIDATION = "toutatice_validation";
	String CST_WORKFLOW_TASK_VALIDATION_VALIDATE = "validate-validation";
    
}
