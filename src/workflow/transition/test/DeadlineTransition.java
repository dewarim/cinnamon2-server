package workflow.transition.test;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import server.data.ObjectSystemData;
import server.global.Constants;
import server.interfaces.Repository;
import utils.ParamParser;

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
		Document metadata = ParamParser.parseXmlToDocument(task.getMetadata(), null);
		Element root = metadata.getRootElement();
		root.addElement("name").addText("deadlinewasreached");
		task.setMetadata(metadata.asXML());
		task.setProcstate(Constants.PROCSTATE_TASK_DONE);
		
		// return empty list.
		return new ArrayList<ObjectSystemData>();
	}

}
