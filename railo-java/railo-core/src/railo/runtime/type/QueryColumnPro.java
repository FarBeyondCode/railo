package railo.runtime.type;

public interface QueryColumnPro extends QueryColumn,Sizeable {
	public QueryColumnPro cloneColumn(Query query, boolean deepCopy);
	public void setKey(Collection.Key key);
	public QueryColumnPro toDebugColumn();

}
