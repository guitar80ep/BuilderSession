package org.builder.session.jackson.console.tags;

import static org.builder.session.jackson.console.html.impl.Elements.InputType;
import static org.builder.session.jackson.console.html.impl.Elements.disable;
import static org.builder.session.jackson.console.html.impl.Elements.newCheckbox;
import static org.builder.session.jackson.console.html.impl.Elements.newDiv;
import static org.builder.session.jackson.console.html.impl.Elements.newInput;
import static org.builder.session.jackson.console.html.impl.Elements.newSelect;
import static org.builder.session.jackson.console.html.impl.Elements.newTable;
import static org.builder.session.jackson.console.html.impl.Elements.newTableCell;
import static org.builder.session.jackson.console.html.impl.Elements.newTableRow;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.SimpleTagSupport;

import org.build.session.jackson.proto.Candidate;
import org.build.session.jackson.proto.InstanceSummary;
import org.build.session.jackson.proto.Resource;
import org.build.session.jackson.proto.Unit;
import org.build.session.jackson.proto.UsageSpec;
import org.builder.session.jackson.console.html.HtmlElement;
import org.builder.session.jackson.console.util.ResourceUtils;
import org.builder.session.jackson.console.util.Value;

import com.google.common.collect.Lists;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class HostViewTag extends SimpleTagSupport {

    private InstanceSummary instance;
    private Candidate candidate;

    public void doTag() throws JspException, IOException {
        JspWriter out = getJspContext().getOut();
        try {
            createInstanceView(instance, candidate).build()
                                                   .print(out);
        } catch (Throwable t) {
            out.println("Failure: " + t.toString() + " -> "
                                + Arrays.asList(t.getStackTrace()));
        }
    }

    private static HtmlElement.HtmlElementBuilder createInstanceView(@NonNull InstanceSummary instance,
                                                                     @NonNull Candidate candidate) {
        return newDiv().subElement(createResourceTable(instance, candidate).build());
    }

    private static HtmlElement.HtmlElementBuilder createResourceTable(@NonNull InstanceSummary instance,
                                                                      @NonNull Candidate candidate) {
        HtmlElement.HtmlElementBuilder table = newTable();

        String hostAddressAndPort = Candidate.ALL.equals(candidate)
                                    ? "ALL"
                                    : instance.getHost() + ":" + instance.getPort();
        HtmlElement candidateInput = newInput(InputType.Hidden,
                                              Input.Candidate.name(),
                                              candidate.name()).build();
        HtmlElement hostAddressInput = newInput(InputType.Hidden,
                                                Input.HostAddress.name(),
                                                instance.getHost()).build();
        HtmlElement hostPortInput = newInput(InputType.Hidden,
                                             Input.HostPort.name(),
                                             Integer.toString(instance.getPort())).build();
        table.subElement(newTableRow()
                                .subElement(newTableCell(3)
                                                    .text("<b>Host(s): </b>" +  hostAddressAndPort)
                                                    .subElement(candidateInput)
                                                    .subElement(hostAddressInput)
                                                    .subElement(hostPortInput)
                                                    .build())
                                .build());

        table.subElement(newTableRow()
                                 .subElement(newTableCell().build())
                                 .subElement(newTableCell().text("Target").build())
                                 .subElement(newTableCell().text("Actual").build())
                                 .build());

        List<UsageSpec> usages = Lists.newArrayList(instance.getUsageList());
        usages.sort(Comparator.comparing(u -> u.getResource().name()));
        Set<Resource> resources = new HashSet<>();
        for(UsageSpec usage : usages) {
            table.subElement(createResourceTableRow(usage.getResource(),
                                                    new Value(usage.getTarget(),
                                                              usage.getUnit()),
                                                    new Value(usage.getActual(),
                                                              usage.getUnit()))
                                     .build());
            resources.add(usage.getResource());
        }
        Set<HtmlElement> checkboxes = resources.stream()
                                               .map(r -> Input.find(r, "Save"))
                                               .map(i -> newCheckbox(i.name(),
                                                                     true)
                                                       .build())
                                               .collect(Collectors.toSet());

        table.subElement(newTableRow().subElement(
                newTableCell(3)
                        .subElement(newInput(InputType.Submit,
                                             "Button",
                                             "Save").build())
                        .subElements(checkboxes)
                        .build()).build());

        return table;
    }

    private static HtmlElement.HtmlElementBuilder createResourceTableRow(@NonNull Resource resource,
                                                                  @NonNull Value target,
                                                                  @NonNull Value actual) {
        return newTableRow()
                .subElement(newTableCell()
                                    .text(resource.name())
                                    .build())
                .subElement(newTableCell()
                                    .subElement(createValueView(resource,
                                                                true,
                                                                target).build())
                                    .build())
                .subElement(newTableCell()
                                    .subElement(createValueView(resource,
                                                                false,
                                                                actual).build())
                                    .build());
    }


    private static HtmlElement.HtmlElementBuilder createValueView(final Resource resource,
                                                           final boolean canEdit,
                                                           @NonNull Value initialValue) {

        //Build value...
        HtmlElement.HtmlElementBuilder valueInput = newInput(InputType.Number,
                                                             Input.find(resource, "Value").name(),
                                                             String.format("%.2f",
                                                                           initialValue.getValue()));
        valueInput = valueInput.attribute("step", "0.01");

        // Build unit...
        Map<String, Boolean> units = Arrays.asList(Unit.values())
                                           .stream()
                                           .collect(Collectors.toMap(
                                                      e -> e.name(),
                                                      e -> ResourceUtils.isMatching(resource, e)
                                           ));
        HtmlElement.HtmlElementBuilder unitInput = newSelect(Input.find(resource, "Unit").name(),
                                                             units,
                                                             initialValue.getUnit().name());


        return newDiv().subElement(disable(valueInput, !canEdit).build())
                       .subElement(disable(unitInput, !canEdit).build());

    }

    /**
     * An enum to define the enum types used to submit a form for a host.
     */
    public enum Input {
        Candidate,
        HostAddress,
        HostPort,
        SaveCpu,
        CpuValue,
        CpuUnit,
        SaveMemory,
        MemoryValue,
        MemoryUnit,
        SaveDisk,
        DiskValue,
        DiskUnit,
        SaveNetwork,
        NetworkValue,
        NetworkUnit;

        public static Input find(Resource resource, String type) {
            return Arrays.asList(Input.values())
                         .stream()
                         .filter(in -> in.name().toLowerCase()
                                         .contains(resource.name().toLowerCase()))
                         .filter(in -> in.name().contains(type))
                         .findFirst()
                         .orElseThrow(() -> new IllegalArgumentException("Couldn't find match for " + resource.name()));
        }
    }
}
