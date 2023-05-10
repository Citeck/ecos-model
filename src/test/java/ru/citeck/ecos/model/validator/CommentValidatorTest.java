package ru.citeck.ecos.model.validator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import ru.citeck.ecos.model.domain.comments.api.validator.CommentValidator;


@SpringBootTest
public class CommentValidatorTest {


    @Test
    public void shouldCleanInlineCSS(){
        String result = CommentValidator.removeVulnerabilities("<h2>Inline CSS Example</h2>\n" +
                        "  <p><span style=\"color: #FF7A59\">inline</span> Test message </p>");
        Assertions.assertFalse(result.contains("style"));

    }

    @Test
    public void shouldCleanInternalCSS(){
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
                Assertions.assertFalse(result.contains("<style>"));

    }

    @Test
    public void shouldRemoveHref(){
        String result = CommentValidator.removeVulnerabilities("<link rel=\"stylesheet\" type=\"text/css\" rel=\"noopener\" target=\"_blank\" href=\"mystyles.css\">");
        Assertions.assertFalse(result.contains("href"));

    }

    @Test
    public void shouldRemoveScript(){
        String result = CommentValidator.removeVulnerabilities("<script>\n" +
                "document.getElementById(\"demo\").innerHTML = \"Hello JavaScript!\";\n" +
                "</script>");
        Assertions.assertFalse(result.contains("<script>"));

    }

    @Test
    public void shouldNotRemoveRequiredTags(){
        String result = CommentValidator.removeVulnerabilities("<p><strong>strong test </strong>" +
            "<em>test message</em> <u>underscore test</u></p>" +
            "<ul>  <li>list item</li>  <li>list item</li></ul>");
        Assertions.assertTrue(result.contains("<strong>") &&
            result.contains("<em>") &&
            result.contains("<u>") &&
            result.contains("<li>") &&
            result.contains("<ul>"));
    }
}
