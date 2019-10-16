<%@ taglib  uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ page import="org.build.session.jackson.proto.Candidate" %>
<%@ page import="org.build.session.jackson.proto.ConsumeRequest" %>
<%@ page import="org.build.session.jackson.proto.ConsumeResponse" %>
<%@ page import="org.build.session.jackson.proto.Resource" %>
<%@ page import="org.build.session.jackson.proto.Unit" %>
<%@ page import="org.build.session.jackson.proto.UsageSpec" %>
<%@ page import="org.builder.session.jackson.client.ConsumerBackendClient" %>
<%@ page import="org.builder.session.jackson.utils.JsonHelper" %>
<%@ page import="org.springframework.context.ApplicationContext" %>
<%@ page import="org.springframework.web.servlet.support.RequestContextUtils" %>
<html>
<%
    ApplicationContext ac = RequestContextUtils.findWebApplicationContext(request);
    ConsumerBackendClient client = (ConsumerBackendClient) ac.getBean("backendClient");
    Candidate candidate = Candidate.valueOf(request.getParameter("candidate"));
    Resource resource = Resource.valueOf(request.getParameter("resource"));
    Unit unit = Unit.valueOf(request.getParameter("unit"));
    double target = Double.valueOf(request.getParameter("target"));
    UsageSpec usage = UsageSpec.newBuilder().setResource(resource).setUnit(unit).setTarget(target).build();
    ConsumeResponse consumeResult = client.call(ConsumeRequest.newBuilder().setCandidate(candidate).addUsage(usage).build());
%>
    <body>
        <h1>Consumer Console</h1>
        <h4>Results</h4>
        <pre><%=JsonHelper.format(consumeResult).trim()%></pre>
    </body>
</html>