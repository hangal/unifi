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
 * SQLTokenMarker.java - Generic SQL token marker
 * Copyright (C) 1999 mike dillon
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

import javax.swing.text.Segment;

/**
 * SQL token marker.
 *
 * @author mike dillon
 * @version $Id: SQLTokenMarker.java,v 1.2 2010/06/18 20:03:43 hangal Exp $
 */
public class SQLTokenMarker extends TokenMarker
{
	private int offset, lastOffset, lastKeyword, length;

	// public members
	public SQLTokenMarker(KeywordMap k)
	{
		this(k, false);
	}

	public SQLTokenMarker(KeywordMap k, boolean tsql)
	{
		keywords = k;
		isTSQL = tsql;
	}

	public byte markTokensImpl(byte token, Segment line, int lineIndex)
	{
		offset = lastOffset = lastKeyword = line.offset;
		length = line.count + offset;

loop:
		for(int i = offset; i < length; i++)
		{
			switch(line.array[i])
			{
			case '*':
				if(token == Token.COMMENT1 && length - i >= 1 && line.array[i+1] == '/')
				{
					token = Token.NULL;
					i++;
					addToken((i + 1) - lastOffset,Token.COMMENT1);
					lastOffset = i + 1;
				}
				else if (token == Token.NULL)
				{
					searchBack(line, i);
					addToken(1,Token.OPERATOR);
					lastOffset = i + 1;
				}
				break;
			case '[':
				if(token == Token.NULL)
				{
					searchBack(line, i);
					token = Token.LITERAL1;
					literalChar = '[';
					lastOffset = i;
				}
				break;
			case ']':
				if(token == Token.LITERAL1 && literalChar == '[')
				{
					token = Token.NULL;
					literalChar = 0;
					addToken((i + 1) - lastOffset,Token.LITERAL1);
					lastOffset = i + 1;
				}
				break;
			case '.': case ',': case '(': case ')':
				if (token == Token.NULL) {
					searchBack(line, i);
					addToken(1, Token.NULL);
					lastOffset = i + 1;
				}
				break;
			case '+': case '%': case '&': case '|': case '^':
			case '~': case '<': case '>': case '=':
				if (token == Token.NULL) {
					searchBack(line, i);
					addToken(1,Token.OPERATOR);
					lastOffset = i + 1;
				}
				break;
			case ' ': case '\t':
				if (token == Token.NULL) {
					searchBack(line, i, false);
				}
				break;
			case ':':
				if(token == Token.NULL)
				{
					addToken((i+1) - lastOffset,Token.LABEL);
					lastOffset = i + 1;
				}
				break;
			case '/':
				if(token == Token.NULL)
				{
					if (length - i >= 2 && line.array[i + 1] == '*')
					{
						searchBack(line, i);
						token = Token.COMMENT1;
						lastOffset = i;
						i++;
					}
					else
					{
						searchBack(line, i);
						addToken(1,Token.OPERATOR);
						lastOffset = i + 1;
					}
				}
				break;
			case '-':
				if(token == Token.NULL)
				{
					if (length - i >= 2 && line.array[i+1] == '-')
					{
						searchBack(line, i);
						addToken(length - i,Token.COMMENT1);
						lastOffset = length;
						break loop;
					}
					else
					{
						searchBack(line, i);
						addToken(1,Token.OPERATOR);
						lastOffset = i + 1;
					}
				}
				break;
			case '!':
				if(isTSQL && token == Token.NULL && length - i >= 2 &&
				(line.array[i+1] == '=' || line.array[i+1] == '<' || line.array[i+1] == '>'))
				{
					searchBack(line, i);
					addToken(1,Token.OPERATOR);
					lastOffset = i + 1;
				}
				break;
			case '"': case '\'':
				if(token == Token.NULL)
				{
					token = Token.LITERAL1;
					literalChar = line.array[i];
					addToken(i - lastOffset,Token.NULL);
					lastOffset = i;
				}
				else if(token == Token.LITERAL1 && literalChar == line.array[i])
				{
					token = Token.NULL;
					literalChar = 0;
					addToken((i + 1) - lastOffset,Token.LITERAL1);
					lastOffset = i + 1;
				}
				break;
			default:
				break;
			}
		}
		if(token == Token.NULL)
			searchBack(line, length, false);
		if(lastOffset != length)
			addToken(length - lastOffset,token);
		return token;
	}

	// protected members
	protected boolean isTSQL = false;

	// private members
	private KeywordMap keywords;
	private char literalChar = 0;

	private void searchBack(Segment line, int pos)
	{
		searchBack(line, pos, true);
	}

	private void searchBack(Segment line, int pos, boolean padNull)
	{
		int len = pos - lastKeyword;
		byte id = keywords.lookup(line,lastKeyword,len);
		if(id != Token.NULL)
		{
			if(lastKeyword != lastOffset)
				addToken(lastKeyword - lastOffset,Token.NULL);
			addToken(len,id);
			lastOffset = pos;
		}
		lastKeyword = pos + 1;
		if (padNull && lastOffset < pos)
			addToken(pos - lastOffset, Token.NULL);
	}
}
