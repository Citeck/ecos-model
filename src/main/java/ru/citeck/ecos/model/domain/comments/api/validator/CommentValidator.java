package ru.citeck.ecos.model.domain.comments.api.validator;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

public class CommentValidator {

    public static String removeVulnerabilities(String data) {
        Document doc = Jsoup.parse(removeNonPrintable(StringEscapeUtils.unescapeHtml4(data)));
        return Jsoup.clean(
            doc.toString(),
            Safelist.relaxed()
                .addAttributes("p", "dir")
                .addAttributes("span", "data-mention")
        );
    }

    private static String removeNonPrintable(String data){
        if (data == null){
            return "";
        }
        return data
            .replace("\\x0C", "")
            .replace("\\x00", "")
            .replace("\\x2F", "")
            .replace("\\x20", "")
            .replace("\\x2F", "")
            .replace("\\x00", "");

    }
}
