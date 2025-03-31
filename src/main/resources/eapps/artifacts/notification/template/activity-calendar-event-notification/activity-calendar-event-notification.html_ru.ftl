<p>${body}</p>

<#if docRef?? && !(docRef?contains("emodel/workspace"))>
    <p>${link.getRecordLink(docRef)}</p>
</#if>
