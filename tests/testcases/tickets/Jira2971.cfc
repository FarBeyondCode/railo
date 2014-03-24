<cfscript>
component extends="org.railo.cfml.test.RailoTestCase"	{
	
	//public function beforeTests(){}
	
	//public function afterTests(){}
	
	//public function setUp(){}


	public void function testArrayFilter() localMode="true" {
		_arrayFilter(false);
	}

	public void function testArrayFilterParallel() localMode="true" {
		_arrayFilter(true);
	}

	private void function _arrayFilter(boolean parallel) localMode="true" {
		arr=['a','b','c'];
		//arr[5]='e';
		res=ArrayFilter(arr, function( value ){
 							return value EQ 'b';
 
                        },parallel);

		assertEquals("b",arrayToList(res));
		savecontent variable="c" {
			res=ArrayFilter([1], function( value ){
							echo(serialize(arguments));
 							return true;
 
                        },parallel);
		}
		assertEquals("{'value':1,'2':1,'3':[1]}",c);
	}

		public void function testQueryFilter() localMode="true" {
		_queryFilter(false);
	}

	public void function testQueryFilterParallel() localMode="true" {
		_queryFilter(true);
	}

	private void function _queryFilter(boolean parallel) localMode="true" {
		qry=query(a:["a1","a2"],b:["b1","b2"]);
		//arr[5]='e';
		res=QueryFilter(qry, function( row ){
 							return row.a EQ 'a2';
 
                        },parallel);

		assertEquals("query('a':['a2'],'b':['b2'])",serialize(res));
		
		qry=query(a:["a1"]);
		savecontent variable="c" {
			res=QueryFilter(qry, function(){
							echo(serialize(arguments));
 							return true;
 
                        },parallel);
		}
		assertEquals("{'1':{'a':'a1'},'2':1,'3':query('a':['a1'])}",c);
	}


	public void function testStructFilter() localMode="true" {
		_structFilter(false);
	}

	public void function testStructFilterParallel() localMode="true" {
		_structFilter(true);
	}

	private void function _structFilter(boolean parallel) localMode="true" {
		sct=structNew("linked");
		sct.a=1;
		sct.b=2;
		sct.c=3;

		res=StructFilter(sct, function(key, value ){
 							return key=='b';
 
                        },parallel);

		assertEquals("{'B':2}",serialize(res));
		savecontent variable="c" {
			res=StructFilter({a:1}, function(key, value ){
							echo(serialize(arguments));
 							return key == 'a';
 
                        },parallel);
		}
		assertEquals("{'key':'A','value':1,'3':{'A':1}}",c);
	}




	public void function testFilter() localMode="true" {
		_map(false);
	}

	public void function testFilterParallel() localMode="true" {
		_map(true);
	}

	private void function _map(boolean parallel) localMode="true" {
		arr=["a"];
		it=arr.iterator();



		res=Filter(it, function(value ){
 							return value == 'a';
 
                        },parallel);

		assertEquals("['a']",serialize(res));
		
		it=arr.iterator();

		savecontent variable="c" {
			res=Filter(it, function(value ){
							echo(serialize(arguments));
 							return value == 'a';
 
                        },parallel);
		}
		assertEquals("{'value':'a'}",c);
	}
} 
</cfscript>