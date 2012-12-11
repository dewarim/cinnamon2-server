package workflow.transition.test;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Node;

import server.data.ObjectSystemData;
import server.global.Constants;
import server.interfaces.Repository;

public class CancelReviewWorkflow  extends BaseTransition{
	
	@Override
	public List<ObjectSystemData>  execute(ObjectSystemData task, Node transitionNode, Repository repository) {
		/*
		 * set task to done. Note: a more complex workflow would need to search
		 * for other tasks and set them to done, too.
		 * 
		 * set workflow to finished. (happens in WorkflowAPI)
		 */

		task.setProcstate(Constants.PROCSTATE_TASK_DONE);
		
		// return empty list.
		return new ArrayList<ObjectSystemData>();
	}
}
