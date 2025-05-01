<%@ page language="java" pageEncoding="UTF-8" %>
<%@ page import="de.jose.web.WebApplication"%>
<%@ page import="de.jose.web.SessionUtil"%>
<%@ page import="de.jose.Language"%>
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
		A { color:black; text-decoration: none; border-color: #e5e5e5; }
		body { background-color: #e5e5e5; }
		img.on { background-color: #c5c5c5; border: groove; background: ButtonHighlight; border-color: #e5e5e5; }
		img.off { background-color: #e5e5e5; border: ButtonShadow; border-color: #e5e5e5; }
		img.button { background-color: #e5e5e5; border: ridge; padding: 4px; }
		span.button-green, span.button-yellow {
			font-family: "Font Awesome 6 Free Solid";
			font-size: 20pt;
			cursor: grab;
			white-space: pre-wrap;
			padding: 8px;
			border-radius: 16px;
		}
		span.button-green {
			color: #009900;
		}
		span.button-yellow {
			color: #888800;
		}
	</style>
</head>
<body>
	<form method=post>
		<table width="100%" height="200px" celllpadding=16 celllspacing=16>
			<tr><td align="center">
				<a href="javascript:nextrow(-1)"><span id="game.previous" class="button-green"
													   onmouseover="hover('game.previous',1)" onmouseout="hover('game.previous',2)"
													   onmousedown="hover('game.previous',3)" onmouseup="hover('game.previous',4)">&#xf35b;</span></a>
			</td></tr>
			<tr><td align="center">
				<a href="collection.jsp" target="_parent"><span id="search" class="button-yellow"
				                                               onmouseover="hover('search',1)" onmouseout="hover('search',2)"
																onmousedown="hover('search',3)" onmouseup="hover('search',4)">&#xf002;</span></a>
			</td></tr>
			<tr><td align="center">
				<a href="index.jsp" target="_parent"><span id="index-page" class="button-yellow"
																onmouseover="hover('index-page',1)" onmouseout="hover('index-page',2)"
																onmousedown="hover('index-page',3)" onmouseup="hover('index-page',4)">&#xf07c;</span></a>
			</td></tr>
			<tr><td align="center">
				<a href="javascript:nextrow(+1)"><span id="game.next" class="button-green"
													   onmouseover="hover('game.next',1)" onmouseout="hover('game.next',2)"
													   onmousedown="hover('game.next',3)" onmouseup="hover('game.next',4)">&#xf358;</span></a>
			</td></tr>
		</table>

		<table width="100%" celllpadding=0 celllspacing=0 style="position: absolute;bottom:0;left:0;text-align: center;">
			<tr><td>
				<a href="javascript:setoutput('xsl.dhtml')"><img src="html.png" name="html"
				                                                 alt="<%=Language.getTip("web.html")%>"
				                                                 width="48" height="48"></a>
			</td></tr>
			<tr><td>
				<a href="javascript:setoutput('xsl.pdf')"><img src="pdf.png" name="pdf"
				                                               alt="<%=Language.getTip("web.pdf")%>"
				                                               width="48" height="48"></a>
			</td></tr>
			<tr><td>
				<a href="javascript:setoutput('xsl.text')"><img src="txt.png" name="txt"
				                                                 alt="<%=Language.getTip("web.txt")%>"
				                                                 width="48" height="48"></a>
			</td></tr>
			<tr><td>
				<a href="javascript:setoutput('export.pgn')"><img src="pgn.png" name="pgn"
				                                                  alt="<%=Language.getTip("web.pgn")%>"
				                                                  width="48" height="48"></a>
			</td></tr>
		</table>
	</form>

	<script language="JavaScript">
		var current_row = <%=su.getInt("row",1,true)%>;
		var max_row = <%=su.getInt("count-results",-1,true)%>;
		var output = '<%=su.getString("out","xsl.dhtml",true)%>';

		function hover(name, state)
		{
			var icon = document.getElementById(name);
			if (icon.origColor==null) icon.origColor = icon.style.color;
			icon.style.background = "#EEEEED";

			if (!icon.isEnabled) {
				icon.style.color = "#888888";
				return;
			}

			switch (state)
			{
			case 1: //  mouse over
			case 4: //  mouse up
				icon.style.color = '#00cc00';
				break;
			case 2: //  mouse out
				icon.style.color = icon.origColor;
				break;
			case 3: //  mouse down
				icon.style.background = "#808080";
				break;
			}
		}

		function setoutput(output_type)
		{
			output = output_type;
			top.GameView.location.href = 'game-servlet?out='+output_type;

			document.images['html'].className ='off';
			document.images['pdf'].className='off';
			document.images['txt'].className='off';
			document.images['pgn'].className='off';

			if (output_type=='xsl.dhtml') document.images['html'].className='on';
			if (output_type=='xsl.pdf') document.images['pdf'].className='on';
			if (output_type=='xsl.text') document.images['txt'].className='on';
			if (output_type=='export.pgn') document.images['pgn'].className='on';
		}

		function nextrow(diff) { setrow(current_row+diff); }

		function setrow(new_row)
		{
			if (new_row < 0) new_row = 0;
			if (max_row >= 0 && new_row >= max_row) new_row = max_row-1;

			document.getElementById('game.previous').isEnabled = (new_row>1);
			document.getElementById('search').isEnabled = true;
			document.getElementById('index-page').isEnabled = true
			document.getElementById('game.next').isEnabled = (max_row<=0 || (new_row+1) < max_row);

			hover('game.previous',2);
			hover('search',2);
			hover('index-page',2);
			hover('game.next',2);

			if (new_row==current_row) return;

			top.GameView.location.href = ('game-servlet?row='+new_row);

			current_row = new_row;
		}
	</script>

	<script language="JavaScript" defer>
		setrow(current_row);
		setoutput(output);
	</script>
</body>
</html>