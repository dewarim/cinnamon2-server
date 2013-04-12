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

/**
 *
 */

public class EditToReview extends BaseTransition {

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
		User editor = getUserFromMetadata(metadata, "//param[name='reviewer']/value");
		ObjectSystemData document = getOsdFromMetadata(metadata, "//param[name='document']/value");
		
		String reviewTaskName = transitionNode.selectSingleNode("//task[@name='review_task']").getText();
		ObjectSystemData reviewTask = createTask(reviewTaskName, editor, repository.getName()); 
		
		// set 'fixed' params on edit task metadata:
		Document editMeta = ParamParser.parseXmlToDocument(reviewTask.getMetadata(), null);
		Node documentValueNode = editMeta.selectSingleNode("//input/fixed/param[name='document']/value");
		documentValueNode.setText(String.valueOf(document.getId()));
		reviewTask.setMetadata(editMeta.asXML());
		
		List<ObjectSystemData> newTasks = new ArrayList<ObjectSystemData>();
		newTasks.add(reviewTask);
		return newTasks;
	}

}
