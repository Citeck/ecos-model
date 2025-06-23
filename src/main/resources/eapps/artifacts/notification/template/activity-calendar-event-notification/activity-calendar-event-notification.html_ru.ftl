<p>${body}</p>

<#if docRef?? && !(docRef?contains("emodel/workspace"))>
    <p><a href="${link.getRecordLink(docRef)}" target="_blank">${eventDisp}</a></p>
</#if>
