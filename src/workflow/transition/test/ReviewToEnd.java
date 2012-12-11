package workflow.transition.test;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;

import server.data.ObjectSystemData;
import server.global.Constants;
import server.interfaces.Repository;
import utils.ParamParser;

public class ReviewToEnd extends BaseTransition {

	@Override
	public List<ObjectSystemData>  execute(ObjectSystemData task, Node transitionNode, Repository repository) {
		/*
		 * get metadata and extract required params:
		 *  1. document
		 * set current task to done.
		 * set workflow to done. (happens in WorkflowAPI)
		 * set document to reviewed_ok
		 * 
		 */
		Document metadata = ParamParser.parseXmlToDocument(task.getMetadata(), null);
		ObjectSystemData document = getOsdFromMetadata(metadata, "//param[name='document']/value");
		document.setProcstate(Constants.PROCSTATE_REVIEW_OK);		
		task.setProcstate(Constants.PROCSTATE_TASK_DONE);
		
		// no new tasks, return empty list.
		return new ArrayList<ObjectSystemData>();
	}

}
