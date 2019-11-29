package org.builder.session.jackson.console.html.impl;

import java.util.Map;

import org.builder.session.jackson.console.html.HtmlElement;

public class Elements extends HtmlElement {

    public static HtmlElementBuilder newTableCell() {
        return HtmlElement.builder()
                          .name("td");
    }

    public static HtmlElementBuilder newTableCell(int columnSpan) {
        return HtmlElement.builder()
                          .name("td")
                          .attribute("colspan", Integer.toString(columnSpan));
    }

    public static HtmlElementBuilder newTableRow() {
        return HtmlElement.builder()
                          .name("tr");
    }

    public static HtmlElementBuilder newDiv() {
        return HtmlElement.builder()
                          .name("div");
    }

    public static HtmlElementBuilder newTable() {
        return HtmlElement.builder()
                          .name("table");
    }

    public static HtmlElementBuilder newForm(String action) {
        return HtmlElement.builder()
                          .name("form")
                          .attribute("action", action);
    }

    public static HtmlElementBuilder newInput(InputType type,
                                              String name,
                                              String value) {
        return HtmlElement.builder()
                          .name("input")
                          .attribute("type", type.toType())
                          .attribute("name", name)
                          .attribute("value", value);
    }

    public static HtmlElementBuilder newCheckbox(String name, boolean checkedByDefault) {
        return checked(newInput(InputType.Checkbox, name, ""), checkedByDefault).text(name);
    }

    public static HtmlElementBuilder newSelect(String name,
                                               Map<String, Boolean> optionsToEnabledStatus,
                                               String selectedOption) {
        HtmlElementBuilder builder = HtmlElement.builder()
                                                .name("select")
                                                .attribute("name", name);
        for(Map.Entry<String, Boolean> entry : optionsToEnabledStatus.entrySet()) {
            String optionValue = entry.getKey();
            boolean enabled = entry.getValue();
            boolean isSelected = optionValue.equals(selectedOption);
            HtmlElementBuilder option = disable(select(HtmlElement.builder()
                                                          .name("option")
                                                          .text(optionValue)
                                                          .attribute("value", optionValue),
                                                       isSelected),
                                                !enabled);
            builder.subElement(option.build());
        }

        return builder;
    }

    public static HtmlElementBuilder disable(HtmlElementBuilder element, boolean ifTrue) {
        if(ifTrue) {
            return element.attribute("disabled", "");
        } else {
            return element;
        }
    }

    public static HtmlElementBuilder select(HtmlElementBuilder element, boolean ifTrue) {
        if(ifTrue) {
            return element.attribute("selected", "");
        } else {
            return element;
        }
    }

    public static HtmlElementBuilder checked(HtmlElementBuilder element, boolean ifTrue) {
        if(ifTrue) {
            return element.attribute("checked", "");
        } else {
            return element;
        }
    }

    public enum InputType {
        Button,
        Number,
        Submit,
        Hidden,
        Checkbox,
        Text;

        public String toType() {
            return this.name().toLowerCase();
        }
    }
}
