package workflow.transition.test;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import server.Metaset;
import server.data.ObjectSystemData;
import server.global.Constants;
import server.interfaces.Repository;
import utils.ParamParser;
import workflow.transition.BaseTransition;

public class DeadlineTransition extends BaseTransition {

	@Override
	public List<ObjectSystemData>  execute(ObjectSystemData task, Node transitionNode, Repository repository) {
		/*
		 * set current task to done.
		 * set workflow to done. (happens in WorkflowAPI)
		 * add metadata element "<name>deadline was reached</name>",
		 * which is automatically picked up by the configured test index items.
		 * 
		 */
        Metaset testMs = task.fetchMetaset("test");
        if(testMs == null){
            // if the test metaset is uninitialized, we create a new one:
            // (by taking the easy way and doing setMetadata instead of using MetasetDAO etc) 
            Document metadata = ParamParser.parseXmlToDocument(task.getMetadata());
            Element root = metadata.getRootElement();
            Element newTestMs = root.addElement("metaset");
            newTestMs.addAttribute("type", "test");
            newTestMs.addElement("name").addText("deadlinewasreached");
            task.setMetadata(metadata.asXML());
        }
        else{
            Document metadata = ParamParser.parseXmlToDocument(testMs.getContent(), null);
            Element root = metadata.getRootElement();
            root.addElement("name").addText("deadlinewasreached");
            testMs.setContent(metadata.asXML());
        }
        
		// return empty list.
		return new ArrayList<ObjectSystemData>();
	}

}
