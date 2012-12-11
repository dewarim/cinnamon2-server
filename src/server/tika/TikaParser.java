package server.tika;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ConfigEntry;
import server.dao.ConfigEntryDAO;
import server.dao.DAOFactory;
import server.data.ObjectSystemData;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import utils.HibernateSession;
import utils.ParamParser;

import javax.persistence.EntityManager;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;

/**
 * Parse incoming content with Apache Tika and store the result in the object's metadata.
 */
public class TikaParser {

    Logger log = LoggerFactory.getLogger(this.getClass());

    public void parse(ObjectSystemData osd, String repository){
        if(osd == null){
            log.debug("received null osd.");
            return;
        }
        if(osd.getFormat() == null || osd.getFormat().getExtension() == null){
            log.debug("object #"+osd.getId()+" has no defined format - will be ignored.");
            return;
        }
        String tikaBlacklist = getTikaBlacklist();
        String extension = osd.getFormat().getExtension().toLowerCase();
        if (extension.matches(tikaBlacklist)){
            log.debug("Object format "+extension+" is not suitable for tika - will be ignored.");
            return;
        }
        try {
            File content = new File(osd.getFullContentPath(repository));
            TikaConfig tikaConfig = new TikaConfig();
            Metadata tikaMeta = new Metadata();

            String xhtml = parseFile(content, tikaConfig, tikaMeta);
            xhtml = xhtml.replaceAll("xmlns=\"http://www\\.w3\\.org/1999/xhtml\"", "");
            Node resultNode = ParamParser.parseXml(xhtml, "Failed to parse tika-generated xml");
            Document meta = ParamParser.parseXmlToDocument(osd.getMetadata());
            Node oldTikaXml = meta.selectSingleNode("/meta/metaset[@type='tika']");
            if(oldTikaXml != null){
                oldTikaXml.detach();
            }
            Element tikaMetaset = meta.getRootElement().addElement("metaset");
            tikaMetaset.addAttribute("type","tika");
            tikaMetaset.add(resultNode);
            osd.setMetadata(meta.asXML());
            log.debug("set osd.metadata to:\n"+osd.getMetadata());
        }
        catch (Exception e) {
            log.warn("Failed to extract data with tika.", e);
            Document meta = ParamParser.parseXmlToDocument(osd.getMetadata());
            Node oldTikaXml = meta.selectSingleNode("/meta/metaset[@type='tika']");
            if(oldTikaXml != null){
                oldTikaXml.detach();
            }
            Element tikaMetaset = meta.getRootElement().addElement("metaset");
            tikaMetaset.addAttribute("type","tika");
            tikaMetaset.addElement("error").addText(e.getLocalizedMessage());
            osd.setMetadata(meta.asXML());
            log.debug("set osd.metadata to:\n"+osd.getMetadata());
        }
    }

    public String parseFile(File file, TikaConfig tikaConfig, Metadata metadata) throws Exception {
        SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter sw = new StringWriter();
        handler.setResult(new StreamResult(sw));

        Parser parser = new AutoDetectParser(tikaConfig);
        ParseContext pc = new ParseContext();
        try {
            parser.parse(new FileInputStream(file), handler, metadata, pc);
            return sw.toString();
        } catch (Exception e) {
            log.debug("Failed to parse file.", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * You can add a config entry to define formats that should not be parsed by Tika.
     * For example, DITA files are already XML, so you can index / handle them without any
     * further Tika-parsing. (And adding them to the metadata may cause problems due to xml-namespaces)
     * The default blacklist is: "xml|dita|ditamap"
     * @return a String that may be used as a regex to filter for invalid format extensions<br/>
     * Example: may return "dita|xml|foo"
     */
    String getTikaBlacklist(){
        DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
        EntityManager em = HibernateSession.getLocalEntityManager();
        ConfigEntryDAO ceDao = daoFactory.getConfigEntryDAO(em);
        ConfigEntry blacklist = ceDao.findByName("tika.blacklist");
        if(blacklist == null){
            log.debug("Did not find tika.blacklist config entry, returning defaultBlacklist.");
            return defaultBlacklist;
        }
        Node blackNode = ParamParser.parseXmlToDocument(blacklist.getConfig()).selectSingleNode("//blacklist");
        if(blackNode == null){
            log.debug("Did not find blacklist node in tika.blacklist config entry, returning defaultBlacklist.");
            return defaultBlacklist;
        }
        log.debug("Found blacklist: "+blackNode.getText());
        return blackNode.getText();
    }

    public final String defaultBlacklist = "xml|dita|ditamap";
}
