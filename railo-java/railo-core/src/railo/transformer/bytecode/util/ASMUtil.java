package railo.transformer.bytecode.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import railo.aprint;
import railo.commons.digest.MD5;
import railo.commons.io.IOUtil;
import railo.commons.io.res.Resource;
import railo.commons.lang.StringUtil;
import railo.runtime.component.Property;
import railo.runtime.exp.PageException;
import railo.runtime.net.rpc.AxisCaster;
import railo.runtime.op.Caster;
import railo.runtime.type.dt.TimeSpanImpl;
import railo.runtime.type.util.ArrayUtil;
import railo.transformer.bytecode.Body;
import railo.transformer.bytecode.BytecodeContext;
import railo.transformer.bytecode.BytecodeException;
import railo.transformer.bytecode.Literal;
import railo.transformer.bytecode.Page;
import railo.transformer.bytecode.Position;
import railo.transformer.bytecode.ScriptBody;
import railo.transformer.bytecode.Statement;
import railo.transformer.bytecode.cast.CastBoolean;
import railo.transformer.bytecode.cast.CastDouble;
import railo.transformer.bytecode.cast.CastString;
import railo.transformer.bytecode.expression.ExprDouble;
import railo.transformer.bytecode.expression.ExprString;
import railo.transformer.bytecode.expression.Expression;
import railo.transformer.bytecode.expression.var.Argument;
import railo.transformer.bytecode.expression.var.BIF;
import railo.transformer.bytecode.expression.var.Member;
import railo.transformer.bytecode.expression.var.Variable;
import railo.transformer.bytecode.expression.var.VariableString;
import railo.transformer.bytecode.literal.Identifier;
import railo.transformer.bytecode.literal.LitBoolean;
import railo.transformer.bytecode.literal.LitDouble;
import railo.transformer.bytecode.literal.LitString;
import railo.transformer.bytecode.statement.FlowControl;
import railo.transformer.bytecode.statement.FlowControlBreak;
import railo.transformer.bytecode.statement.FlowControlContinue;
import railo.transformer.bytecode.statement.FlowControlFinal;
import railo.transformer.bytecode.statement.PrintOut;
import railo.transformer.bytecode.statement.TryCatchFinally;
import railo.transformer.bytecode.statement.tag.Attribute;
import railo.transformer.bytecode.statement.tag.Tag;
import railo.transformer.bytecode.statement.tag.TagComponent;
import railo.transformer.bytecode.statement.tag.TagTry;
import railo.transformer.cfml.evaluator.EvaluatorException;

public final class ASMUtil {

	//private static final int VERSION_2=1;
	//private static final int VERSION_3=2;

	public static final short TYPE_ALL=0;
	public static final short TYPE_BOOLEAN=1;
	public static final short TYPE_NUMERIC=2;
	public static final short TYPE_STRING=4;
	
	
	
	
	//private static int version=0;
	
	private final static Method CONSTRUCTOR_OBJECT = Method.getMethod("void <init> ()");
	private static final Method _SRC_NAME = new Method("_srcName",
        			Types.STRING,
        			new Type[]{}
            		);;
	//private static final String VERSION_MESSAGE = "you use an invalid version of the ASM Jar, please update your jar files";
	private static long id=0;
		
	/**
	 * Gibt zur�ck ob das direkt �bergeordnete Tag mit dem �bergebenen Full-Name (Namespace und Name) existiert.
	 * @param el Startelement, von wo aus gesucht werden soll.
	 * @param fullName Name des gesuchten Tags.
	 * @return Existiert ein solches Tag oder nicht.
	 */
	public static boolean hasAncestorTag(Tag tag, String fullName) {
	    return getAncestorTag(tag, fullName)!=null;
	}
	

	/**
	 * Gibt das �bergeordnete CFXD Tag Element zur�ck, falls dies nicht existiert wird null zur�ckgegeben.
	 * @param el Element von dem das parent Element zur�ckgegeben werden soll.
	 * @return �bergeordnete CFXD Tag Element
	 */
	public static Tag getParentTag(Tag tag)	{
		Statement p=tag.getParent();
		if(p==null)return null;
		p=p.getParent();
		if(p instanceof Tag) return (Tag) p;
		return null;
	}

	public static boolean isParentTag(Tag tag,String fullName)	{
		Tag p = getParentTag(tag);
		if(p==null) return false;
		return p.getFullname().equalsIgnoreCase(fullName);
		
	}
	public static boolean isParentTag(Tag tag,Class clazz)	{
		Tag p = getParentTag(tag);
		if(p==null) return false;
		return p.getClass()==clazz;
		
	}
	
