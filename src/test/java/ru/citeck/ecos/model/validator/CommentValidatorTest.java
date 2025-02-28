package ru.citeck.ecos.model.validator;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.citeck.ecos.model.domain.comments.api.validator.CommentValidator;

import static org.assertj.core.api.Assertions.assertThat;

public class CommentValidatorTest {

    @Test
    public void testStyleParams() {

        String src = "<ul class=\"rt-editor-theme__ul\">" +
            "<li class=\"rt-editor-theme__list-item\"><span class=\"PlaygroundEditorTheme__embedBlock\">sdfsdfsdf</span>" +
            "</li></ul>" +
            "<ol class=\"rt-editor-theme__ol1\"><li class=\"rt-editor-theme__list-item\">" +
            "<span class=\"PlaygroundEditorTheme__embedBlock\">sdfsdfsdfdsfsdfsdfdsfsdf</span></li>" +
            "</ol>" +
            "<p class=\"PlaygroundEditorTheme__paragraph\" dir=\"ltr\">" +
            "<span class=\"PlaygroundEditorTheme__embedBlock\" style=\"background-color: rgb(255, 48, 48); font-size: 0.8em\">dsfsdfsdf</span>" +
            "<br></p><p class=\"PlaygroundEditorTheme__paragraph\" dir=\"ltr\">" +
            "<span class=\"PlaygroundEditorTheme__embedBlock\">sdfsdfsdf</span></p>";

        assertThat(Jsoup.parseBodyFragment(CommentValidator.removeVulnerabilities(src)).outerHtml()).isEqualTo(Jsoup.parseBodyFragment(src).outerHtml());
    }

    @Test
    public void testImgWithRelativeUrl() {

        String src = "<p class=\"PlaygroundEditorTheme__paragraph\" dir=\"ltr\">" +
            "<img class=\"PlaygroundEditorTheme__image editor-image\" " +
            "src=\"/gateway/emodel/api/ecos/webapp/content?ref=temp-file%400a8f6f48-61d7-44b0-91c1-714047f1a7a0&amp;att=content\" " +
            "alt=\"\" width=\"inherit\" height=\"inherit\"><span class=\"PlaygroundEditorTheme__embedBlock\">рп</span></p>";

        assertThat(CommentValidator.removeVulnerabilities(src)).isEqualTo(src);
    }

    @Test
    public void shouldCleanInternalCSS() {
        String result = CommentValidator.removeVulnerabilities("<!DOCTYPE html>\n" +
            "<html>\n" +
            "    <head>\n" +
            "        <style>\n" +
            "            p {\n" +
            "                color: #FF7A59;\n" +
            "            }\n" +
            "        </style>\n" +
            "    </head>\n" +
            "    <body>\n" +
            "        <h2>Text message</h2>\n" +
            "    </body>\n" +
            "</html>");

        assertThat(result.contains("<style>")).isFalse();
    }

    @Test
    public void shouldRemoveHref() {
        String result = CommentValidator.removeVulnerabilities("<link rel=\"stylesheet\" type=\"text/css\" rel=\"noopener\" target=\"_blank\" href=\"mystyles.css\">");

        Assertions.assertFalse(result.contains("href"));
    }

    @Test
    public void shouldRemoveScript() {
        String result = CommentValidator.removeVulnerabilities("<script>\n" +
            "document.getElementById(\"demo\").innerHTML = \"Hello JavaScript!\";\n" +
            "</script>");

        Assertions.assertFalse(result.contains("<script>"));
    }

    @Test
    public void shouldNotRemoveRequiredTags() {
        String result = CommentValidator.removeVulnerabilities("<p><strong>strong test </strong>" +
            "<em>test message</em> <u>underscore test</u></p>" +
            "<ul>  <li>list item</li>  <li>list item</li></ul>");

        Assertions.assertTrue(result.contains("<strong>") &&
            result.contains("<em>") &&
            result.contains("<u>") &&
            result.contains("<li>") &&
            result.contains("<ul>"));
    }

    @Test
    public void shouldCleanNonPrintableElementsInTheBeginning(){
        String result = CommentValidator.removeVulnerabilities("<script\\x0C>javascript:alert(1)</script> text message");
        Assertions.assertEquals(" text message", result);
    }

    @Test
    public void shouldCleanNonPrintableElementsInEnd(){
        String result = CommentValidator.removeVulnerabilities("<script>javascript:alert(1)<\\x00/script> text message");
        Assertions.assertEquals(" text message", result);
    }
}
