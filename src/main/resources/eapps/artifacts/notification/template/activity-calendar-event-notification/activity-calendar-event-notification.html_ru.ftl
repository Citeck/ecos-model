<p>${body}</p>

<p><#if eventDate??>${eventDate}</#if> <#if eventTime??>${eventTime}</#if></p>

<#if docRef?? && !(docRef?contains("emodel/workspace"))>
    <p><a href="${link.getRecordLink(docRef)}" target="_blank">${eventDisp}</a></p>
</#if>
