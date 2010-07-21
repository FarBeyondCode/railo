package railo.runtime.converter;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Node;

import railo.commons.lang.CFTypes;
import railo.runtime.Component;
import railo.runtime.ComponentImpl;
import railo.runtime.ComponentWrap;
import railo.runtime.PageContext;
import railo.runtime.exp.ExpressionException;
import railo.runtime.exp.PageException;
import railo.runtime.java.JavaObject;
import railo.runtime.op.Caster;
import railo.runtime.op.Decision;
import railo.runtime.reflection.Reflector;
import railo.runtime.text.xml.XMLCaster;
import railo.runtime.type.Array;
import railo.runtime.type.Collection.Key;
import railo.runtime.type.KeyImpl;
import railo.runtime.type.ObjectWrap;
import railo.runtime.type.Query;
import railo.runtime.type.Struct;
import railo.runtime.type.StructImpl;
import railo.runtime.type.UDF;
import railo.runtime.type.UDFImpl;
import railo.runtime.type.dt.DateTime;
import railo.runtime.type.dt.DateTimeImpl;
import railo.runtime.type.dt.TimeSpan;
import railo.runtime.type.util.ArrayUtil;
import railo.runtime.type.util.ComponentUtil;

/**
 * class to serialize and desirilize WDDX Packes
 */
public final class JSONConverter {
    

	private static final Key TO_JSON = KeyImpl.getInstance("_toJson");
    private static final Object NULL = new Object();
    private static final String NULL_STRING = "";


	/**
     * constructor of the class
     */
    public JSONConverter() {
    }
	
	
	/**
	 * serialize Serializable class
	 * @param serializable
     * @param sb
	 * @param serializeQueryByColumns 
	 * @throws ConverterException
     */
    
    private void _serializeClass(PageContext pc,Set test,Class clazz,Object obj, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
    	Struct sct=new StructImpl(Struct.TYPE_LINKED);
    	if(test==null)test=new HashSet();
    	
    	// Fields
    	Field[] fields = clazz.getFields();
    	Field field;
    	for(int i=0;i<fields.length;i++){
    		field=fields[i];
    		if(obj!=null || (field.getModifiers()&Modifier.STATIC)>0)
				try {
					sct.setEL(field.getName(), testRecusrion(test,field.get(obj)));
				} catch (Exception e) {
					e.printStackTrace();
				}
    	}
    	if(obj !=null){
	    	// setters
	    	Method[] setters=Reflector.getSetters(clazz);
	    	for(int i=0;i<setters.length;i++){
	    		sct.setEL(setters[i].getName().substring(3), NULL);
	    	}
	    	// getters
	    	Method[] getters=Reflector.getGetters(clazz);
	    	for(int i=0;i<getters.length;i++){
	    		try {
	    			sct.setEL(getters[i].getName().substring(3), testRecusrion(test,getters[i].invoke(obj, ArrayUtil.OBJECT_EMPTY)));
	    			
				} 
	    		catch (Exception e) {}
	    	}
    	}
    	test.add(clazz);
    	
    	
    	_serializeStruct(pc,test,sct, sb, serializeQueryByColumns, true);
    }
    
	
	private Object testRecusrion(Set test, Object obj) {
		if(test.contains(obj.getClass())) return obj.getClass().getName();
		return obj;
	}


	/**
	 * serialize a Date
	 * @param date Date to serialize
	 * @param sb
	 * @throws ConverterException
	 */
	private void _serializeDate(Date date, StringBuffer sb) {
		_serializeDateTime(new DateTimeImpl(date),sb);
	}
	/**
	 * serialize a DateTime
	 * @param dateTime DateTime to serialize
	 * @param sb
	 * @throws ConverterException
	 */
	private void _serializeDateTime(DateTime dateTime, StringBuffer sb) {
		
		sb.append('"');
		
		//sb.append(escape(dateTime.toString()));
		sb.append(escape(JSONDateFormat.format(dateTime)));
		sb.append('"');
		
		/*try {
	        sb.append(goIn());
		    sb.append("createDateTime(");
		    sb.append(DateFormat.call(null,dateTime,"yyyy,m,d"));
		    sb.append(' ');
		    sb.append(TimeFormat.call(null,dateTime,"HH:mm:ss"));
		    sb.append(')');
		} 
	    catch (PageException e) {
			throw new ConverterException(e);
		}*/
	    //Januar, 01 2000 01:01:01
	}

	/**
	 * serialize a Array
	 * @param array Array to serialize
	 * @param sb
	 * @param serializeQueryByColumns 
	 * @throws ConverterException
	 */
	private void _serializeArray(PageContext pc,Set test,Array array, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
		_serializeList(pc,test,array.toList(),sb,serializeQueryByColumns);
	}
	
