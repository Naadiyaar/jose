<%@ page import="de.jose.web.WebApplication"%>
<%@ page import="de.jose.web.SessionUtil"%>
<%@ page import="de.jose.pgn.Collection"%>
<%@ page import="de.jose.db.JoConnection"%>
<%@ page import="de.jose.db.JoPreparedStatement"%>
<%@ page import="java.sql.ResultSet"%>
<%@ page import="de.jose.Language"%>
<%@ page language="java" %>
<!--%@ taglib prefix="jose" tagdir="." %-->
<%!
	public static int getDepth(String path)
	{
		int depth = 0;
		int i=-1;
		for (;;)
		{
			i = path.indexOf("/",i+1);
			if (i >= 0)
				depth++;
			else
				break;
		}
		return depth;
	}
%>
<%
	WebApplication.open(application,response);
	SessionUtil su = new SessionUtil(request,session);
	WebApplication.createCollateral(su);
%>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="StyleSheet" type="text/css" href="games.css">
	<style>
		td,th,input { font-family: Arial,Helvetica,Sans-Serif; font-size: 14px; }
		TR.odd {  background-color: #e2e2ff;}
		TR.even {  background-color: #eeeeff; }
		TD { padding:2px 4px 2px 4px; white-space: nowrap; }
		TD.center { text-align: center; }
		TD.center-pad { text-align: center; padding-left: 32px; }
		TD.right { text-align: right; }
		TR.header, TR.footer {  background-color: #e5e5e5; padding: 8px 4px 8px 4px; }
		A { color:black; text-decoration: none; }
		span.label {
			font-size: 13pt;
			cursor: grab;
			white-space: pre-wrap;
		}
		span.button, span.folder, span.expander {
			font-family: "Font Awesome 6 Free Solid";
			font-size: 16pt;
			cursor: grab;
			white-space: pre-wrap;
			padding-left: 8px;
			padding-right: 8px;
		}
		span.folder {
			color: #aeae63;
		}
		span.expander {
			color: grey;
		}
	</style>

</head>
<script language="JavaScript">
	var cids = new Array();

	function adjust_image(CId) {
		var xicon = document.getElementById('x-' + CId);
		if (!xicon.canExpand)
			xicon.textContent = " ";
		else if (xicon.isExpanded)
			xicon.textContent = "\uf078";
		else
			xicon.textContent = "\uf054";

		var ficon = document.getElementById('f-' + CId);
		switch(CId) {
			case <%=Collection.TRASH_ID%>:
				ficon.textContent = "\uf2ed";
				ficon.style.color = "#aaaaaa";
				return;
			case <%=Collection.CLIPBOARD_ID%>:
				ficon.textContent = "\uf328";
				ficon.style.color = "#aaaaaa";
				return;
			case <%=Collection.AUTOSAVE_ID%>:
				ficon.textContent = "\uf0c7";
				ficon.style.color = "#aaaaaa";
				return;
			case <%=Collection.INTRAY_ID%>:
				ficon.textContent = "\uf01c";
				ficon.style.color = "#aaaaaa";
				return;
		}

		if (xicon.canExpand && xicon.isExpanded) {
			ficon.textContent = "\uf07c";
			ficon.style.color = "#aeae63";
		} else {
			ficon.textContent = "\uf07b";
			ficon.style.color = "#cece63"
		}
	}

	function add_image(CId, nest_depth, canExpand, isExpanded)
	{
		cids[cids.length] = CId;
		var xicon = document.getElementById('x-'+CId);
		xicon.nestDepth = nest_depth;
		xicon.canExpand = canExpand;
		xicon.isExpanded = isExpanded;
		adjust_image(CId);
	}

	function toggle(CId) {
		var xicon = document.getElementById('x-' + CId);
		if (xicon.canExpand) {
			xicon.isExpanded = ! xicon.isExpanded;
			adjust_image(CId);
			showChildren(CId, xicon.isExpanded);
		}  else {
			//	goto Collection !
			var form = document.forms[0];
			form.elements['CId'].value = CId;
			form.submit();
		}
	}

	function showChildren(parentId, visible)
	{
		var parentIcon = document.getElementById('x-'+parentId);
		var i = cids.indexOf(parentId)+1;

		for ( ; i < cids.length; i++)
		{
			var childId = cids[i];
			var childIcon = document.getElementById('x-'+childId);
			if (childIcon.nestDepth <= parentIcon.nestDepth)
				break;

			if (childIcon.nestDepth == (parentIcon.nestDepth+1))
			{
				show(childId, visible);
				if (childIcon.canExpand && childIcon.isExpanded)
					showChildren(childId, visible);
			}
		}
	}

	function show(CId, visible)
	{
		var row = document.getElementById('r-'+CId);
		if (visible)
			row.style.display = '';
		else
			row.style.display = 'none';
	}

</script>

<body>

<form method="post" action="collection.jsp">
	<input type="hidden" name="CId">
</form>

<table border=0 cellpadding=0 cellspacing=0>
	<tr class="header">
		<th colspan="2">&nbsp;</th>
		<th colspan="2">&nbsp;<%=Language.get("web.download")%> </th>
	</tr>

<%
	JoConnection connection = null;
	try {
		connection = JoConnection.get();
		JoPreparedStatement pstm = connection.getPreparedStatement(
			"select Parent.Id, Parent.Path, Parent.Name, Parent.GameCount, COUNT(Child.Id)" +
			" from Collection Parent LEFT OUTER JOIN Collection Child ON Child.PId = Parent.Id" +
			" group by Parent.Id" +
			" order by Parent.Path");

		pstm.execute();
		ResultSet res = pstm.getResultSet();

		for (int line=1; res.next(); line++)
		{
			String row_style ="even";

			int CId = res.getInt(1);
			String path = res.getString(2);
			String name = res.getString(3);
			if (Collection.isSystem(CId))
				name = Language.get(name);

			int gameCount = res.getInt(4);
			boolean hasChildren = res.getInt(5) > 0;

			int depth = getDepth(path)-2;
			if (depth > 0) row_style="odd";

			%>
			<tr class="<%=row_style%>" id="r-<%=CId%>">
				<td>
					<span style="margin-left: <%=16*depth%>px;"></span>
					<span class="expander" id="x-<%=CId%>" onclick="toggle(<%=CId%>)"></span>
					<span class="folder" id="f-<%=CId%>" onclick="toggle(<%=CId%>)"></span>
					<span class="label" onclick="toggle(<%=CId%>)"><%=name%></span>
				</td>
				<td class="right">
					<%
						if (gameCount > 0) {
						%><a href="collection.jsp?CId=<%=CId%>">(<%=gameCount%> <%=Language.getPlural("web.game",gameCount>1)%>)</a><%
						}
					%>
				</td>
				<td class="center-pad">
					<a href="download-servlet?CId=<%=CId%>&out=pgn">pgn</a>
				</td>
				<td class="center">
					<a href="download-servlet?CId=<%=CId%>&out=archive">archive</a>
				</td>
			</tr>
			<script language="JavaScript">
				add_image(<%=CId%>,<%=depth%>,<%=hasChildren%>,<%=hasChildren%>);
			</script>
			<%
		}
%>

	<tr>
		<td colspan="4" style="text-align: center;padding-top:20px;background: #EEEEED;">
			<form method="post" action="upload-servlet" enctype="multipart/form-data" target="_blank">
				<input type="file" name="upload-file" />
				<input type="submit" value="<%=Language.get("web.upload")%>" />
			</form>
		</td>
	</tr>

	<tr class="footer">
		<th colspan="4" style="text-align: center;">
			created with
			<a href="https://peteschaefer.github.io/jose/">jose Chess</a>
		</th>
	</tr>

</table>
</body>
</html>
<%
	} finally {
		JoConnection.release(connection);
	}
%>
