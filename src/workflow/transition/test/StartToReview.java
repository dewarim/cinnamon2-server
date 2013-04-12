package workflow.transition.test;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Node;

import server.User;
import server.data.ObjectSystemData;
import server.global.Constants;
import server.interfaces.Repository;
import utils.ParamParser;
import workflow.transition.BaseTransition;

public class StartToReview extends BaseTransition {
	
	@Override
	public List<ObjectSystemData> execute(ObjectSystemData task, Node transitionNode, Repository repository) {
		/*
		 * get metadata and extract required params:
		 * 	1. reviewer
		 *  2. document
		 * get review_task-definition from transitionNode
		 * create task for reviewer.
		 * set fixed params on review task
		 * set current task to done.
		 * 
		 */
		Document metadata = ParamParser.parseXmlToDocument(task.getMetadata(), null);
		User reviewer = getUserFromMetadata(metadata, "//param[name='reviewer']/value");
		ObjectSystemData document = getOsdFromMetadata(metadata, "//param[name='document']/value");
		
		log.debug("creating review task");
		String reviewTaskName = transitionNode.selectSingleNode("//task[@name='review_task']").getText();
		ObjectSystemData reviewTask = createTask(reviewTaskName, reviewer, repository.getName()); 
		
		// set 'fixed' params on review task metadata:
		Document reviewMeta = ParamParser.parseXmlToDocument(reviewTask.getMetadata(), null);
		Node documentValueNode = reviewMeta.selectSingleNode("//input/fixed/param[name='document']/value");
		documentValueNode.setText(String.valueOf(document.getId()));
//		log.debug("set metadata of reviewTask to:\n"+reviewMeta.asXML());
		reviewTask.setMetadata(reviewMeta.asXML());
		
		List<ObjectSystemData> newTasks = new ArrayList<ObjectSystemData>();
		newTasks.add(reviewTask);
		return newTasks;
	}
	
}
