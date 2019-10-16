<%@ taglib  uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ page import="org.build.session.jackson.proto.Candidate" %>
<%@ page import="org.build.session.jackson.proto.Resource" %>
<%@ page import="org.build.session.jackson.proto.Unit" %>
<html>
    <body>
        <h1>Consumer Console</h1>
        <h4>Overview</h4>
        <p>This console allows users to make a simple request to ConsumerBackendService.</p>
        <h4>Make a Request:</h4>
        <p>Fill out all of the fields and click 'Submit'.</p>
        <form action="/results" method="POST">
            <table>
                <tr>
                    <th align="right">Candidate:</th>
                    <th>
                        <input type="text" name="candidate" list="candidate"/>
                    </th>
                </tr>
                <tr>
                    <th align="right">Resource:</th>
                    <th>
                        <input type="text" name="resource" list="resource"/>
                    </th>
                </tr>
                <tr>
                    <th align="right">Unit:</th>
                    <th>
                        <input type="text" name="unit" list="unit"/>
                    </th>
                </tr>
                <tr>
                    <th align="right">Target:</th>
                    <th>
                        <input type="number" name="target" step="0.001"/>
                    </th>
                </tr>
                <tr>
                    <th></th>
                    <th>
                        <input type = "submit" value = "Consume Resources"/>
                    </th>
                </tr>
            </table>

            <c:set var="candidateEnums" value="<%=Candidate.values()%>"/>
            <datalist id="candidate">
                <c:forEach items="${candidateEnums}" var="x">
                    <option>${x.name()}</option>
                </c:forEach>
            </datalist>

            <c:set var="resourceEnums" value="<%=Resource.values()%>"/>
            <datalist id="resource">
                <c:forEach items="${resourceEnums}" var="x">
                    <option>${x.name()}</option>
                </c:forEach>
            </datalist>

            <c:set var="unitEnums" value="<%=Unit.values()%>"/>
            <datalist id="unit">
                <c:forEach items="${unitEnums}" var="x">
                    <option>${x.name()}</option>
                </c:forEach>
            </datalist>
        </form>
    </body>
</html>