	public static boolean hasAncestorBreakFCStatement(Statement stat) {
		return getAncestorBreakFCStatement(stat,null)!=null;
	}
	
	public static boolean hasAncestorContinueFCStatement(Statement stat) {
		return getAncestorContinueFCStatement(stat,null)!=null;
	}
	
	
	/* *
	 * get ancestor LoopStatement 
	 * @param stat
	 * @param ingoreScript 
	 * @return
	 * /
	public static FlowControl getAncestorFlowControlStatement(Statement stat) {
		Statement parent = stat;
		while(true)	{
			parent=parent.getParent();
			if(parent==null)return null;
			if(parent instanceof FlowControl)	{
				if(parent instanceof ScriptBody){
					FlowControl scriptBodyParent = getAncestorFlowControlStatement(parent);
					if(scriptBodyParent!=null) return scriptBodyParent;
					return (FlowControl)parent;
				}
				return (FlowControl) parent;
			}
		}
	}*/
	
	public static FlowControlBreak getAncestorBreakFCStatement(Statement stat, List<FlowControlFinal> finallyLabels) {
		return (FlowControlBreak) getAncestorFCStatement(stat, finallyLabels, FlowControl.BREAK);
	}
	
	public static FlowControlContinue getAncestorContinueFCStatement(Statement stat, List<FlowControlFinal> finallyLabels) {
		return (FlowControlContinue) getAncestorFCStatement(stat, finallyLabels, FlowControl.CONTINUE);
	}

	private static FlowControl getAncestorFCStatement(Statement stat, List<FlowControlFinal> finallyLabels, int flowType) {
		Statement parent = stat;
		FlowControlFinal fcf;
		while(true)	{
			parent=parent.getParent();
			if(parent==null)return null;
			if(
			   (flowType==FlowControl.CONTINUE && parent instanceof FlowControlContinue) || 
			   (flowType==FlowControl.BREAK && parent instanceof FlowControlBreak))	{
				if(parent instanceof ScriptBody){
					List<FlowControlFinal> _finallyLabels=finallyLabels==null?null:new ArrayList<FlowControlFinal>();
					
					FlowControl scriptBodyParent = getAncestorFCStatement(parent,_finallyLabels,flowType);
					if(scriptBodyParent!=null) {
						if(finallyLabels!=null){
							Iterator<FlowControlFinal> it = _finallyLabels.iterator();
							while(it.hasNext()){
								finallyLabels.add(it.next());
							}
						}
						return scriptBodyParent;
					}
					return (FlowControl)parent;
				}
				return (FlowControl) parent;
			}
			
			// only if not last
			if(finallyLabels!=null){
				fcf = parent.getFlowControlFinal();
				if(fcf!=null){
					finallyLabels.add(fcf);
				}
			}
			
		}
	}
	
	public static void leadFlow(BytecodeContext bc,Statement stat, int flowType) throws BytecodeException {
		List<FlowControlFinal> finallyLabels=new ArrayList<FlowControlFinal>();
		
		FlowControl fc = flowType==FlowControl.BREAK?
				ASMUtil.getAncestorBreakFCStatement(stat,finallyLabels):
				ASMUtil.getAncestorContinueFCStatement(stat,finallyLabels);
				
		if(fc==null)
			throw new BytecodeException("break must be inside a loop (for,while,do-while,<cfloop>,<cfwhile> ...)",stat.getStart());
		
		GeneratorAdapter adapter = bc.getAdapter();
		
		Label end=flowType==FlowControl.BREAK?((FlowControlBreak)fc).getBreakLabel():((FlowControlContinue)fc).getContinueLabel();
		
		// first jump to all final labels
		FlowControlFinal[] arr = finallyLabels.toArray(new FlowControlFinal[finallyLabels.size()]);
		if(arr.length>0) {
			FlowControlFinal fcf;
			for(int i=0;i<arr.length;i++){
				fcf=arr[i];
				
				// first
				if(i==0) {
					adapter.visitJumpInsn(Opcodes.GOTO, fcf.getFinalEntryLabel());
				}
				
				// last
				if(arr.length==i+1) fcf.setAfterFinalGOTOLabel(end);
				else fcf.setAfterFinalGOTOLabel(arr[i+1].getFinalEntryLabel());
			}
			
		}
		else bc.getAdapter().visitJumpInsn(Opcodes.GOTO, end);
	}
	
	
	
