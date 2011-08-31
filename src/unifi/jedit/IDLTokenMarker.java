/*
UniFi software.
Copyright [2001-2010] Sudheendra Hangal  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 
    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/

package unifi.jedit;

/*
 * IDLTokenMarker.java - IDL token marker
 * Copyright (C) 1999 Slava Pestov
 * Copyright (C) 1999 Juha Lindfors
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

import javax.swing.text.Segment;

/**
 * IDL token marker.
 *
 * @author Slava Pestov
 * @author Juha Lindfors
 * @version $Id: IDLTokenMarker.java,v 1.2 2010/06/18 20:03:43 hangal Exp $
 */
public class IDLTokenMarker extends CTokenMarker
{
	public IDLTokenMarker()
	{
		super(true,getKeywords());
	}

	public static KeywordMap getKeywords()
	{
		if(idlKeywords == null)
		{
			idlKeywords = new KeywordMap(false);

			idlKeywords.add("any",      Token.KEYWORD3);
			idlKeywords.add("attribute",Token.KEYWORD1);
			idlKeywords.add("boolean",  Token.KEYWORD3);
			idlKeywords.add("case",     Token.KEYWORD1);
			idlKeywords.add("char",     Token.KEYWORD3);
			idlKeywords.add("const",    Token.KEYWORD1);
			idlKeywords.add("context",  Token.KEYWORD1);
			idlKeywords.add("default",  Token.KEYWORD1);
			idlKeywords.add("double",   Token.KEYWORD3);
			idlKeywords.add("enum",     Token.KEYWORD3);
			idlKeywords.add("exception",Token.KEYWORD1);
			idlKeywords.add("FALSE",    Token.LITERAL2);
			idlKeywords.add("fixed",    Token.KEYWORD1);
			idlKeywords.add("float",    Token.KEYWORD3);
			idlKeywords.add("in",       Token.KEYWORD1);
			idlKeywords.add("inout",    Token.KEYWORD1);
			idlKeywords.add("interface",Token.KEYWORD1);
			idlKeywords.add("long",     Token.KEYWORD3);
			idlKeywords.add("module",   Token.KEYWORD1);
			idlKeywords.add("Object",   Token.KEYWORD3);
			idlKeywords.add("octet",    Token.KEYWORD3);
			idlKeywords.add("oneway",   Token.KEYWORD1);
			idlKeywords.add("out",      Token.KEYWORD1);
			idlKeywords.add("raises",   Token.KEYWORD1);
			idlKeywords.add("readonly", Token.KEYWORD1);
			idlKeywords.add("sequence", Token.KEYWORD3);
			idlKeywords.add("short",    Token.KEYWORD3);
			idlKeywords.add("string",   Token.KEYWORD3);
			idlKeywords.add("struct",   Token.KEYWORD3);
			idlKeywords.add("switch",   Token.KEYWORD1);
			idlKeywords.add("TRUE",     Token.LITERAL2);
			idlKeywords.add("typedef",  Token.KEYWORD3);
			idlKeywords.add("unsigned", Token.KEYWORD3);
			idlKeywords.add("union",    Token.KEYWORD3);
			idlKeywords.add("void",     Token.KEYWORD3);
			idlKeywords.add("wchar",    Token.KEYWORD3);
			idlKeywords.add("wstring",  Token.KEYWORD3);
		}
		return idlKeywords;
	}

	// private members
	private static KeywordMap idlKeywords;
}
