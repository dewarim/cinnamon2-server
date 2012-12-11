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

public class ReviewToEdit extends BaseTransition{

	@Override
	public List<ObjectSystemData>  execute(ObjectSystemData task, Node transitionNode, Repository repository) {
		/*
		 * get metadata and extract required params:
		 * 	1. editor
		 *  2. document
		 * get edit_task-definition from transitionNode
		 * create task for editor.
		 * set fixed params on edit task
		 * set current task to done.
		 * 
		 */
		Document metadata = ParamParser.parseXmlToDocument(task.getMetadata(), null);
		User editor = getUserFromMetadata(metadata, "//param[name='editor']/value");
		ObjectSystemData document = getOsdFromMetadata(metadata, "//param[name='document']/value");
		
		String editTaskName = transitionNode.selectSingleNode("//task[@name='edit_task']").getText();
		ObjectSystemData editTask = createTask(editTaskName, editor, repository.getName()); 
		
		// set 'fixed' params on edit task metadata:
		Document editMeta = ParamParser.parseXmlToDocument(editTask.getMetadata(), null);
		Node documentValueNode = editMeta.selectSingleNode("//input/fixed/param[name='document']/value");
		documentValueNode.setText(String.valueOf(document.getId()));
		editTask.setMetadata(editMeta.asXML());
		
		task.setProcstate(Constants.PROCSTATE_TASK_DONE);
		
		List<ObjectSystemData> newTasks = new ArrayList<ObjectSystemData>();
		newTasks.add(editTask);
		return newTasks;
	}

}