	public static boolean hasAncestorTryStatement(Statement stat) {
		return getAncestorTryStatement(stat)!=null;
	}
	
	public static Statement getAncestorTryStatement(Statement stat) {
		Statement parent = stat;
		while(true)	{
			parent=parent.getParent();
			if(parent==null)return null;
			
			if(parent instanceof TagTry)	{
				return parent;
			}
			else if(parent instanceof TryCatchFinally)	{
				return parent;
			}
		}
	}
	


	
	/**
	 * Gibt ein �bergeordnetes Tag mit dem �bergebenen Full-Name (Namespace und Name) zur�ck, 
	 * falls ein solches existiert, andernfalls wird null zur�ckgegeben.
	 * @param el Startelement, von wo aus gesucht werden soll.
	 * @param fullName Name des gesuchten Tags.
	 * @return  �bergeornetes Element oder null.
	 */
	public static Tag getAncestorTag(Tag tag, String fullName) {
		Statement parent=tag;
		while(true)	{
			parent=parent.getParent();
			if(parent==null)return null;
			if(parent instanceof Tag)	{
				tag=(Tag) parent;
				if(tag.getFullname().equalsIgnoreCase(fullName))
					return tag;
			}
		}
	}

    /**
     * extract the content of a attribut
     * @param cfxdTag
     * @param attrName
     * @return attribute value
     * @throws EvaluatorException
     */
	public static Boolean getAttributeBoolean(Tag tag,String attrName) throws EvaluatorException {
		Boolean b= getAttributeLiteral(tag, attrName).getBoolean(null);
		if(b==null)throw new EvaluatorException("attribute ["+attrName+"] must be a constant boolean value");
		return b;
    }
    
    /**
     * extract the content of a attribut
     * @param cfxdTag
     * @param attrName
     * @return attribute value
     * @throws EvaluatorException
     */
	public static Boolean getAttributeBoolean(Tag tag,String attrName, Boolean defaultValue) {
		Literal lit=getAttributeLiteral(tag, attrName,null);
		if(lit==null) return defaultValue;
		return lit.getBoolean(defaultValue); 
    }


    /**
     * extract the content of a attribut
     * @param cfxdTag
     * @param attrName
     * @return attribute value
     * @throws EvaluatorException
     */
	public static String getAttributeString(Tag tag,String attrName) throws EvaluatorException {
		return getAttributeLiteral(tag, attrName).getString();
    }
    
    /**
     * extract the content of a attribut
     * @param cfxdTag
     * @param attrName
     * @return attribute value
     * @throws EvaluatorException
     */
	public static String getAttributeString(Tag tag,String attrName, String defaultValue) {
		Literal lit=getAttributeLiteral(tag, attrName,null);
		if(lit==null) return defaultValue;
		return lit.getString(); 
    }
	
	/**
     * extract the content of a attribut
     * @param cfxdTag
     * @param attrName
     * @return attribute value
     * @throws EvaluatorException
     */
	public static Literal getAttributeLiteral(Tag tag,String attrName) throws EvaluatorException {
		Attribute attr = tag.getAttribute(attrName);
		if(attr!=null && attr.getValue() instanceof Literal) return ((Literal)attr.getValue());
        throw new EvaluatorException("attribute ["+attrName+"] must be a constant value");
    }
	
	
    
    /**
     * extract the content of a attribut
     * @param cfxdTag
     * @param attrName
     * @return attribute value
     * @throws EvaluatorException
     */
	public static Literal getAttributeLiteral(Tag tag,String attrName, Literal defaultValue) {
		Attribute attr = tag.getAttribute(attrName);
		if(attr!=null && attr.getValue() instanceof Literal) return ((Literal)attr.getValue());
        return defaultValue; 
    }
	
	
	

	/**
	 * Pr�ft ob das das angegebene Tag in der gleichen Ebene nach dem angegebenen Tag vorkommt.
	 * @param tag Ausgangspunkt, nach diesem tag darf das angegebene nicht vorkommen.
	 * @param nameToFind Tag Name der nicht vorkommen darf
	 * @return kommt das Tag vor.
	 */
	public static boolean hasSisterTagAfter(Tag tag, String nameToFind) {
		Body body=(Body) tag.getParent();
		List<Statement> stats = body.getStatements();
		Iterator<Statement> it = stats.iterator();
		Statement other;
		
		boolean isAfter=false;
		while(it.hasNext()) {
			other=it.next();
			
			if(other instanceof Tag) {
				if(isAfter) {
					if(((Tag) other).getTagLibTag().getName().equals(nameToFind))
					return true;
				}
				else if(other == tag) isAfter=true;
			}
		}
		return false;
	}
	
