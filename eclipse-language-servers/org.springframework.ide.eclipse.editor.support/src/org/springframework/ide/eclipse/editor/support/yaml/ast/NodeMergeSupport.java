/**
 * Copyright (c) 2008, 2019 https://www.snakeyaml.org and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *
 *   2008 - https://www.snakeyaml.org original api and implementation.
 *   2019 - Copied and modified by Pivotal.
 */
package org.springframework.ide.eclipse.editor.support.yaml.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ide.eclipse.editor.support.reconcile.IProblemCollector;
import org.springframework.ide.eclipse.editor.support.yaml.reconcile.YamlSchemaProblems;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Bits and pieces copied from snakeyaml library to support dealing with '<<' merge
 * nodes.
 * <p>
 * Some modifications made to support our own use-cases.
 */
public class NodeMergeSupport {

	private IProblemCollector problems;

	public NodeMergeSupport(IProblemCollector problems) {
		this.problems = problems;
	}

	/**
	 * Walk the entire tree from given root and deal with any '<<:'
	 * entries found by flattening / merging them into the parent node.
	 *
	 * @param root
	 */
	public void mergeAll(Node root) {
		mergeAll(root, new HashSet<>());
	}

	private void mergeAll(Node root, Set<Node> seen) {
		if (seen.add(root)) {
			if (root instanceof SequenceNode) {
				SequenceNode seq = (SequenceNode) root;
				for (Node node : seq.getValue()) {
					mergeAll(node, seen);
				}
			} else if (root instanceof MappingNode) {
				MappingNode map = (MappingNode) root;
				flattenMapping((MappingNode) root);
				for (NodeTuple entry : map.getValue()) {
					mergeAll(entry.getValueNode(), seen);
				}
			}
		}
	}


    public void flattenMapping(MappingNode node) {
        // perform merging only on nodes containing merge node(s)
        //processDuplicateKeys(node);
        if (node.isMerged()) {
            node.setValue(mergeNode(node, true, new HashMap<Object, Integer>(),
                    new ArrayList<NodeTuple>()));
            node.setMerged(false);
        }
    }

    /**
     * Does merge for supplied mapping node.
     *
     * @param node
     *            where to merge
     * @param isPreffered
     *            true if keys of node should take precedence over others...
     * @param key2index
     *            maps already merged keys to index from values
     * @param values
     *            collects merged NodeTuple
     * @return list of the merged NodeTuple (to be set as value for the
     *         MappingNode)
     */
    private List<NodeTuple> mergeNode(MappingNode node, boolean isPreffered,
            Map<Object, Integer> key2index, List<NodeTuple> values) {
        Iterator<NodeTuple> iter = node.getValue().iterator();
        while (iter.hasNext()) {
            final NodeTuple nodeTuple = iter.next();
            final Node keyNode = nodeTuple.getKeyNode();
            final Node valueNode = nodeTuple.getValueNode();
            if (keyNode.getTag().equals(Tag.MERGE)) {
                iter.remove();
                switch (valueNode.getNodeId()) {
                case mapping:
                    MappingNode mn = (MappingNode) valueNode;
                    mergeNode(mn, false, key2index, values);
                    break;
                case sequence:
                    SequenceNode sn = (SequenceNode) valueNode;
                    List<Node> vals = sn.getValue();
                    for (Node subnode : vals) {
                        if (!(subnode instanceof MappingNode)) {
                        	problems.accept(YamlSchemaProblems.schemaProblem(
                        			"Expected a mapping for merging, but found "+subnode.getNodeId(), subnode
                        	));
                        } else {
	                        MappingNode mnode = (MappingNode) subnode;
	                        mergeNode(mnode, false, key2index, values);
                        }
                    }
                    break;
                default:
                	problems.accept(YamlSchemaProblems.schemaProblem(
                            "Expected a mapping or list of mappings for merging, but found "
                                    + valueNode.getNodeId(),
                            valueNode
                    ));
                }
            } else {
                // we need to construct keys to avoid duplications
                String key = NodeUtil.asScalar(keyNode);
                if (key != null) {
	                if (!key2index.containsKey(key)) { // 1st time merging key
	                    values.add(nodeTuple);
	                    // keep track where tuple for the key is
	                    key2index.put(key, values.size() - 1);
	                } else if (isPreffered) { // there is value for the key, but we
	                                          // need to override it
	                    // change value for the key using saved position
	                    values.set(key2index.get(key), nodeTuple);
	                }
                }
            }
        }
        return values;
    }

}
