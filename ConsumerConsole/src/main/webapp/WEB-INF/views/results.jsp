<%@ taglib  uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ page import="org.build.session.jackson.proto.Candidate" %>
<%@ page import="org.build.session.jackson.proto.ConsumeRequest" %>
<%@ page import="org.build.session.jackson.proto.ConsumeResponse" %>
<%@ page import="org.build.session.jackson.proto.Resource" %>
<%@ page import="org.build.session.jackson.proto.Unit" %>
<%@ page import="org.build.session.jackson.proto.UsageSpec" %>
<%@ page import="org.builder.session.jackson.client.ConsumerBackendClient" %>
<%@ page import="org.builder.session.jackson.utils.HostnameUtils" %>
<%@ page import="org.builder.session.jackson.utils.JsonHelper" %>
<%@ page import="org.springframework.context.ApplicationContext" %>
<%@ page import="org.springframework.web.servlet.support.RequestContextUtils" %>
<html>
<%
    ApplicationContext ac = RequestContextUtils.findWebApplicationContext(request);
    ConsumerBackendClient client = (ConsumerBackendClient) ac.getBean("backendClient");

    String candidateInput = request.getParameter("candidate");
    String resourceInput = request.getParameter("resource");
    String unitInput = request.getParameter("unit");
    String targetInput = request.getParameter("target");
    boolean hasAllInputs = candidateInput != null && resourceInput != null &&
            unitInput != null && targetInput != null;
    boolean hasNoInputs = candidateInput != null && resourceInput != null &&
            unitInput != null && targetInput != null;

    ConsumeResponse consumeResult = null;
    if(hasNoInputs) {
        //Essentially it's just a read....
        consumeResult = client.call(ConsumeRequest.newBuilder()
                                                  .setCandidate(Candidate.ALL)
                                                  .build());
    } else if (hasAllInputs) {
        Candidate candidate = Candidate.valueOf(candidateInput);
        Resource resource = Resource.valueOf(resourceInput);
        Unit unit = Unit.valueOf(request.getParameter(unitInput));
        double target = Double.valueOf(targetInput);
        UsageSpec usage = UsageSpec.newBuilder()
                                   .setResource(resource)
                                   .setUnit(unit)
                                   .setTarget(target)
                                   .build();
        consumeResult = client.call(ConsumeRequest.newBuilder()
                                                  .setCandidate(candidate)
                                                  .addUsage(usage)
                                                  .build());
    } else {
        throw new IllegalArgumentException("Users must input either all arguments or none.");
    }
%>
    <body>
        <h1>Consumer Console</h1>
        <h4>Results from <%= HostnameUtils.resolveIpAddress() %></h4>
        <pre><%=JsonHelper.format(consumeResult).trim()%></pre>
    </body>
</html>