# Tika

Status: 2013-02-01 #1

This document describes the features provided by the integration of Tika with Cinnamon.

## What is Tika?

> "The [Apache Tika™ toolkit](http://tika.apache.org/) detects and extracts metadata and structured text content from various documents using existing parser libraries."

With the Tika toolkit, a programm can extract the text content from Microsoft Word documents or Excel spreadheets.
This enables a software author to write code that delegates the difficult part of working with several proprietary 
file formats to a well-tested and feature-rich library.

Tika re-uses existing libraries like [Apache POI](http://poi.apache.org/) to parse your content and it 
can be extended for almost any file format you have, provided sufficient documentation on the file structure 
exists.

## Tika and Cinnamon

Unless an uploaded document is already available as XML or otherwise exempt from Tika-parsing,
it will be analyzed by Tika and the results stored in a Cinnamon custom metadata field linked
to the original document.

This metadata field will then be indexed by Lucene, so you can actually find the content, either
by semantic indexing or by scanning the text for full-text search.

Example: from a template for a German business letter in docx-format, Tika will generate the following structure
(slightly edited for better presentation here):

	  <metaset type="tika">
	  <html>
      <head>
        <meta name="Content-Type" content="application/vnd.openxmlformats-officedocument.wordprocessingml.document" />
        <title />
      </head>
      <body>
        <p>
          <img src="embedded:logo.png" alt="" />TEXOLUTION GmbH &amp; Co. KG
		</p>
        <p>Freiheitstraße 34</p>
        <p>D-75045 Walzbachtal</p>
        <p class="angebotstitel">Report</p>
        <h1>Übersicht</h1>
        <h2>Allgemeine Zielsetzung</h2>
        <p class="body_Text">Text</p>		
	  </body>
      </html>
      </metaset>
	  
Headlines from the original document are converted into h1 / h2-tags, and the main text can be found in a
paragraph with class "body_Text". The title of the document is stored in a paragraph with the class "angebotstitel" (proposal title).

If the Lucene indexing is configured properly, a user can now search for "proposal title=Report" and Cinnamon will
find this document. If your MS-Word document contains tables, the indexing can be refined to index the columns / titles and
so on into special fields. This way you can go beyond a simple full-text search and find "all monthly reports with earnings over 10000 €"