	/**
	 * serialize a List (as Array)
	 * @param list List to serialize
	 * @param sb
	 * @param serializeQueryByColumns 
	 * @throws ConverterException
	 */
	private void _serializeList(PageContext pc,Set test,List list, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
		
	    sb.append(goIn());
	    sb.append("[");
	    boolean doIt=false;
		ListIterator it=list.listIterator();
		while(it.hasNext()) {
		    if(doIt)sb.append(',');
		    doIt=true;
			_serialize(pc,test,it.next(),sb,serializeQueryByColumns);
		}
		
		sb.append(']');
	}
	private void _serializeArray(PageContext pc,Set test,Object[] arr, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
		
	    sb.append(goIn());
	    sb.append("[");
	    for(int i=0;i<arr.length;i++) {
		    if(i>0)sb.append(',');
		    _serialize(pc,test,arr[i],sb,serializeQueryByColumns);
		}
		sb.append(']');
	}

    /**
     * serialize a Struct
     * @param struct Struct to serialize
     * @param sb
     * @param serializeQueryByColumns 
     * @param addUDFs 
     * @throws ConverterException
     */
    public void _serializeStruct(PageContext pc,Set test,Struct struct, StringBuffer sb, boolean serializeQueryByColumns, boolean addUDFs) throws ConverterException {
        // Component
    	if(struct instanceof Component){
        	String res = castToJson(pc, (Component)struct, NULL_STRING);
        	if(res!=NULL_STRING) {
        		sb.append(res);
        		return;
        	}
        }
    	
    	
    	sb.append(goIn());
        sb.append("{");
        Key[] keys = struct.keys();
        Key key;
        Object value;
        boolean doIt=false;
        for(int i=0;i<keys.length;i++) {
        	key=keys[i];
        	value=struct.get(key,null);
        	if(!addUDFs && value instanceof UDF)continue;
        	if(doIt)sb.append(',');
            doIt=true;
            sb.append('"');
            sb.append(escape(key.getString()));
            sb.append('"');
            sb.append(':');
            _serialize(pc,test,struct.get(key,null),sb,serializeQueryByColumns);
        }
        sb.append('}');
    }
    
    private static String castToJson(PageContext pc,Component cfc, String defaultValue) throws ConverterException {
		Object o=cfc.get(TO_JSON,null);
		if(!(o instanceof UDF)) return defaultValue;
		UDF udf=(UDF) o;
		if(udf.getReturnType()==CFTypes.TYPE_STRING && udf.getFunctionArguments().length==0) {
			try {
				return Caster.toString(cfc.call(pc, TO_JSON, new Object[0]));
			} catch (PageException e) {
				e.printStackTrace();
				throw new ConverterException(e);
			}
		}
		return defaultValue;
    }
    
    

    /**
     * serialize a Map (as Struct)
     * @param map Map to serialize
     * @param sb
     * @param serializeQueryByColumns 
     * @throws ConverterException
     */
    private void _serializeMap(PageContext pc,Set test,Map map, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
        sb.append(goIn());
        sb.append("{");
        
        Iterator it=map.keySet().iterator();
        boolean doIt=false;
        while(it.hasNext()) {
            Object key=it.next();
            if(doIt)sb.append(',');
            doIt=true;
            sb.append('"');
            sb.append(escape(key.toString()));
            sb.append('"');
            sb.append(':');
            _serialize(pc,test,map.get(key),sb,serializeQueryByColumns);
        }
        
        sb.append('}');
    }
    /**
     * serialize a Component
     * @param component Component to serialize
     * @param sb
     * @param serializeQueryByColumns 
     * @throws ConverterException
     */
    private void _serializeComponent(PageContext pc,Set test,Component component, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
    	try {
			ComponentImpl ci = ComponentUtil.toComponentImpl(component);
			ComponentWrap cw = new ComponentWrap(Component.ACCESS_PRIVATE,ci);
	    	_serializeStruct(pc,test,cw, sb, serializeQueryByColumns,false);
		} 
    	catch (ExpressionException e) {
			throw new ConverterException(e);
		}
    }
    

    private void _serializeUDF(PageContext pc,Set test,UDF udf, StringBuffer sb,boolean serializeQueryByColumns) throws ConverterException {
		Struct sct=new StructImpl();
		try {
			// Meta
			Struct meta = udf.getMetaData(pc);
			sct.setEL("Metadata", meta);
			
			// Parameters
			sct.setEL("MethodAttributes", meta.get("PARAMETERS"));
		} 
		catch (PageException e) {
			throw new ConverterException(e);
		}
		
		sct.setEL("Access", ComponentUtil.toStringAccess(udf.getAccess(),"public"));
		sct.setEL("Output", Caster.toBoolean(udf.getOutput()));
		sct.setEL("ReturnType", udf.getReturnTypeAsString());
		try{
			sct.setEL("PagePath", ((UDFImpl)udf).getPageSource().getFile().getAbsolutePath());
		}catch(Throwable t){}
		
		_serializeStruct(pc,test,sct, sb, serializeQueryByColumns, true);
		// TODO key SuperScope and next?
	}

    