	/**
	 * Prueft ob das angegebene Tag innerhalb seiner Ebene einmalig ist oder nicht.
	 * @param tag Ausgangspunkt, nach diesem tag darf das angegebene nicht vorkommen.
	 * @return kommt das Tag vor.
	 */
	public static boolean hasSisterTagWithSameName(Tag tag) {
		
		Body body=(Body) tag.getParent();
		List<Statement> stats = body.getStatements();
		Iterator<Statement> it = stats.iterator();
		Statement other;
		String name=tag.getTagLibTag().getName();
		
		while(it.hasNext()) {
			other=it.next();
			if(other != tag && other instanceof Tag && ((Tag) other).getTagLibTag().getName().equals(name))
					return true;
			
		}
		return false;
	}

	/**
	 * remove this tag from his parent body
	 * @param tag
	 */
	public static void remove(Tag tag) {
		Body body=(Body) tag.getParent();
		body.getStatements().remove(tag);
	}

	/**
	 * replace src with trg
	 * @param src
	 * @param trg
	 */
	public static void replace(Tag src, Tag trg, boolean moveBody) {
		trg.setParent(src.getParent());
		
		Body p=(Body) src.getParent();
		List<Statement> stats = p.getStatements();
		Iterator<Statement> it = stats.iterator();
		Statement stat;
		int count=0;
		
		while(it.hasNext()) {
			stat=it.next();
			if(stat==src) {
				if(moveBody && src.getBody()!=null)src.getBody().setParent(trg);
				stats.set(count, trg);
				break;
			}
			count++;
		}
	}
	
	public static Page getAncestorPage(Statement stat) throws BytecodeException {
		Statement parent=stat;
		while(true)	{
			parent=parent.getParent();
			if(parent==null) {
				throw new BytecodeException("missing parent Statement of Statement",stat.getStart());
				//return null;
			}
			if(parent instanceof Page)	return (Page) parent;
		}
	}
	
	public static Page getAncestorPage(Statement stat, Page defaultValue) {
		Statement parent=stat;
		while(true)	{
			parent=parent.getParent();
			if(parent==null) {
				return defaultValue;
			}
			if(parent instanceof Page)	return (Page) parent;
		}
	}
	
	public static void listAncestor(Statement stat) {
		Statement parent=stat;
		aprint.o(stat);
		while(true)	{
			parent=parent.getParent();
			if(parent instanceof Page)aprint.o("page-> "+ ((Page)parent).getSource());
			else aprint.o("parent-> "+ parent);
			if(parent==null) break;
		}
	}
	
	
	public static Tag getAncestorComponent(Statement stat) throws BytecodeException {
		//print.ln("getAncestorPage:"+stat);
		Statement parent=stat;
		while(true)	{
			parent=parent.getParent();
			//print.ln(" - "+parent);
			if(parent==null) {
				throw new BytecodeException("missing parent Statement of Statement",stat.getStart());
				//return null;
			}
			if(parent instanceof TagComponent)
			//if(parent instanceof Tag && "component".equals(((Tag)parent).getTagLibTag().getName()))	
				return (Tag) parent;
		}
	}
	
	public static Statement getRoot(Statement stat) {
		while(true)	{
			if(isRoot(stat))	{
				return stat;
			}
			stat=stat.getParent();
		}
	}



    public static boolean isRoot(Statement statement) { 
    	//return statement instanceof Page || (statement instanceof Tag && "component".equals(((Tag)statement).getTagLibTag().getName()));
    	return statement instanceof Page || statement instanceof TagComponent;
    }
	
	public static void invokeMethod(GeneratorAdapter adapter, Type type, Method method) {
		if(type.getClass().isInterface())
			adapter.invokeInterface(type, method);
		else
			adapter.invokeVirtual(type, method);
	}

