package synoptic.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents all of the relation paths through a single trace.
 * 
 * @author timjv
 */
public class Trace {

    /** Relation string -> Representative relation path. */
    private Map<String, RelationPath> paths;

    public Trace() {
        this.paths = new HashMap<String, RelationPath>();
    }

    public void addRelationPath(String relation, EventNode eNode) {
    	if (paths.containsKey(relation)) {
    		throw new IllegalArgumentException("Trace already contains path");
    	}
        paths.put(relation, new RelationPath(eNode, relation,
                Event.defaultTimeRelationString));
    }
    
    public void markRelationPathFinalNode(String relation, EventNode eNode) {
    	RelationPath path = paths.get(relation);
    	path.setFinalNode(eNode);
    }

    public Set<EventType> getSeen(String relation) {
        if (!paths.containsKey(relation)) {
            throw new IllegalArgumentException("Trace doesn't contain the "
                    + relation + " relation");
        }
        return paths.get(relation).getSeen();
    }

    public Map<EventType, Integer> getEventCounts(String relation) {
        if (!paths.containsKey(relation)) {
            throw new IllegalArgumentException("Trace doesn't contain the "
                    + relation + " relation");
        }
        return paths.get(relation).getEventCounts();
    }

    // TODO: Make the return type deeply unmodifiable
    public Map<EventType, Map<EventType, Integer>> getFollowedByCounts(
            String relation) {
        if (!paths.containsKey(relation)) {
            throw new IllegalArgumentException("Trace doesn't contain the "
                    + relation + " relation");
        }
        return paths.get(relation).getFollowedByCounts();
    }

    // TODO: Make the return type deeply unmodifiable
    public Map<EventType, Map<EventType, Integer>> getPrecedesCounts(
            String relation) {
        if (!paths.containsKey(relation)) {
            throw new IllegalArgumentException("Trace doesn't contain the "
                    + relation + " relation");
        }
        return paths.get(relation).getPrecedesCounts();
    }
    
    public boolean hasRelation(String relation) {
    	return paths.containsKey(relation);
    }
    
    public Set<String> getRelations() {
    	return Collections.unmodifiableSet(paths.keySet());
    }

}