	/**
	 * serialize a Query
	 * @param query Query to serialize
	 * @param sb
	 * @param serializeQueryByColumns 
	 * @throws ConverterException
	 */
	private void _serializeQuery(PageContext pc,Set test,Query query, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
		
		String[] keys = query.keysAsString();
		sb.append(goIn());
		sb.append("{");
		
		/*

{"DATA":[["a","b"],["c","d"]]}
{"DATA":{"aaa":["a","c"],"bbb":["b","d"]}}
		 * */
		// Rowcount
		if(serializeQueryByColumns){
			sb.append("\"ROWCOUNT\":");
			sb.append(Caster.toString(query.getRecordcount()));
			sb.append(',');
		}
		
		// Columns
		sb.append("\"COLUMNS\":[");
		String[] cols = query.getColumns();
		for(int i=0;i<cols.length;i++) {
			if(i>0)sb.append(",\"");
			else sb.append('"');
            sb.append(escape(cols[i].toUpperCase()));
            sb.append('"');
		}
		sb.append("],");
		
		// Data
		sb.append("\"DATA\":");
		if(serializeQueryByColumns) {
			sb.append('{');
			boolean oDoIt=false;
			int len=query.getRecordcount();
			for(int i=0;i<keys.length;i++) {
			    if(oDoIt)sb.append(',');
			    oDoIt=true;
			    sb.append(goIn());
	            sb.append('"');
	            sb.append(escape(keys[i]));
	            sb.append('"');
				sb.append(":[");
				boolean doIt=false;
					for(int y=1;y<=len;y++) {
					    if(doIt)sb.append(',');
					    doIt=true;
					    try {
							_serialize(pc,test,query.getAt(keys[i],y),sb,serializeQueryByColumns);
						} catch (PageException e) {
							_serialize(pc,test,e.getMessage(),sb,serializeQueryByColumns);
						}
					}
				
				sb.append(']');
			}
	
			sb.append('}');
		}
		else {
			sb.append('[');
			boolean oDoIt=false;
			int len=query.getRecordcount();
			for(int row=1;row<=len;row++) {
			    if(oDoIt)sb.append(',');
			    oDoIt=true;
	
				sb.append("[");
				boolean doIt=false;
					for(int col=0;col<keys.length;col++) {
					    if(doIt)sb.append(',');
					    doIt=true;
					    try {
							_serialize(pc,test,query.getAt(keys[col],row),sb,serializeQueryByColumns);
						} catch (PageException e) {
							_serialize(pc,test,e.getMessage(),sb,serializeQueryByColumns);
						}
					}
				sb.append(']');
			}
			sb.append(']');
		}
		sb.append('}');
	}
	
