package railo.runtime.tag;

import railo.runtime.exp.TagNotSupported;
import railo.runtime.ext.tag.TagImpl;

public final class Applet extends TagImpl {

	/** The text to display if a page containing a Java applet-based cfform control is opened by a browser that does not support Java or has Java support disabled. */
	private String notsupported;

	/** The width of the applet, in pixels. */
	private double width;

	/** The height of the applet, in pixels. */
	private double height;

	/** Alignment */
	private String align;

	/** Space on each side of the applet, in pixels. */
	private double hspace;

	/** The name of a registered parameter for the applet. */
	private String paramn;

	/** Space above and below applet, in pixels. */
	private double vspace;

	/** The form variable name for the applet. */
	private String name;

	/** The name of the registered applet. */
	private String appletsource;


	/**
	* constructor for the tag class
	 * @throws TagNotSupported 
	**/
	public Applet() throws TagNotSupported {
		throw new TagNotSupported("Applet");
	}

	/** set the value notsupported
	*  The text to display if a page containing a Java applet-based cfform control is opened by a browser that does not support Java or has Java support disabled.
	* @param notsupported value to set
	**/
	public void setNotsupported(String notsupported)	{
		this.notsupported=notsupported;
	}

	/** set the value width
	*  The width of the applet, in pixels.
	* @param width value to set
	**/
	public void setWidth(double width)	{
		this.width=width;
	}

	/** set the value height
	*  The height of the applet, in pixels.
	* @param height value to set
	**/
	public void setHeight(double height)	{
		this.height=height;
	}

	/** set the value align
	*  Alignment
	* @param align value to set
	**/
	public void setAlign(String align)	{
		this.align=align;
	}

	/** set the value hspace
	*  Space on each side of the applet, in pixels.
	* @param hspace value to set
	**/
	public void setHspace(double hspace)	{
		this.hspace=hspace;
	}

	/** set the value paramn
	* @param paramn value to set
	**/
	public void setParamn(String paramn)	{
		this.paramn=paramn;
	}

	/** set the value vspace
	*  Space above and below applet, in pixels.
	* @param vspace value to set
	**/
	public void setVspace(double vspace)	{
		this.vspace=vspace;
	}

	/** set the value name
	*  The form variable name for the applet.
	* @param name value to set
	**/
	public void setName(String name)	{
		this.name=name;
	}

	/** set the value appletsource
	*  The name of the registered applet.
	* @param appletsource value to set
	**/
	public void setAppletsource(String appletsource)	{
		this.appletsource=appletsource;
	}


	/**
	* @see javax.servlet.jsp.tagext.Tag#doStartTag()
	*/
	public int doStartTag()	{
		return SKIP_BODY;
	}

	/**
	* @see javax.servlet.jsp.tagext.Tag#doEndTag()
	*/
	public int doEndTag()	{
		return EVAL_PAGE;
	}

	/**
	* @see javax.servlet.jsp.tagext.Tag#release()
	*/
	public void release()	{
		super.release();
		notsupported="";
		width=0d;
		height=0d;
		align="";
		hspace=0d;
		paramn="";
		vspace=0d;
		name="";
		appletsource="";
	}
}