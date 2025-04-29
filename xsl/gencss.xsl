<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fo="http://www.w3.org/1999/XSL/Format" 
	exclude-result-prefixes="fo">
	
	<xsl:import href="css.xsl"/>
	<xsl:output method="text" version="1.0" encoding="ISO-8859-1" indent="yes"/>

	<!-- - - - - - - - - - - - - - -->
	<!-- Main Template             -->
	<!-- - - - - - - - - - - - - - -->
	<xsl:template match="jose-export">
	<xsl:call-template name="generate_css"/>
	</xsl:template>

</xsl:stylesheet>
