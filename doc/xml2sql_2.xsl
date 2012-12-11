<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="/">
	<query>
	<xsl:apply-templates/>
	</query>
</xsl:template>

<xsl:template match="select">
	<xsl:text>SELECT</xsl:text>
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="selection">
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="tableAlias">
	<xsl:value-of select="."/>
	<xsl:if test="count(following-sibling::name) = 1"><xsl:text>.</xsl:text></xsl:if>
</xsl:template>

<xsl:template match="table">
	<xsl:apply-templates/>
	<xsl:if test="count(following-sibling::table) > 0"><xsl:text>, </xsl:text></xsl:if>
</xsl:template>

<xsl:template match="name">
	<xsl:value-of select="."/><xsl:text> </xsl:text>
</xsl:template>

<xsl:template match="from">
	<xsl:text>FROM </xsl:text>
	<xsl:apply-templates/>
</xsl:template>
	 
<xsl:template match="where"><xsl:text> WHERE </xsl:text> <xsl:apply-templates/> </xsl:template>

<xsl:template match="negate"><xsl:text> NOT </xsl:text></xsl:template>

<xsl:template match="combinator">
	<xsl:value-of select="."/>
</xsl:template>

<xsl:template match="group">
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="parenthesis">
	<xsl:text> ( </xsl:text>
	<xsl:apply-templates/>
	 <xsl:text> ) </xsl:text>
</xsl:template>

<xsl:template match="comparison">
	<xsl:apply-templates/>	
</xsl:template>

<xsl:template match="operator">
	<xsl:value-of select="."/>
</xsl:template>

<xsl:template match="parameterName">
	<xsl:text>:</xsl:text><xsl:value-of select="."/>
</xsl:template>/

<xsl:template match="field"> 
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="subSelect">
	<xsl:apply-templates select="field"/>
	<xsl:if test="count(negate) > 0"><xsl:text> NOT </xsl:text></xsl:if>
	<xsl:text> IN (</xsl:text>
	<xsl:apply-templates select="subSelectStatement"/>
	<xsl:text>)</xsl:text>
</xsl:template>

<xsl:template match="comma">
<xsl:text>, </xsl:text>
</xsl:template>

<xsl:template match="parameterList"> 

</xsl:template>

</xsl:stylesheet>
