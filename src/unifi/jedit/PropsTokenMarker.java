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
 * PropsTokenMarker.java - Java props/DOS INI token marker
 * Copyright (C) 1998, 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */

import javax.swing.text.Segment;

/**
 * Java properties/DOS INI token marker.
 *
 * @author Slava Pestov
 * @version $Id: PropsTokenMarker.java,v 1.2 2010/06/18 20:03:43 hangal Exp $
 */
public class PropsTokenMarker extends TokenMarker
{
	public static final byte VALUE = Token.INTERNAL_FIRST;

	public byte markTokensImpl(byte token, Segment line, int lineIndex)
	{
		char[] array = line.array;
		int offset = line.offset;
		int lastOffset = offset;
		int length = line.count + offset;
loop:		for(int i = offset; i < length; i++)
		{
			int i1 = (i+1);

			switch(token)
			{
			case Token.NULL:
				switch(array[i])
				{
				case '#': case ';':
					if(i == offset)
					{
						addToken(line.count,Token.COMMENT1);
						lastOffset = length;
						break loop;
					}
					break;
				case '[':
					if(i == offset)
					{
						addToken(i - lastOffset,token);
						token = Token.KEYWORD2;
						lastOffset = i;
					}
					break;
				case '=':
					addToken(i - lastOffset,Token.KEYWORD1);
					token = VALUE;
					lastOffset = i;
					break;
				}
				break;
			case Token.KEYWORD2:
				if(array[i] == ']')
				{
					addToken(i1 - lastOffset,token);
					token = Token.NULL;
					lastOffset = i1;
				}
				break;
			case VALUE:
				break;
			default:
				throw new InternalError("Invalid state: "
					+ token);
			}
		}
		if(lastOffset != length)
			addToken(length - lastOffset,Token.NULL);
		return Token.NULL;
	}

	public boolean supportsMultilineTokens()
	{
		return false;
	}
}
