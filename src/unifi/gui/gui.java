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
import javax.swing.table.*;
import javax.swing.event.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.io.*;
import org.apache.bcel.generic.*;

import unifi.jedit.*;
import unifi.units.FieldUnit;
import unifi.units.Unit;
import unifi.util.Util;
import unifi.*;

public class gui extends JFrame
{

Collection _all_events, _selected_events, _selected_units, _selected_unique_units;
final Vector _events_row_vector = new Vector();
final Vector _units_row_vector = new Vector();
final Vector _unique_units_row_vector = new Vector();

String _col_names[];
double _col_pct_widths[];

public static final String title = "UniFi Units";

JOptionPane _dialog = new JOptionPane ();
JEditTextArea _ta; /* displays source */
JTable _events_table, _units_table, _unique_units_table;
AbstractTableModel _events_tm, _units_tm;

private String[] _source_paths;
JMenuBar _menubar;
JMenu _file_menu, _filter_menu;
JMenuItem _open_file, _reread_item, _quit;
JMenuItem _show_path_item;
MenuSelectionAction _ms_action;

String _currently_displayed_filename; /* this is /path/to/a/b/classname.java */
String _current_file_contents; /* this contains contents of /path/to/a/b/c/classname.java */
String _data_filename; /* this is the file from which invariants/violations are being read */

private Unit _prev_prev_selected_unit, _prev_selected_unit;

public void setup_menus ()
{
    _menubar = new JMenuBar ();

    _file_menu = new JMenu ();

    _open_file = new JMenuItem();
    _reread_item = new JMenuItem();
    _quit = new JMenuItem();
    _show_path_item = new JMenuItem();

    _file_menu.setText ("File");
    _file_menu.setActionCommand ("File");
    _file_menu.setMnemonic ((int)'F');
    _menubar.add (_file_menu);

    _reread_item.setText ("Reread Units ");
    _reread_item.setActionCommand ("Reread Units");
    _reread_item.setAccelerator (KeyStroke.getKeyStroke('R',java.awt.Event.CTRL_MASK,false));
    _file_menu.add (_reread_item);

    _show_path_item.setText ("Show Path");
    _show_path_item.setActionCommand ("Show Path");
    _show_path_item.setMnemonic ((int)'P');
    _file_menu.add (_show_path_item);

    _quit.setText ("Quit");
    _quit.setActionCommand ("Quit");
    _quit.setAccelerator (KeyStroke.getKeyStroke('Q',java.awt.Event.CTRL_MASK,false));
    _file_menu.add (_quit);

    _filter_menu = new JMenu ();

    _filter_menu.setText ("Filter");
    _filter_menu.setActionCommand ("Filter");
    _filter_menu.setMnemonic ((int)'L');
    _menubar.add (_filter_menu);

    _ms_action = new MenuSelectionAction();
    _open_file.addActionListener (_ms_action);
    _quit.addActionListener (_ms_action);
    _show_path_item.addActionListener (_ms_action);
}

// private AbstractTableModel _tm[] = new AbstractTableModel[3];

private void display_all ()
{
    setup_menus ();
    setJMenuBar (_menubar);

    Container contentPane = getContentPane();
    contentPane.setLayout (new  BoxLayout(contentPane, BoxLayout.Y_AXIS));
    setSize (1024,673);

    _ta = new JEditTextArea();
    _ta.setMinimumSize(new Dimension (600,50)); // setting min. size is pretty important, otherwise its set to 600x300 or so
    _ta.setTokenMarker(new JavaTokenMarker());
    parse_outfile(_data_filename);

    String event_col_names[] = {"#", "Where", "Unit1", "Unit2"};
    double event_col_pct_widths[] = {0.04, 0.16, 0.40, 0.40}; // must add up to 1
    _events_table = new MyJTable (_events_row_vector, new UnificationEventSelectionListener(), event_col_names, event_col_pct_widths);

//    String unit_col_names[] = {"#", "Unit", "Class", "Method", "Type"};
//    double unit_col_pct_widths[] = {0.04, 0.46, 0.2, 0.2, 0.1}; // must add up to 1
    String unit_col_names[] = {"#", "Unit", "Type"};
    double unit_col_pct_widths[] = {0.1, 0.8, 0.1}; // must add up to 1
    _units_table = new MyJTable (_units_row_vector, new UnitSelectionListener(), unit_col_names, unit_col_pct_widths);

    // disabling class and method name columns
//    String unique_unit_col_names[] = {"#", "Unit", "#elements", "Class", "Method", "Type"};
    String unique_unit_col_names[] = {"#", "Unit", "#elements", "Type"};
//    double unique_unit_col_pct_widths[] = {0.04, 0.46, 0.1, 0.15, 0.15, 0.1}; // must add up to 1
    double unique_unit_col_pct_widths[] = {0.04, 0.76, 0.1, 0.1}; // must add up to 1
    _unique_units_table = new MyJTable (_unique_units_row_vector, new UniqueUnitSelectionListener(), unique_unit_col_names, unique_unit_col_pct_widths);

    TableSorter ts = (TableSorter) _events_table.getModel();
    _events_tm = (AbstractTableModel) ts.getModel();
    ts = (TableSorter) _units_table.getModel();
    _units_tm = (AbstractTableModel) ts.getModel();

    JScrollPane scroll1 = new JScrollPane(_events_table);
    JScrollPane scroll2 = new JScrollPane(_units_table);
    JSplitPane splitPane1 = new JSplitPane (JSplitPane.VERTICAL_SPLIT, scroll1, scroll2);
    splitPane1.setOneTouchExpandable(true);

    JScrollPane scroll3 = new JScrollPane(_unique_units_table);
    JSplitPane splitPane2 = new JSplitPane (JSplitPane.VERTICAL_SPLIT, _ta, splitPane1);
    splitPane2.setOneTouchExpandable(true);

    JSplitPane splitPane = new JSplitPane (JSplitPane.VERTICAL_SPLIT, splitPane2, scroll3);
    splitPane.setOneTouchExpandable(true);
    splitPane.setResizeWeight(0.5);
    splitPane1.setResizeWeight(0.5);
    splitPane2.setResizeWeight(0.5);
    splitPane.setPreferredSize(new Dimension(1100,800));

    contentPane.add (splitPane);
    splitPane.setDividerLocation (0.35);
    splitPane1.setDividerLocation (0.5);
    splitPane2.setDividerLocation (0.5);
    this.pack();
    this.setVisible(true);
}

public gui(String[] sp, String data_filename)
{
    super (title + " from \"" + data_filename + '"');
    _source_paths = sp;
    _data_filename = data_filename;
    display_all ();
}

public void parse_outfile(String filename)
{
    UnitCollection uc = null;

    if (filename.equals ("Live Run"))
    {
        uc = Unit._current_unit_collection;
        _all_events = _selected_events = uc.get_events();
    }
    else
    {
        // read from a file
        ObjectInputStream ois;
        try { ois= new ObjectInputStream (new FileInputStream (filename)); }
        catch (Exception e)
        {
            System.err.println ("Warning: error opening file: " + filename);
            return;
        }

        try {
    	    uc = (UnitCollection) ois.readObject();
            _all_events = _selected_events = uc.get_events();
	    	ois.close();
        } catch (Exception e)
        {
            System.err.println ("Warning: error reading data from file " + filename);
            System.err.println (e);
	    	Util.ASSERT (false);
        }
    }

    // need the foll. because other code refers to this static later
    Unit._current_unit_collection = uc;
    uc.compute_reps();
    _selected_units = uc.get_units();
    _selected_unique_units = uc.get_all_unique_units_sorted_by_class_size();
//    uc.print_units();

    uc.checkIds();
    initialize_events_row_vector(_selected_events);
    initialize_units_row_vector(_selected_units);
    initialize_unique_units_row_vector(_selected_unique_units, uc);
}

void initialize_events_row_vector(Collection uevents)
{
    _events_row_vector.clear();

    int num = 0;
    for (Iterator it = uevents.iterator() ; it.hasNext(); )
    {
        num++;
        UnificationEvent e = (UnificationEvent) it.next();

        Vector row_data = new Vector(); // row values
        row_data.addElement (new Integer(num));
        if (e instanceof GoldenUnifiEvent)
        	row_data.addElement(((GoldenUnifiEvent) e).get_units().size() + " golden units");
        else
        	row_data.addElement (e.get_bcp());
        row_data.addElement (e.get_unit_a());
        row_data.addElement (e.get_unit_b());
        _events_row_vector.addElement (row_data);
    }
}

void initialize_units_row_vector(Collection units)
{
    _units_row_vector.clear();

    int num = 0;
    for (Iterator it = units.iterator() ; it.hasNext(); )
    {
        num++;
        Unit u = (Unit) it.next();
        // we use a convenience object PairForUnitsTable, which gives us the unit as well as allows us to modify the tostring
        PairForUnitsTable p = new PairForUnitsTable();
        p.unit = u;
        Vector row_data = new Vector(); // row values
        row_data.addElement (new Integer(num));
        row_data.addElement (p);
//        row_data.addElement ("Class");
//        row_data.addElement ("Method");
	Type t = u.getType();
	row_data.addElement ((t != null) ? t.toString() : "");
        _units_row_vector.addElement (row_data);
    }
}

void initialize_unique_units_row_vector(Collection<Unit> unique_units, UnitCollection uc)
{
    _unique_units_row_vector.clear();

    int num = 0;
    for (Unit rep: unique_units)
    {
        num++;

        String displayName = uc.getAllDisplayNames(rep);

        Vector<Object> row_data = new Vector<Object>(); // row values
        row_data.addElement (new Integer(num));
        PairForUniqueUnitsTable p = new PairForUniqueUnitsTable();
        p.unit = rep;
        p.displayName = displayName;
        row_data.addElement (p);
        row_data.addElement (new Integer (uc.get_num_units_for_rep (rep)));
//        row_data.addElement ("Class");  //TOFIX
//        row_data.addElement ("Method"); //TOFIX
        Type t = rep.getType();
        row_data.addElement ((t != null) ? t.toString() : "");
        _unique_units_row_vector.addElement (row_data);
    }
}

/* classname is still with dots, not slashes */
public void display_src (String classname, int lineno)
{
    String s = classname.replace('.', File.separatorChar);
    int x = s.indexOf ('$'); // if inner class drop it
    if (x >= 0)
        s = s.substring (0, x);
    s += ".java";

    readFile (s);
    setTitle (_currently_displayed_filename);
    _ta.setText(_current_file_contents);

    if (lineno > _ta.getLineCount() || lineno < 0)
         lineno = 0;
    _ta.setCaretPosition (_ta.getLineStartOffset (lineno));
    _ta.updateScrollBars ();
}

/* Lookup a file name along all the source paths
   sets _current_file_contents to the contents of the file
   (or an error message if the file is not found)

   if the file is found, the full path to it is returned.
   if not found, an error string is returned.
*/

public void readFile (String filename)
{
    String full_path = null;
    boolean found = false;

    // handle a common case
    String suffix = File.separatorChar + filename;
    if (_currently_displayed_filename != null)
        if (_currently_displayed_filename.endsWith (suffix))
            return;

    for (int i = 0 ; i < _source_paths.length ; i++)
    {
        full_path = _source_paths[i] + File.separatorChar + filename;
        File f = new File (full_path);

        if (f.exists())
        {
            found = true;
            break;
        }
    }

    if (!found)
    {
        String s = "Sorry. unable to find file \"" + filename + '"'
                 + " on source path, which is currently set to:\n";
        for (int i = 0 ; i < _source_paths.length ; i++)
            s += '"' + _source_paths[i] + '"' + "\n" +
                "Define the property unifi.sp to view sources";
        _current_file_contents = s;
        _currently_displayed_filename = "File not found";
        return;
    }

    _currently_displayed_filename = full_path;

    StringBuffer sb = new StringBuffer();

    try {

    LineNumberReader r = new LineNumberReader
                         (new InputStreamReader
                          (new FileInputStream (full_path)));

    while (true)
    {
        String s = r.readLine();
        if (s == null)
            break;

        sb.append (r.getLineNumber() + ": ");
        sb.append (s);
        sb.append ("\n");
    }
    _current_file_contents = sb.toString();
    r.close();
    return;
    } catch (IOException e) {
        _current_file_contents = "Sorry: Exception trying to read file: " + filename + "\n" + e;
        _currently_displayed_filename = "File not found";
        return;
    }
}

class UnificationEventSelectionListener implements ListSelectionListener {

// when a unification event is selected, we display the relevant
// code in the display
public void valueChanged(ListSelectionEvent e)
{
    //Ignore extra messages.
    if (e.getValueIsAdjusting())
        return;

    ListSelectionModel lsm = (ListSelectionModel)e.getSource();

    // if no row is selected, we need to bail out
    if (lsm.isSelectionEmpty ())
        return;

    int selectedRow = lsm.getMinSelectionIndex();

    Object o = ((TableSorter) _events_table.getModel()).getValueAt (selectedRow, 1);
    // o could be a bcp or a direct string (like for golden units)
    if (o instanceof BCP)
    {
    	BCP bcp = (BCP) o;
    	String s = bcp.get_class_name();
    	display_src (s, bcp.get_src_line()-1);
    }
}
}

class UnitSelectionListener implements ListSelectionListener {

public void valueChanged(ListSelectionEvent e)
{
    //Ignore extra messages.
    if (e.getValueIsAdjusting())
        return;

    ListSelectionModel lsm = (ListSelectionModel)e.getSource();

    // if no row is selected, we need to bail out
    if (lsm.isSelectionEmpty ())
        return;

    int selectedRow = lsm.getMinSelectionIndex();

    PairForUnitsTable p = (PairForUnitsTable) ((TableSorter) _units_table.getModel()).getValueAt (selectedRow, 1);
    Unit u = p.unit;
    _prev_prev_selected_unit = _prev_selected_unit;
    _prev_selected_unit = u;

    Collection c = new HashSet();
    c.add (u);
    _selected_events = UnificationEvent.select_events (c);
    initialize_events_row_vector(_selected_events);
    _events_tm.fireTableDataChanged();
}
}

// temp pair data struct for table because we need to have a tostring that
// returns the display name when possible
/*static class PairForUniqueUnitsTable {
	public Unit unit; public String displayName;
	public String toString() {
		return (Util.nullOrEmpty(displayName) ? unit.toString() : displayName);
	}
}*/

//temp pair data struct for table because we need to have a tostring that
//returns the display name when possible


static class PairForUnitsTable {
        public Unit unit; 
        public String displayName;
	public String toString() {
		if (unit.isGolden())
			return "[G" + unit.getGoldenId() + "] " + unit.toString();
		else
			return unit.toString();
	}
}


class UniqueUnitSelectionListener implements ListSelectionListener {


public void valueChanged(ListSelectionEvent e)
{
    //Ignore extra messages.
    if (e.getValueIsAdjusting())
        return;

    ListSelectionModel lsm = (ListSelectionModel)e.getSource();

    // if no row is selected, we need to bail out
    if (lsm.isSelectionEmpty ())
        return;

    int selectedRow = lsm.getMinSelectionIndex();

    PairForUniqueUnitsTable p = (PairForUniqueUnitsTable) ((TableSorter) _unique_units_table.getModel()).getValueAt(selectedRow, 1);
    Unit u = p.unit;
    _selected_units = Unit._current_unit_collection.select_units(u);
    _selected_events = UnificationEvent.select_events (_selected_units);

    initialize_events_row_vector(_selected_events);
    initialize_units_row_vector (_selected_units);
    _events_tm.fireTableDataChanged();
    _units_tm.fireTableDataChanged();
}
}

class MenuSelectionAction implements java.awt.event.ActionListener {

void setup_base(java.awt.event.ActionEvent event)
{
    try
    { //
    } catch(Exception e)
    {
        System.out.println ("exception thrown in uimanager: " + e);
        e.printStackTrace();
    }
}

public void actionPerformed(java.awt.event.ActionEvent event)
{
    Object object = event.getSource();
    if (object == _open_file)
       do_open_file (event);
    if (object == _reread_item)
       do_reread (event);
    if (object == _show_path_item)
       do_show_path (event);
    else if (object == _quit)
       System.exit (0);
// add others as menu items get added
}

void do_open_file (ActionEvent event)
{
    Object o = JOptionPane.showInputDialog (null, "Open source file for class:", "Input", JOptionPane.QUESTION_MESSAGE, null, null, "");

    if (!(o instanceof String))
        return;
    else
        display_src ((String) o, 0);
}

void do_reread (ActionEvent event)
{
    parse_outfile(_data_filename);
    try
    {
//        _events_table.fireTableChanged (new  TableModelEvent (_events_table));
//        _units_table.fireTableChanged (new  TableModelEvent (_units_table));
    } catch(Exception e)
    {
        System.out.println ("Exception in UI " + e);
        e.printStackTrace();
    }
}

void do_show_path(ActionEvent event)
{
    if ((_prev_prev_selected_unit != null) && (_prev_selected_unit != null))
    {
        if (_prev_prev_selected_unit.find() == _prev_selected_unit.find())
        {
            Unit._current_unit_collection.print_path (_prev_prev_selected_unit, _prev_selected_unit);
            _selected_events = Unit._current_unit_collection.find_event_path (_prev_prev_selected_unit, _prev_selected_unit);
            initialize_events_row_vector(_selected_events);
            _events_tm.fireTableDataChanged();
        }
    }
}

}
}