	/**
	 * serialize a Object to his xml Format represenation
	 * @param object Object to serialize
	 * @param sb StringBuffer to write data
	 * @param serializeQueryByColumns 
	 * @throws ConverterException
	 */
	private void _serialize(PageContext pc,Set test,Object object, StringBuffer sb, boolean serializeQueryByColumns) throws ConverterException {
		
		// NULL
		if(object==null) {
		    sb.append(goIn());
		    sb.append("null");
		}
		else if(object==NULL) {
		    sb.append(goIn());
		    sb.append("null");
		}
		// String
		else if(object instanceof String || object instanceof StringBuffer) {
		    sb.append(goIn());
		    sb.append('"');
		    sb.append(escape(object.toString()));
		    sb.append('"');
		}
		// Character
		else if(object instanceof Character) {
		    sb.append(goIn());
		    sb.append('"');
		    sb.append(escape(String.valueOf(((Character)object).charValue())));
		    sb.append('"');
		}
		// Number
		else if(object instanceof Number) {
		    sb.append(goIn());
		    sb.append(Caster.toString(((Number)object).doubleValue()));
		}
		// Boolean
		else if(object instanceof Boolean) {
		    sb.append(goIn());
		    sb.append(Caster.toString(((Boolean)object).booleanValue()));
		}
		// DateTime
		else if(object instanceof DateTime) {
			_serializeDateTime((DateTime)object,sb);
		}
		// Date
		else if(object instanceof Date) {
			_serializeDate((Date)object,sb);
		}
        // Component
        else if(object instanceof Component) {
            _serializeComponent(pc,test,(Component)object,sb,serializeQueryByColumns);
        }
        // UDF
        else if(object instanceof UDF) {
            _serializeUDF(pc,test,(UDF)object,sb,serializeQueryByColumns);
        }
        // XML
        else if(object instanceof Node) {
        	_serializeXML((Node)object,sb);
        }
        // Struct
        else if(object instanceof Struct) {
        	_serializeStruct(pc,test,(Struct)object,sb,serializeQueryByColumns,true);
        }
        // Map
        else if(object instanceof Map) {
            _serializeMap(pc,test,(Map)object,sb,serializeQueryByColumns);
        }
		// Array
		else if(object instanceof Array) {
			_serializeArray(pc,test,(Array)object,sb,serializeQueryByColumns);
		}
		// List
		else if(object instanceof List) {
			_serializeList(pc,test,(List)object,sb,serializeQueryByColumns);
		}
        // Query
        else if(object instanceof Query) {
            _serializeQuery(pc,test,(Query)object,sb,serializeQueryByColumns);
        }
        // Timespan
        else if(object instanceof TimeSpan) {
        	_serializeTimeSpan((TimeSpan) object,sb);
        }
		// String Converter
		else if(object instanceof ScriptConvertable) {
		    sb.append(((ScriptConvertable)object).serialize());
		}
		// File
		else if(object instanceof File) {
			_serialize(pc,test, ((File)object).getAbsolutePath(), sb, serializeQueryByColumns);
		}
		// Native Array
		else if(Decision.isNativeArray(object)){
			if(object instanceof char[])
				_serialize(pc,test,new String((char[])object), sb, serializeQueryByColumns);
			else {
				_serializeArray(pc,test,ArrayUtil.toReferenceType(object,ArrayUtil.OBJECT_EMPTY), sb, serializeQueryByColumns);
			}
				
		}
		// ObjectWrap
		else if(object instanceof ObjectWrap) {
			try {
				_serialize(pc,test,((ObjectWrap)object).getEmbededObject(), sb, serializeQueryByColumns);
			} catch (PageException e) {
				if(object instanceof JavaObject){
					_serializeClass(pc,test,((JavaObject)object).getClazz(),null,sb,serializeQueryByColumns);
				}
				else throw new ConverterException("can't serialize Object of type [ "+Caster.toClassName(object)+" ]");
			}
		}
		
		else  {
			_serializeClass(pc,test,object.getClass(),object,sb,serializeQueryByColumns);
			
		}
		
	}

	private void _serializeXML(Node node, StringBuffer sb) {
    	node=XMLCaster.toRawNode(node);
    	sb.append(goIn());
	    sb.append('"');
	    sb.append(escape(XMLCaster.toString(node,"")));
	    sb.append('"');
    	
	}


	private void _serializeTimeSpan(TimeSpan span, StringBuffer sb) {
    	
	        sb.append(goIn());
		    sb.append("createTimeSpan(");
		    sb.append(span.getDay());
		    sb.append(',');
		    sb.append(span.getHour());
		    sb.append(',');
		    sb.append(span.getMinute());
		    sb.append(',');
		    sb.append(span.getSecond());
		    sb.append(')');
		
	}

	
	public static String escape(String str) {
		char[] arr=str.toCharArray();
		StringBuffer rtn=new StringBuffer(arr.length);
		for(int i=0;i<arr.length;i++) {
			if(arr[i] < 128){
				switch(arr[i]) {
					case '\\': rtn.append("\\\\"); break;
					case '/': rtn.append("\\/"); break;
					case '\n': rtn.append("\\n"); break;
					case '\r': rtn.append("\\r"); break;
					case '\f': rtn.append("\\f"); break;
					case '\b': rtn.append("\\b"); break;
					case '\t': rtn.append("\\t"); break;
					case '"' : rtn.append("\\\""); break;
					default : rtn.append(arr[i]); break;
				}
			}
			else {
				if (arr[i] < 0x10)			rtn.append("\\u000");
			    else if (arr[i] < 0x100) 	rtn.append( "\\u00");
			    else if (arr[i] < 0x1000) 	rtn.append( "\\u0");
			    else 						rtn.append( "\\u");
				rtn.append(Integer.toHexString(arr[i]));
			}
		}
		return rtn.toString();
	}

    /**
	 * serialize a Object to his literal Format
	 * @param object Object to serialize
     * @param serializeQueryByColumns 
	 * @return serialized wddx package
	 * @throws ConverterException
	 */
	public String serialize(PageContext pc,Object object, boolean serializeQueryByColumns) throws ConverterException {
		StringBuffer sb=new StringBuffer();
		_serialize(pc,null,object,sb,serializeQueryByColumns);
		return sb.toString();
	}
	
	
	/**
	 * @return return current blockquote
	 */
	private String goIn() {
	    return "";
	}


}