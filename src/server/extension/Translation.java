package server.extension;

import java.io.IOException;
import java.util.*;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.*;
import server.dao.*;
import server.data.ObjectSystemData;
import server.exceptions.CinnamonException;
import server.helpers.ObjectTreeCopier;
import server.interfaces.CommandRegistry;
import server.interfaces.Response;
import server.response.XmlResponse;
import utils.ParamParser;

import javax.persistence.Query;

public class Translation extends BaseExtension {

    static {
        /*
         * Required so BaseExtension can set the API-Class.
         */
        setExtensionClass(Translation.class);
    }

    public CommandRegistry registerApi(CommandRegistry cmdReg) {
        return findAndRegisterMethods(cmdReg, this);
    }

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private static DAOFactory daoFactory = DAOFactory.instance(DAOFactory.HIBERNATE);
    private ObjectTreeCopier objectTreeCopier;
    protected ConfigEntry configEntry;

    /**
     * createTranslation - create a copy of an object (and the whole object tree,
     * if necessary) to be used in a translation process.
     * <br/>
     * Definition: an object tree in this context means all versions of a
     * Cinnamon object.
     * </br>
     * <h1>Creating a translation object</h1>
     * <h2>Find root relation</h2>
     * The client specifies a root relation type with the parameter <em>root_translation_type_id</em>,
     * to define a necessary relation between the
     * source object tree and a translated object tree.<br/>
     * One source tree may have several translations. Each translation object tree is connected
     * via (for example) a relation of the type "root_translation".
     * createTranslation searches for those "root_translation"-relations between
     * the root object of the source object tree and other object trees.
     * A root relation exists if a "root_translation" with the correct
     * <em>root_relation_type_id</em> is found which contains an <em>attribute</em>
     * (an XPath-expression returning a node)
     * with the <em>attribute_value</em>.
     * <h3>Creating a new root relation</h3>
     * If no root relation of the given type and attribute/value parameters exists,
     * the whole source object tree will be copied (without its content and metadata).
     * Each object created this way gets a metadata-node which describes the translation:
     * <pre>
     * {@code
     * 	<meta>
     *      <metaset type="translation_extension">
     * 			<target>
     * 				<!-- here 3 is the id of a "translation_relation_type" -->
     * 				<relation_type_id>3</relation_type_id>
     * 				<attribute_value>en_US</attribute_value>
     * 			</target>
     *     </metaset>
     * </meta>
     * }
     * </pre>
     * If you index this field via appropriate IndexItems, you can search for all documents
     * which do lack content and thus need to be translated to en_US.
     * <h2>Find target node in translation object tree</h2>
     * After the server has found or created the translation object tree, it
     * checks if there exists a node which has the same version as the source object.
     * (Strictly speaking, if we just did create the object tree, this is guaranteed.)
     * If the target node is missing, it will be created (without content, with
     * translation metadata-node).
     * <h2>Check object_relation_type</h2>
     * If the target node already has a relation to the source object with a
     * relation type of <em>object_relation_type_id</em>, an error message
     * is returned because the object is already in place. Otherwise, the
     * corresponding relation will be created and the target node receives the content
     * and metadata of the source (the translation metadata is retained).
     * The method returns the new target node's id as an XML node along with a list of all
     * generated objects (see CmdInterpreter.getObjects):
     * <pre>
     * {@code
     *  <createTranslation><translationId>4</translationId>
     *      <objects><object><id>4</...>
     *  </createTranslation>
     *
     * }
     * </pre>
     * <hr>
     *
     * @param cmd HTTP-Request map with the following parameters:
     *            <ul>
     *            <li>attribute = XPath expression to find a specific attribute node.</li>
     *            <li>attribute_value = The required value of the attribute node.</li>
     *            <li>source_id = the source object which will be translated.</li>
     *            <li>object_relation_type_id = the type of the object relation which will be created
     *            between the source object and the translated object.</li>
     *            <li>root_relation_type_id = the type of relation by which the existence of a
     *            translated object tree can be identified.</li>
     *            <li>[target_folder_id] = optional id of the folder where the translation objects will be created.
     *            <em>This will only be observed if a new target object tree is created!</em>
     *            </ul>
     * @return A Cinnamon Response object.
     */
    @CinnamonMethod(checkTrigger = "true")
    public Response createTranslation(Map<String, String> cmd) {
        try {
            String attribute = cmd.get("attribute");
            String attribute_value = cmd.get("attribute_value");
            String target_folder_id = cmd.get("target_folder_id");

            ObjectSystemData source = getSource(cmd);
            RelationType objectRelationType = getObjectRelationType(cmd);
            RelationType rootRelationType = getRootRelationType(cmd);

            FolderDAO folderDao = daoFactory.getFolderDAO(em);
            Folder targetFolder;
            if (target_folder_id != null) {
                targetFolder = folderDao.get(target_folder_id);
                if (targetFolder == null) {
                    throw new CinnamonException("error.target_folder.not_found");
                }
            } else {
                targetFolder = source.getParent();
            }

            // initialize objectTreeCopier:
            objectTreeCopier = new ObjectTreeCopier(getUser(), targetFolder);
            setAclForTranslations(objectTreeCopier);
            Set<ObjectSystemData> newObjects = new HashSet<ObjectSystemData>();

            // 1. check if the target object tree already exists.
            ObjectSystemData objectTreeRoot = checkRootRelation(source, rootRelationType, attribute, attribute_value);

            String metaNode = String.format("<meta><metaset type='translation_extension'><target><relation_type_id>%d</relation_type_id>"
                + "<attribute_value>%s</attribute_value></target></metaset></meta>",
                objectRelationType.getId(), attribute_value);

            if (objectTreeRoot == null) {
                log.debug("no existing targetObjectTree was found - will grow one.");
                objectTreeRoot = growObjectTree(source, rootRelationType, metaNode, targetFolder, newObjects);
            } else {
                log.debug("Found existing targetObjectTree.");
            }
            log.debug("targetObjectTree (now) exists as: " + objectTreeRoot.getId());

            // 2. Tree already exists, but also the required version?
            // => check version
            ObjectSystemData targetNode = getNodeFromObjectTree(objectTreeRoot, source);
            if (targetNode == null) {
                log.debug("target node does not exist - will create it.");
                targetNode = growTargetNode(objectTreeRoot, source, metaNode, newObjects);
//			log.debug("create relation of type "+objectRelationType.getId());
//			log.debug(" between "+source.getId()+" and "+targetNode.getId());
//			relDao.findOrCreateRelation(objectRelationType, source, targetNode);
            }
            /*
            *  3. now we got a node, but is it new or do we need to fill it
            *  with to-be-translated-content?
            *  => check for object_relation_type-Relation
            */
            RelationDAO relDao = daoFactory.getRelationDAO(em);
            if (relDao.findAllByLeftAndRightAndType(source, targetNode, objectRelationType).size() == 0) {
                log.debug("copy content and metadata for object " + targetNode.getName() + " v" + targetNode.getVersion());
                copyContentAndMetadata(source, targetNode);
                log.debug("Source "+source.getId()+" has metadata: "+source.getMetadata());
                log.debug("Target "+targetNode.getId()+" has metadata: "+targetNode.getMetadata());
                source.copyRelations(targetNode);
                addTranslationMetadata(targetNode, objectRelationType, attribute_value);
                relDao.findOrCreateRelation(objectRelationType, source, targetNode, "");

                ObjectSystemDataDAO oDao = daoFactory.getObjectSystemDataDAO(em);
                List<ObjectSystemData> children = oDao.findAllByPredecessorID(targetNode);
                ObjectSystemData.fixLatestHeadAndBranch(targetNode, children);

                log.debug("fixLatestHeadAndBranch:" +
                        String.format("target: %d is latestHead %s / branch: %s", targetNode.getId(),
                                targetNode.getLatestHead().toString(),
                                targetNode.getLatestBranch().toString()));
                ObjectSystemData predecessor = targetNode.getPredecessor();
                if (predecessor != null) {
                    log.debug("fixLatestHeadAndBranch:" +
                            String.format("predecessor: %d is latestHead: %s / branch: %s", predecessor.getId(),
                                    predecessor.getLatestHead().toString(),
                                    predecessor.getLatestBranch().toString()));
                }
            } else {
                throw new CinnamonException("error.translation_exists");
            }

            XmlResponse resp = new XmlResponse(res);
            Document doc = resp.getDoc();
            Element root = doc.addElement("createTranslation");
            Element translationId = root.addElement("translationId");
            translationId.addText(String.valueOf(targetNode.getId()));
            newObjects.add(targetNode);
            Element objectsNode = root.addElement("objects");
            for (ObjectSystemData object : newObjects) {
                object.toXmlElement(objectsNode);
            }
            return resp;
        } catch (Exception e) {
            log.debug("failed to create translation:", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Load the source object for a create/checkTranslation. Throws an Exception if
     * no object can be found.
     *
     * @param cmd Map with parameter "source_id" of an OSD.
     * @return the OSD
     */
    ObjectSystemData getSource(Map<String, String> cmd) {
        String source_id = cmd.get("source_id");
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData source = osdDao.get(source_id);
        if (source == null) {
            throw new CinnamonException("error.object.not.found");
        }
        return source;
    }

    /**
     * Load the RelationType and return it. If no relation type with this
     * id can be found, throw an exception.
     *
     * @param cmd Map with parameter "object_relation_type_id"
     * @return the RelationType for a source-to-translation object relation.
     */
    RelationType getObjectRelationType(Map<String, String> cmd) {
        String object_relation_type_id = cmd.get("object_relation_type_id");
        RelationTypeDAO relTypeDao = daoFactory.getRelationTypeDAO(em);
        RelationType objectRelationType = relTypeDao.get(object_relation_type_id);
        if (objectRelationType == null) {
            throw new CinnamonException("error.param.object_relation_type_id");
        }
        return objectRelationType;
    }

    /**
     * Load the RelationType and return it. If no relation type with this
     * id can be found, throw an exception.
     *
     * @param cmd Map with parameter "root_relation_type_id"
     * @return the RelationType for a root-source-object to root-translation object relation.
     */
    RelationType getRootRelationType(Map<String, String> cmd) {
        String root_relation_type_id = cmd.get("root_relation_type_id");
        RelationTypeDAO relTypeDao = daoFactory.getRelationTypeDAO(em);
        RelationType rootRelationType = relTypeDao.get(root_relation_type_id);
        if (rootRelationType == null) {
            throw new CinnamonException("error.root_relation_type.not_found");
        }
        return rootRelationType;
    }

    /**
     * Given a relation, check if it points to an object which has an XPath element whose value
     * matches the given attribute value.<br/>
     * The XPath expression is tested against the metadata, the system metadata and the content.
     * The three content categories are the same as those of the Lucene index server.
     *
     * @param relation       the Relation to test
     * @param attribute      the attribute on which to test
     * @param attributeValue the required value
     * @return the object linked to by the matching Relation - or null.
     * @see server.index.LuceneBridge
     */
    ObjectSystemData checkRelation(Relation relation, String attribute, String attributeValue) {
        ObjectSystemData osd = relation.getRight();
        ObjectSystemData objectTreeRoot = null;
        String[] xmlForm = {osd.getMetadata(), osd.getSystemMetadata(), osd.getContent(repository.getName())};
        for (String xml : xmlForm) {
            log.debug("testing: " + attribute + " value: " + attributeValue);
            log.debug("against: " + xml);
            Document doc = ParamParser.parseXmlToDocument(xml, null);
            try {
                Node node = doc.selectSingleNode(attribute);
                if (node != null && node.getText().equals(attributeValue)) {
                    log.debug("found objectTreeRoot:" + osd.getId());
                    objectTreeRoot = osd;
                    break;
                }
            } catch (Exception e) {
                log.warn(String.format(
                        "Exception occurred during translation checking, testing for attribute %s with value %s against %s.",
                        attribute, attributeValue, xml), e);
            }
        }
        return objectTreeRoot;
    }

    /**
     * Copy a root object and all of its descendants.
     * Creates a relation between the root of the original and the copy.
     *
     * @param source           the source object
     * @param rootRelationType type of the relation between the root object of the source and the copy.
     * @param metaNode         all copies in the tree get this as metadata.
     * @param targetFolder     the folder in which the copy will be created.
     * @param newObjects       set in which any new objects will be stored.
     * @return the root object of the new objectTree
     */
    ObjectSystemData growObjectTree(ObjectSystemData source,
                                    RelationType rootRelationType,
                                    String metaNode, Folder targetFolder, Set<ObjectSystemData> newObjects) {
        RelationDAO relDao = daoFactory.getRelationDAO(em);
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        List<ObjectSystemData> allVersions = osdDao.findAllVersions(source);
        List<ObjectSystemData> newTree = new ArrayList<ObjectSystemData>();

        // create copies of all versions:
//		clearEmptyCopies();
        objectTreeCopier = new ObjectTreeCopier(getUser(), targetFolder);
        setAclForTranslations(objectTreeCopier);
        log.debug("create empty copies of all versions");
        for (ObjectSystemData osd : allVersions) {
            log.debug("create empty copy of: " + osd.getId());
            ObjectSystemData emptyCopy = objectTreeCopier.createEmptyCopy(osd);
            log.debug(String.format("Empty copy of %d is %d", osd.getId(), emptyCopy.getId()));
            emptyCopy.setMetadata(metaNode);
            objectTreeCopier.getCopyCache().put(osd, emptyCopy);
            newTree.add(emptyCopy);
            newObjects.add(emptyCopy);
        }
        ObjectSystemData treeRoot = newTree.get(0).getRoot();
        log.debug("treeRoot of objectTree: " + treeRoot.getId());
        log.debug("metadata of treeRoot:" + treeRoot.getMetadata());
        // create root_object_relation:

        log.debug(String.format("create root relation between: %d and %d of type %d",
                source.getRoot().getId(), treeRoot.getId(), rootRelationType.getId()));
        relDao.findOrCreateRelation(rootRelationType, source.getRoot(), treeRoot, "");

        return treeRoot;
    }


    /**
     * Find the target node corresponding to the source. For example, if the client needs
     * a copy of version 4, this code looks if the target object tree already has a translated
     * object with this version. If the object exists, it will be returned. Otherwise, the
     * result is null.
     *
     * @param treeRoot the tree on which the target leaf node may exist.
     * @param source   the source object from which we take the version number that we need
     *                 to determine if there is an already translated leaf node.
     * @return the target OSD node from the object tree, or null if the node was not found
     */
    ObjectSystemData getNodeFromObjectTree(ObjectSystemData treeRoot, ObjectSystemData source) {
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        ObjectSystemData treeNode = osdDao.findByRootAndVersion(treeRoot, source.getVersion());
        log.debug(String.format("Result of trying to fetch version %s from tree with rootId %d: %s",
                source.getVersion(), treeRoot.getId(), treeNode));
        return treeNode;
    }

    /**
     * Create all missing leaves on target object tree and return the requested node.
     *
     * @param treeRoot   the root of the target object tree
     * @param source     the source object which will be copied
     * @param newObjects set in which any new objects will be stored.
     * @param metaNode   the metadata which will be added to all translation objects on this tree.
     * @return the OSD with the same version as the source object
     */
    ObjectSystemData growTargetNode(ObjectSystemData treeRoot, ObjectSystemData source, String metaNode,
                                    Set<ObjectSystemData> newObjects) {
        ObjectSystemDataDAO osdDao = daoFactory.getObjectSystemDataDAO(em);
        List<ObjectSystemData> allVersions = osdDao.findAllVersions(source);
        // find and store existing copies:

        Map<ObjectSystemData, ObjectSystemData> emptyCopies = objectTreeCopier.getCopyCache();
        for (ObjectSystemData osd : allVersions) {
            ObjectSystemData newLeaf = osdDao.findByRootAndVersion(treeRoot, osd.getVersion());
            log.debug(String.format("OSD: %d / newLeaf: %d",
                    osd.getId(), newLeaf != null ? newLeaf.getId() : null));
            emptyCopies.put(osd, newLeaf);
        }

        // create missing leaves:
        for (ObjectSystemData osd : objectTreeCopier.getCopyCache().keySet()) {
            if (emptyCopies.get(osd) == null) {
                ObjectSystemData leaf = objectTreeCopier.createEmptyCopy(osd);
                log.debug(String.format("EmptyCopy of %d is %d",
                        osd.getId(), leaf != null ? leaf.getId() : null));
//				log.debug(String.format("predecessor of %d is %d",
//						leaf.getId(), leaf.getPredecessor() == null ? null : leaf.getPredecessor().getId()));
                if (leaf == null) {
                    log.warn("An empty leaf node was generated!");
                    throw new CinnamonException("error.translation.internal");
                }
                leaf.setMetadata(metaNode);
                newObjects.add(leaf);
                emptyCopies.put(osd, leaf);
            }
        }
        return emptyCopies.get(source);
    }

    /**
     * Copy the content and metadata of the source to the target object.
     *
     * @param source the source OSD whose content needs translation
     * @param target the recipient of the copied content and metadata.
     */
    void copyContentAndMetadata(ObjectSystemData source, ObjectSystemData target) {
        source.copyContent(repository.getName(), target);
        target.setMetadata(source.getMetadata());
    }

    /**
     * Add the translation-metadata node to target object. If the node exists,
     * this just adds the new "target"-node.
     *
     * @param target             the source language object
     * @param objectRelationType the RelationType for this type
     * @param attributeValue     the value by which the client differentiates the translated objects. For example,
     *                           if your source document is of language en-US and the target object is de-DE, you should set
     *                           the attribute value to de-DE.
     */
    void addTranslationMetadata(ObjectSystemData target,
                                RelationType objectRelationType, String attributeValue) {
        Document meta = ParamParser.parseXmlToDocument(target.getMetadata(), "error.invalid.metadata");
        Node translationNode = meta.selectSingleNode("/meta/metaset[@type='translation_extension']");
        Element translation;

        if (translationNode == null) {
            log.debug("no translation node exists - we create one.");
            translation = meta.getRootElement().addElement("metaset");
            translation.addAttribute("type", "translation_extension");
        } else {
            translation = (Element) translationNode;
        }
        Element targetNode = translation.addElement("target");
        targetNode.addElement("relation_type_id").addText(String.valueOf(objectRelationType.getId()));
        targetNode.addElement("attribute_value").addText(attributeValue);
        log.debug("about to set metadata:" + meta.asXML());
        target.setMetadata(meta.asXML());
    }

    /**
     * Check all OSDs linked by relations to the source object, if one of them
     * fulfills the conditions defined in the parameters <em>attribute</em> and
     * <em>attribute_value</em>.
     *
     * @param source           the source object
     * @param rootRelationType the relation type by which a translation node may be linked to the source object.
     * @param attribute        the attribute which is used to select the discriminating attribute value
     * @param attribute_value  the value by which the client differentiates the translated objects. For example,
     *                         if your source document is of language en-US and the target object is de-DE, you should set
     *                         the attribute value to de-DE.
     * @return the target object tree or null.
     */
    ObjectSystemData checkRootRelation(ObjectSystemData source,
                                       RelationType rootRelationType, String attribute, String attribute_value) {

        RelationDAO relDao = daoFactory.getRelationDAO(em);
        List<Relation> relationList = relDao.findAllByLeftAndType(source.getRoot(), rootRelationType);

        // 1. check root relations for attribute and attribute_value
        ObjectSystemData objectTreeRoot = null;
        for (Relation rootRelation : relationList) {
            log.debug("testing relation: " + rootRelation.getId());
            objectTreeRoot = checkRelation(rootRelation, attribute, attribute_value);
            if (objectTreeRoot != null) {
                log.debug("Found root relation");
                break;
            }
        }
        return objectTreeRoot;
    }

    /**
     * Check if there already exists a translation for the given source
     * object.
     * The request contains the following parameters:
     * <p/>
     * <pre>
     * {@code
     * <translation>
     * 	<!-- if the translation object tree exists: -->
     * 	<tree_root_id>1234</tree_root_id>
     *
     *  <!-- if a translation object of the requested version already exists: -->
     *
     *  <target_object_id translated="true">543</target_object_id>
     *  <!-- note: Attribute 'translated' is true if the object has a relation
     *  	of the object_relation_type, false otherwise -->
     * </translation>
     *
     * }
     * </pre>
     *
     * @param cmd the HTTP request parameters as a HashMap:
     *            <ul>
     *            <li>attribute = XPath expression to find a specific attribute node.</li>
     *            <li>attribute_value = The required value of the attribute node.</li>
     *            <li>source_id = the source object which will be translated.</li>
     *            <li>object_relation_type_id = the type of the object relation which will be created
     *            between the source object and the translated object.</li>
     *            <li>root_relation_type_id = the type of relation by which the existence of a
     *            translated object tree can be identified.</li>
     *            </ul>
     * @return an XML response.<br>
     *         This method returns an XML document which may only contain an empty translation node
     *         or more, depending on whether the target translation object already exists and if
     *         it already has an translation relation to the source object.
     */
    @CinnamonMethod
    public Response checkTranslation(Map<String, String> cmd) {
        String attribute = cmd.get("attribute");
        String attribute_value = cmd.get("attribute_value");

        ObjectSystemData source = getSource(cmd);
        RelationType objectRelationType = getObjectRelationType(cmd);
        RelationType rootRelationType = getRootRelationType(cmd);

        XmlResponse resp = new XmlResponse(res);
        Document doc = resp.getDoc();
        Element root = doc.addElement("translation");

        // 1. check if the target object tree already exists.
        ObjectSystemData objectTreeRoot = checkRootRelation(source, rootRelationType, attribute, attribute_value);

        // 2. Tree already exists, but also the required version?
        // => check version
        if (objectTreeRoot != null) {
            root.addElement("tree_root_id").addText(String.valueOf(objectTreeRoot.getId()));
            ObjectSystemData targetNode = getNodeFromObjectTree(objectTreeRoot, source);

            if (targetNode != null) {
                log.debug("targetNode found; id: " + targetNode.getId());
                Element target = root.addElement("target_object_id");
                target.addText(String.valueOf(targetNode.getId()));

                // 3. check if target node has already been translated:
                RelationDAO relDao = daoFactory.getRelationDAO(em);
                if (relDao.findAllByLeftAndRightAndType(source, targetNode, objectRelationType).size() == 0) {
                    // targetNode exists and has no translation relation to source object
                    target.addAttribute("translated", "false");
                } else {
                    // targetNode exists and has a translation relation to source object
                    target.addAttribute("translated", "true");
                }
            } else {
                log.debug(String.format("targetNode for %d was not found.", source.getId()));
            }
        } else {
            log.debug(String.format("No target object tree for %d was found", source.getId()));
        }
        return resp;
    }

    void setAclForTranslations(ObjectTreeCopier otc) {
        ConfigEntryDAO entryDAO = daoFactory.getConfigEntryDAO(em);
        try {
            configEntry = entryDAO.findByName("translation.config");
            if (configEntry == null) {
                log.debug("Could not find configEntry 'translation.config'.");

            } else {
                Node node = configEntry.parseConfig().selectSingleNode("aclForTranslatedObjects");
                if (node != null) {
                    String aclName = node.getText();
                    AclDAO aDao = daoFactory.getAclDAO(em);
                    Acl acl = aDao.findByName(aclName);
                    otc.setAclForCopies(acl);
                    log.debug("setAclForCopies: " + aclName);
                } else {
                    log.debug("node for aclForTranslatedObjects is null");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to setAclForCopies (will use default):", e);
            otc.setAclForCopies(null);
        }
    }
}
