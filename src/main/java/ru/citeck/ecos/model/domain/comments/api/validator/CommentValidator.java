package ru.citeck.ecos.model.domain.comments.api.validator;

import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

public class CommentValidator {

    public static String removeVulnerabilities(String data) {
        Document doc = Jsoup.parse(StringEscapeUtils.unescapeHtml(data));
        return Jsoup.clean(doc.toString(), Safelist.relaxed());
    }
}
