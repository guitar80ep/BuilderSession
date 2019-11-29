package org.builder.session.jackson.console.html;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.jsp.JspWriter;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import software.amazon.awssdk.utils.StringUtils;

@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class HtmlElement {

    @NonNull
    private String name;
    @Singular
    @NonNull
    private Map<String, String> attributes = new LinkedHashMap<>();
    @Singular
    @NonNull
    private List<HtmlElement> subElements = new ArrayList<>();
    private String text;

    public void print(JspWriter writer) throws IOException {

        //Combine all attributes.
        StringBuilder attributesBuilder = new StringBuilder();
        for(Map.Entry<String, String> attribute : attributes.entrySet()) {
            String nameOfAttribute = attribute.getKey();
            String valueOfAttribute = attribute.getValue();

            attributesBuilder.append(nameOfAttribute);
            if(!StringUtils.isBlank(valueOfAttribute)) {
                attributesBuilder.append("=");
                attributesBuilder.append("\"" + valueOfAttribute + "\"");
            }
            attributesBuilder.append(" ");
        }

        //Write the element.
        boolean hasSubElements = subElements.size() > 0;
        boolean hasText = !StringUtils.isBlank(text);
        if(hasSubElements || hasText) {
            //Open the tag with all attributes...
            writer.println("<" + name + " " + attributesBuilder.toString() + " >");

            //Print all sub elements...
            if(hasSubElements) {
                for (HtmlElement element : subElements) {
                    element.print(writer);
                }
            }

            //Print all text...
            if(hasText) {
                writer.println(text);
            }

            //Close the tag...
            writer.println("</" + name + ">");
        } else {
            //Open and close the tag with all attributes...
            writer.println("<" + name + " " + attributesBuilder.toString() + " />");
        }
    }
}
