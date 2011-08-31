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

/*
UniFi: A tool for detecting and root-causing bugs in Java programs.
Copyright (C) 2002 Sudheendra Hangal

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

For more details, please see:
http://www.gnu.org/copyleft/gpl.html

*/

package unifi.gui;

import javax.swing.*;
import java.awt.*;

class TextAreaTableCellRenderer extends JTextArea implements javax.swing.table.TableCellRenderer {
public TextAreaTableCellRenderer()
{
    setLineWrap(true);
    setWrapStyleWord(true);
    setOpaque(true);
}

public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
{
    if (isSelected) {
      setForeground(table.getSelectionForeground());
      setBackground(table.getSelectionBackground());
    } else {
      setForeground(table.getForeground());
      setBackground(table.getBackground());
    }
    setFont(table.getFont());
    String str = (value == null) ? "" : value.toString();
    setText(str);
    int len = str.length();

    /* this row computation is approximate */
    int rows = (len / 40) + 2;

    // rows = getLineCount() does not work - gives number
    // of \n's in the input string, not the # lines in the text area
    // display.

    int newRowHeight = getRowHeight() * rows;
    if (rows > 1)
        System.out.println ("getLinecount = " + rows);
    if (newRowHeight > table.getRowHeight())
    {
       // if(newRowHeight > 300)
//            newRowHeight = 250;
        table.setRowHeight(newRowHeight);
    }
    return this;
}
}

