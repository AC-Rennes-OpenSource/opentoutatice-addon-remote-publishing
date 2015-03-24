<HTML>
<BODY>
<#if eventId =="documentWaitingPublication">
Une demande de publication du document '${htmlEscape(docTitle)}' vous a &eacute;t&eacute; assign&eacute;e par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<#elseif eventId == "documentPublished">
Un nouveau document a &eacute;t&eacute; publi&eacute; par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<#elseif eventId == "documentPublicationApproved">
Le document <a href="${docUrl}">${htmlEscape(docTitle)}</a> a été publié par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<BR>Commentaire: ${comment}
<#elseif eventId == "documentPublicationRejected">
La demande de publication du document <a href="${docUrl}">${htmlEscape(docTitle)}</a> a &eacute;t&eacute; rejet&eacute;e par ${author} le ${dateTime?datetime?string("dd/MM/yyyy - HH:mm")}.
<BR>Commentaire: ${comment}
</#if>
<BR>
<#if eventId == "documentPublished" || eventId == "documentPublicationApproved">
Vous &ecirc;tes invit&eacute;(e) &agrave; consulter ce document
<#if docPermalink!=""> dans toutatice : <a href="${docPermalink}">${htmlEscape(docTitle)}</a>.
<br/><i>
( vous pouvez y accéder directement 
</#if>
dans nuxeo en cliquant <a href="${docUrl}">ici</a>.
<#if docPermalink!=""> ) </i> </#if>
<#else>
Vous &ecirc;tes invit&eacute;(e) &agrave; consulter ce document &agrave; l'adresse suivante: <a href="${docUrl}">${htmlEscape(docTitle)}</a>.
</#if>
</BODY>
<HTML>