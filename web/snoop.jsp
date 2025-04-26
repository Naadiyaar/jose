<%@ page language="java" %>
<%@ page import="de.jose.Version"%>
<%@ page import="java.sql.Connection"%>
<%@ page import="de.jose.db.JoConnection"%>
<%@ page import="de.jose.db.JoPreparedStatement"%>
<%@ page import="de.jose.web.WebApplication"%>
<%@ page import="de.jose.db.DBAdapter" %>

<% WebApplication.open(application,response); %>

<html>
<head></head>

<body>
Hello from jose <%=Version.jose%> <br>
running on <%=Version.osName%> <%=Version.osVersion%> <%=Version.arch%>

<hr>

Working directory is: <%=WebApplication.theApplication.theWorkingDirectory%> <br>
Web directory is: <%=application.getRealPath("")%><br>
Request path is: <%=request.getRealPath("")%><br>

<!--
Property("jose.workdir")=< %=Version.getSystemProperty("jose.workdir")%> <br>
Property("jose_workdir")=< %=Version.getSystemProperty("jose_workdir")%> <br>
-->

<hr>
Database is: <%=WebApplication.theApplication.theDatabaseId%><br>
<%
	JoConnection conn = JoConnection.get();
	Connection jconn = conn.getJdbcConnection();
	DBAdapter adapt = JoConnection.getAdapter();
%>
<%=adapt.getDatabaseProductName(jconn)%> <%=adapt.getDatabaseProductVersion(jconn)%><br>
<% 	conn.release(); %>
<br>
local data dir: <%=WebApplication.theApplication.theDatabaseDirectory%> <br>
jdbc-url: <%=JoConnection.getAdapter().getURL()%> <br>

<hr>

<table border=1>
	<tr>
		<th>Id</th>
		<th>Name</th>
		<th># of Games</th>
	</tr>
<%
	try {
		conn = JoConnection.get();
		JoPreparedStatement stm = conn.getPreparedStatement(
				"SELECT Id, Name, GameCount" +
				" FROM Collection WHERE GameCount > 0");
		stm.execute();
		while (stm.next())
		{
			%><tr>
			<td>
				<a href="collection.jsp?CId=<%=stm.getInt(1)%>"><%=stm.getInt(1)%></a>
			</td>
			<td><%=stm.getString(2)%></td>
			<td><%=stm.getInt(3)%></td>
			</tr><%
		}
	} catch (Throwable ex) {
			ex.printStackTrace(response.getWriter());
	} finally {
		JoConnection.release(conn);
	}

%>
</table>

</body>
</html>