<%@ page import="java.util.List" %>
<%@ page import="org.build.session.jackson.proto.Candidate" %>
<%@ page import="org.build.session.jackson.proto.ConsumeResponse" %>
<%@ page import="org.build.session.jackson.proto.InstanceSummary" %>
<%@ page import="org.builder.session.jackson.client.consumer.ConsumerBackendClient" %>
<%@ page import="org.builder.session.jackson.console.util.InstanceUtils" %>
<%@ page import="org.builder.session.jackson.console.util.RequestUtils" %>
<%@ page import="org.builder.session.jackson.utils.JsonHelper" %>
<%@ page import="org.springframework.context.ApplicationContext" %>
<%@ page import="org.springframework.web.servlet.support.RequestContextUtils" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="core"%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix = "consumer" uri = "/WEB-INF/tld/consumer.tld"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
    <head>
        <base href="${pageContext.request.contextPath}">
        <title>Consumer Service Console</title>

        <link href="resources/css/default.css" type="text/css" rel="stylesheet" >
        <link href="resources/css/tab.css" type="text/css" rel="stylesheet" >
        <link href="resources/css/table.css" type="text/css" rel="stylesheet" >
    </head>

    <%
        ApplicationContext ac = RequestContextUtils.findWebApplicationContext(request);
        //This signifies a request.
        ConsumeResponse result = null;
        List<InstanceSummary> instances = null;
        try (ConsumerBackendClient client = (ConsumerBackendClient) ac.getBean("backendClient")) {
            RequestUtils.request(request, client);
            instances = RequestUtils.describe(client);
            instances = RequestUtils.sort(instances);
        }

        // Choose a random instance
        InstanceSummary randomInstance = InstanceUtils.getRandomInstance(instances);

        // Calculate an "Average" instance for use with the ALL candidate.
        InstanceSummary avgInstance = InstanceUtils.calculateAverage("ALL", instances.size(), instances);
    %>

    <body>
        <h1>Consumer Service Console</h1>
        <h3>Overview</h3>
        <p>
            This console allows users to make a simple request to ConsumerBackendService to set
            the usage of resources for a particular ECS Task. One ConsumerService Task is made
            up of a Console and a Backend container.

            Users can choose to edit a SPECIFIC host, ALL hosts or a RANDOM host. Edit fields
            in the category of interest and hit "Submit." This page will refresh to show the
            new service state.

            You are talking to <%= RequestUtils.getHostname() %>.
        </p>
        <br/>

        <p>
            <%= result != null ? "Result: " + JsonHelper.format(result.getInstances(0)).trim()
                               : "No Result." %>
        </p>
        <br/>

        <div class="tab">
            <button id="SpecificButton" class="tabbutton" onclick="changeTab(event, 'Specific')">Specific</button>
            <button id="AllButton" class="tabbutton" onclick="changeTab(event, 'All')">All</button>
            <button id="RandomButton" class="tabbutton" onclick="changeTab(event, 'Random')">Random</button>
        </div>

        <div id="Specific" class="tabcontent">
            <p>Change a specfic hosts' target consumption.</p>
            <c:forEach items="<%= instances %>" var="hostToView" >
                <form action="" method="post">
                    <consumer:HostView instance="${hostToView}" candidate="<%= Candidate.SPECIFIC %>"/>
                </form>
            </c:forEach>
        </div>

        <div id="All" class="tabcontent">
            <p>Change all hosts' target consumption.</p>
            <form action="" method="post">
                <consumer:HostView instance="<%= avgInstance %>" candidate="<%= Candidate.ALL %>"/>
            </form>
        </div>

        <div id="Random" class="tabcontent">
            <p>Change a random host's target consumption.</p>
            <form action="" method="post">
                <consumer:HostView instance="<%= randomInstance %>" candidate="<%= Candidate.SPECIFIC %>"/>
            </form>
        </div>

        <script>
            //A function for changing tabs within the page.
            function changeTab(event, candidate) {
                var i, tabcontent, tabbuttons;

                // Turn off display of all Tab content.
                tabcontent = document.getElementsByClassName("tabcontent");
                for (i = 0; i < tabcontent.length; i++) {
                    tabcontent[i].style.display = "none";
                }

                //Turn all buttons to inactive except for the selected one.
                tabbuttons = document.getElementsByClassName("tabbutton");
                for (i = 0; i < tabbuttons.length; i++) {
                    tabbuttons[i].className = tabbuttons[i].className.replace(" active", "");
                }
                document.getElementById(candidate).style.display = "block";
                event.currentTarget.className += " active";
            }
        </script>
    </body>
    <script defer>
        // By default, we open the "Specific" tab.
        document.getElementById("SpecificButton").click();
    </script>
</html>