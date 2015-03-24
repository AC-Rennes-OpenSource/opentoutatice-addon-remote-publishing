<HTML>
<BODY>
<#if eventId == "workflowReviewTaskAssigned">
Une demande de validation du document '${htmlEscape(docTitle)}' a &eacute;t&eacute; &eacute;mise par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<#elseif eventId == "workflowReviewTaskApproved">
Le document '${htmlEscape(docTitle)}' a &eacute;t&eacute; valid&eacute; par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<#elseif eventId == "workflowReviewTaskRejected">
La demande de validation du document '${htmlEscape(docTitle)}' a &eacute;t&eacute; rejet&eacute;e par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<#elseif eventId == "workflowReviewTaskCanceled">
La demande de validation du document '${htmlEscape(docTitle)}' a &eacute;t&eacute; annul&eacute;e par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
</#if>
<BR>
Vous &ecirc;tes invit&eacute;(e) &agrave; le consulter &agrave; l'adresse suivante: <a href="${docUrl}">${htmlEscape(docTitle)}</a></P>
</BODY>
<HTML>