    public static byte[] createPojo(String className, ASMProperty[] properties,Class parent,Class[] interfaces, String srcName) throws PageException {
    	className=className.replace('.', '/');
    	className=className.replace('\\', '/');
    	className=railo.runtime.type.List.trim(className, "/");
    	String[] inter=null;
    	if(interfaces!=null){
    		inter=new String[interfaces.length];
    		for(int i=0;i<inter.length;i++){
    			inter[i]=interfaces[i].getName().replace('.', '/');
    		}
    	}
    // CREATE CLASS	
		//ClassWriter cw = new ClassWriter(true);
    	ClassWriter cw = ASMUtil.getClassWriter();
        cw.visit(Opcodes.V1_2, Opcodes.ACC_PUBLIC, className, null, parent.getName().replace('.', '/'), inter);
        String md5;
        try{
    		md5=createMD5(properties);
    	}
    	catch(Throwable t){
    		md5="";
    		t.printStackTrace();
    	}
        
        FieldVisitor fv = cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC, "_md5_", "Ljava/lang/String;", null, md5);
        fv.visitEnd();
        
        
    // Constructor
        GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ACC_PUBLIC,CONSTRUCTOR_OBJECT,null,null,cw);
        adapter.loadThis();
        adapter.invokeConstructor(toType(parent,true), CONSTRUCTOR_OBJECT);
        adapter.returnValue();
        adapter.endMethod();
    
        // properties
        for(int i=0;i<properties.length;i++){
        	createProperty(cw,className,properties[i]);
        }
        
        // complexType src
        if(!StringUtil.isEmpty(srcName)) {
        	GeneratorAdapter _adapter = new GeneratorAdapter(Opcodes.ACC_PUBLIC+Opcodes.ACC_FINAL+ Opcodes.ACC_STATIC , _SRC_NAME, null, null, cw);
        	_adapter.push(srcName);
        	_adapter.returnValue();
        	_adapter.endMethod();
        }
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private static void createProperty(ClassWriter cw,String classType, ASMProperty property) throws PageException {
		String name = property.getName();
		Type type = property.getASMType();
		Class clazz = property.getClazz();
		
		cw.visitField(Opcodes.ACC_PRIVATE, name, type.toString(), null, null).visitEnd();
		
		int load=loadFor(type);
		//int sizeOf=sizeOf(type);
		
    	// get<PropertyName>():<type>
    		Type[] types=new Type[0];
    		Method method = new Method((clazz==boolean.class?"get":"get")+StringUtil.ucFirst(name),type,types);
            GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ACC_PUBLIC , method, null, null, cw);
            
            Label start = new Label();
            adapter.visitLabel(start);
            
            adapter.visitVarInsn(Opcodes.ALOAD, 0);
			adapter.visitFieldInsn(Opcodes.GETFIELD, classType, name, type.toString());
			adapter.returnValue();
			
			Label end = new Label();
			adapter.visitLabel(end);
			adapter.visitLocalVariable("this", "L"+classType+";", null, start, end, 0);
			adapter.visitEnd();
			
			adapter.endMethod();
			
			
			
			
		
		// set<PropertyName>(object):void
			types=new Type[]{type};
			method = new Method("set"+StringUtil.ucFirst(name),Types.VOID,types);
            adapter = new GeneratorAdapter(Opcodes.ACC_PUBLIC , method, null, null, cw);
            
            start = new Label();
            adapter.visitLabel(start);
            adapter.visitVarInsn(Opcodes.ALOAD, 0);
            adapter.visitVarInsn(load, 1);
            adapter.visitFieldInsn(Opcodes.PUTFIELD, classType, name, type.toString());
			
			adapter.visitInsn(Opcodes.RETURN);
			end = new Label();
			adapter.visitLabel(end);
			adapter.visitLocalVariable("this", "L"+classType+";", null, start, end, 0);
			adapter.visitLocalVariable(name, type.toString(), null, start, end, 1);
			//adapter.visitMaxs(0, 0);//.visitMaxs(sizeOf+1, sizeOf+1);// hansx
			adapter.visitEnd();
        
			adapter.endMethod();
			
			
			
			
	}

    public static int loadFor(Type type) {
    	if(type.equals(Types.BOOLEAN_VALUE) || type.equals(Types.INT_VALUE) || type.equals(Types.CHAR) || type.equals(Types.SHORT_VALUE))
    		return Opcodes.ILOAD;
    	if(type.equals(Types.FLOAT_VALUE))
    		return Opcodes.FLOAD;
    	if(type.equals(Types.LONG_VALUE))
    		return Opcodes.LLOAD;
    	if(type.equals(Types.DOUBLE_VALUE))
    		return Opcodes.DLOAD;
    	return Opcodes.ALOAD;
	}

    public static int sizeOf(Type type) {
    	if(type.equals(Types.LONG_VALUE) || type.equals(Types.DOUBLE_VALUE))
    		return 2;
    	return 1;
	}


	/**
     * translate a string cfml type definition to a Type Object
     * @param cfType
     * @param axistype
     * @return
     * @throws PageException
     */
    public static Type toType(String cfType, boolean axistype) throws PageException {
		return toType(Caster.cfTypeToClass(cfType), axistype);
	}

    /**
     * translate a string cfml type definition to a Type Object
     * @param cfType
     * @param axistype
     * @return
     * @throws PageException
     */
    public static Type toType(Class type, boolean axistype) {
		if(axistype)type=AxisCaster.toAxisTypeClass(type);
		return Type.getType(type);	
	}
    

	public static String createMD5(ASMProperty[] props) {
		
		StringBuffer sb=new StringBuffer();
		for(int i=0;i<props.length;i++){
			sb.append("name:"+props[i].getName()+";");
			if(props[i] instanceof Property){
				sb.append("type:"+((Property)props[i]).getType()+";");
			}
			else {
				try {
					sb.append("type:"+props[i].getASMType()+";");
					
				} 
				catch (PageException e) {}
			}
		}
		try {
			return MD5.getDigestAsString(sb.toString());
		} catch (IOException e) {
			return "";
		}
	}



	public static void removeLiterlChildren(Tag tag, boolean recursive) {
		Body body=tag.getBody();
		if(body!=null) {
        	List<Statement> list = body.getStatements();
        	Statement[] stats = list.toArray(new Statement[list.size()]);
        	PrintOut po;
        	Tag t;
        	for(int i=0;i<stats.length;i++) {
            	if(stats[i] instanceof PrintOut) {
            		po=(PrintOut) stats[i];
            		if(po.getExpr() instanceof Literal) {
            			body.getStatements().remove(po);
            		}
            	}
            	else if(recursive && stats[i] instanceof Tag) {
            		t=(Tag) stats[i];
            		if(t.getTagLibTag().isAllowRemovingLiteral()) {
            			removeLiterlChildren(t, recursive);
            		}
            	}
            }
        }
	}


	public synchronized static String getId() {
		if(id<0)id=0;
		return StringUtil.addZeros(++id,6);
	}


	public static boolean isEmpty(Body body) {
		return body==null || body.isEmpty();
	}


	/**
	 * @param adapter
	 * @param expr
	 * @param mode
	 */
	public static void pop(GeneratorAdapter adapter, Expression expr,int mode) {
		if(mode==Expression.MODE_VALUE && (expr instanceof ExprDouble))adapter.pop2();
		else adapter.pop();
	}
	public static void pop(GeneratorAdapter adapter, Type type) {
		if(type.equals(Types.DOUBLE_VALUE))adapter.pop2();
		else if(type.equals(Types.VOID));
		else adapter.pop();
	}


	public static ClassWriter getClassWriter() {
		return new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
		/*if(true) return new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		
		if(version==VERSION_2)
			return new ClassWriter(ClassWriter.COMPUTE_MAXS+ClassWriter.COMPUTE_FRAMES);
		
		try{
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			version=VERSION_2;
			return cw;
		}
		catch(NoSuchMethodError err){
			if(version==0){
				version=VERSION_3;
			}
			
			PrintWriter ew = ThreadLocalPageContext.getConfig().getErrWriter();
			SystemOut.printDate(ew, VERSION_MESSAGE);
			
			try {
				return  ClassWriter.class.getConstructor(new Class[]{boolean.class}).newInstance(new Object[]{Boolean.TRUE});
				
			} 
			catch (Exception e) {
				throw new RuntimeException(Caster.toPageException(e));
				
			}
		}*/
	}

	/*
	 * For 3.1
	 * 
	 * public static ClassWriter getClassWriter() {
		if(version==VERSION_3)
			return new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		try{
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			version=VERSION_3;
			return cw;
		}
		catch(NoSuchMethodError err){
			if(version==0){
				version=VERSION_2;
				throw new RuntimeException(new ApplicationException(VERSION_MESSAGE+
						", after reload this version will work as well, but please update to newer version"));
			}
			
			PrintWriter ew = ThreadLocalPageContext.getConfig().getErrWriter();
			SystemOut.printDate(ew, VERSION_MESSAGE);
			//err.printStackTrace(ew);
			
			try {
				return (ClassWriter) ClassWriter.class.getConstructor(new Class[]{boolean.class}).newInstance(new Object[]{Boolean.TRUE});
				
			} 
			catch (Exception e) {
				throw new RuntimeException(Caster.toPageException(e));
				
			}
		}
	}*/


	public static String createOverfowMethod() {
		return "_call"+ASMUtil.getId();
	}
	
	public static boolean isOverfowMethod(String name) {
		return name.startsWith("_call") && name.length()>=11;
	}


	public static boolean isDotKey(ExprString expr) {
		return expr instanceof LitString && !((LitString)expr).fromBracket();
	}

	public static String toString(Expression exp,String defaultValue) {
		try {
			return toString(exp);
		} catch (BytecodeException e) {
			return defaultValue;
		}
	}
	public static String toString(Expression exp) throws BytecodeException {
		if(exp instanceof Variable) {
			return toString(VariableString.toExprString(exp));
		}
		else if(exp instanceof VariableString) {
			return ((VariableString)exp).castToString();
		}
		else if(exp instanceof Literal) {
			return ((Literal)exp).toString();
		}
		return null;
	}


	public static Boolean toBoolean(Attribute attr, Position start) throws BytecodeException {
		if(attr==null)
			throw new BytecodeException("attribute does not exist",start);
		
		if(attr.getValue() instanceof Literal){
			Boolean b=((Literal)attr.getValue()).getBoolean(null);
			if(b!=null) return b; 
		}
		throw new BytecodeException("attribute ["+attr.getName()+"] must be a constant boolean value",start);
		
		
	}
	public static Boolean toBoolean(Attribute attr, int line, Boolean defaultValue) {
		if(attr==null)
			return defaultValue;
		
		if(attr.getValue() instanceof Literal){
			Boolean b=((Literal)attr.getValue()).getBoolean(null);
			if(b!=null) return b; 
		}
		return defaultValue;	
	}


	public static boolean isCFC(Statement s) {
		Statement p;
		while((p=s.getParent())!=null){
			s=p;
		}
		
		return true;
	}


	
	
	public static boolean isLiteralAttribute(Tag tag, String attrName, short type,boolean required,boolean throwWhenNot) throws EvaluatorException {
		Attribute attr = tag.getAttribute(attrName);
		String strType="/constant";
		if(attr!=null) {
			switch(type){
			case TYPE_ALL:
				if(attr.getValue() instanceof Literal) return true;
			break;
			case TYPE_BOOLEAN:
				if(CastBoolean.toExprBoolean(attr.getValue()) instanceof LitBoolean) return true;
				strType=" boolean";
			break;
			case TYPE_NUMERIC:
				if(CastDouble.toExprDouble(attr.getValue()) instanceof LitDouble) return true;
				strType=" numeric";
			break;
			case TYPE_STRING:
				if(CastString.toExprString(attr.getValue()) instanceof LitString) return true;
				strType=" string";
			break;
			}
			if(!throwWhenNot) return false;
			throw new EvaluatorException("Attribute ["+attrName+"] of the Tag ["+tag.getFullname()+"] must be a literal"+strType+" value");
		}
		if(required){
			if(!throwWhenNot) return false;
			throw new EvaluatorException("Attribute ["+attrName+"] of the Tag ["+tag.getFullname()+"] is required");
		}
		return true;
	}


	public static boolean isRefType(Type type) {
		return 
		!(type==Types.BYTE_VALUE || type==Types.BOOLEAN_VALUE || type==Types.CHAR || type==Types.DOUBLE_VALUE || 
		type==Types.FLOAT_VALUE || type==Types.INT_VALUE || type==Types.LONG_VALUE || type==Types.SHORT_VALUE);
	}


	public static Type toRefType(Type type) {
		if(type==Types.BYTE_VALUE) return Types.BYTE;
		if(type==Types.BOOLEAN_VALUE) return Types.BOOLEAN;
		if(type==Types.CHAR) return Types.CHARACTER;
		if(type==Types.DOUBLE_VALUE) return Types.DOUBLE;
		if(type==Types.FLOAT_VALUE) return Types.FLOAT;
		if(type==Types.INT_VALUE) return Types.INTEGER;
		if(type==Types.LONG_VALUE) return Types.LONG;
		if(type==Types.SHORT_VALUE) return Types.SHORT;
		
		return type;
	}
	
	/**
	 * return value type only when there is one
	 * @param type
	 * @return
	 */
	public static Type toValueType(Type type) {
		if(type==Types.BYTE) return Types.BYTE_VALUE;
		if(type==Types.BOOLEAN) return Types.BOOLEAN_VALUE;
		if(type==Types.CHARACTER) return Types.CHAR;
		if(type==Types.DOUBLE) return Types.DOUBLE_VALUE;
		if(type==Types.FLOAT) return Types.FLOAT_VALUE;
		if(type==Types.INTEGER) return Types.INT_VALUE;
		if(type==Types.LONG) return Types.LONG_VALUE;
		if(type==Types.SHORT) return Types.SHORT_VALUE;
		
		return type;
	}


	public static Class getValueTypeClass(Type type, Class defaultValue) {

		if(type==Types.BYTE_VALUE) return byte.class;
		if(type==Types.BOOLEAN_VALUE) return boolean.class;
		if(type==Types.CHAR) return char.class;
		if(type==Types.DOUBLE_VALUE) return double.class;
		if(type==Types.FLOAT_VALUE) return float.class;
		if(type==Types.INT_VALUE) return int.class;
		if(type==Types.LONG_VALUE) return long.class;
		if(type==Types.SHORT_VALUE) return short.class;
		
		return defaultValue;
	}


	public static ASMProperty[] toASMProperties(Property[] properties) {
		ASMProperty[] asmp=new ASMProperty[properties.length];
		for(int i=0;i<asmp.length;i++){
			asmp[i]=(ASMProperty) properties[i];
		}
		return asmp;
	}
	

	public static boolean containsComponent(Body body) {
		if(body==null) return false;
		
		Iterator<Statement> it = body.getStatements().iterator();
		while(it.hasNext()){
			if(it.next() instanceof TagComponent)return true;
		}
		return false;
	}


	public static void dummy1(BytecodeContext bc) {
		bc.getAdapter().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J");
		bc.getAdapter().visitInsn(Opcodes.POP2);
	}
	public static void dummy2(BytecodeContext bc) {
		bc.getAdapter().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J");
		bc.getAdapter().visitInsn(Opcodes.POP2);
	}


	/**
	 * convert a clas array to a type array
	 * @param classes
	 * @return
	 */
	public static Type[] toTypes(Class<?>[] classes) {
		if(classes==null || classes.length==0) 
			return new Type[0];
		
		Type[] types=new Type[classes.length];
		for(int i=0;i<classes.length;i++)	{
			types[i]=Type.getType(classes[i]);
		}
		return types;
	}


	public static String display(ExprString name) {
		if(name instanceof Literal) {
			if(name instanceof Identifier) 
				return ((Identifier)name).getRaw();
			return ((Literal)name).getString();
			
		}
		return name.toString();
	}


	public static long timeSpanToLong(Expression val) throws EvaluatorException {
		if(val instanceof Literal) {
			Double d = ((Literal)val).getDouble(null);
			if(d==null) throw cacheWithinException();
			return TimeSpanImpl.fromDays(d.doubleValue()).getMillis();
		}
		// createTimespan
		else if(val instanceof Variable) {
			Variable var=(Variable)val;
			if(var.getMembers().size()==1) {
				Member first = var.getFirstMember();
				if(first instanceof BIF) {
					BIF bif=(BIF) first;
					if("createTimeSpan".equalsIgnoreCase(bif.getFlf().getName())) {
						Argument[] args = bif.getArguments();
						int len=ArrayUtil.size(args);
						if(len>=4 && len<=5) {
							double days=toDouble(args[0].getValue());
							double hours=toDouble(args[1].getValue());
							double minutes=toDouble(args[2].getValue());
							double seconds=toDouble(args[3].getValue());
							double millis=len==5?toDouble(args[4].getValue()):0;
							return new TimeSpanImpl((int)days,(int)hours,(int)minutes,(int)seconds,(int)millis).getMillis();
						}
					}
				}
			}
		}
		throw cacheWithinException();
	}



	private static EvaluatorException cacheWithinException() {
		return new EvaluatorException("value of cachedWithin must be a literal timespan, like 0.1 or createTimespan(1,2,3,4)");
	}


	private static double toDouble(Expression e) throws EvaluatorException {
		if(!(e instanceof Literal)) 
			throw new EvaluatorException("Paremeters of the function createTimeSpan have to be literal numeric values in this context");
		Double d = ((Literal)e).getDouble(null);
		if(d==null)
			throw new EvaluatorException("Paremeters of the function createTimeSpan have to be literal numeric values in this context");
		
		return d.doubleValue();
	}

	public static void visitLabel(GeneratorAdapter ga, Label label) {
		if(label!=null) ga.visitLabel(label);
	}
	

	
	public static String getClassName(Resource res) throws IOException{
		byte[] src=IOUtil.toBytes(res);
		ClassReader cr = new ClassReader(src);
		return cr.getClassName();
	}
	
	public static String getClassName(byte[] barr){
		return new ClassReader(barr).getClassName();
	}
	
}
