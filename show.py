from __future__ import nested_scopes

import java         # import what we need
import javax.swing
import com
import re

import unifi.gui;

import javax.swing;
import javax.swing.table;
import javax.swing.event;
    
import java.awt;
import java.awt.event
import java.util
import java.util.List
import java.io

import unifi.jedit

import unifi.units.Unit as Unit
import unifi.util.Util
from unifi import UnificationEvent 
from unifi import GoldenUnifiEvent 
import unifi.UnitCollection
import unifi;

from java.lang import System

from unifi.gui import GuiPanel 
from unifi.gui import GuessCallback

from TermMiner import *

import sys

if sys.exec_prefix.rfind('/src') >= 0:
    prefix = sys.exec_prefix[:sys.exec_prefix.rfind('/src')]
    sys.path.insert(0, prefix + '/gython/Lib/')
else:
    sys.path.insert(0, sys.exec_prefix + '/gython/Lib/')
print "sys.path:", sys.path

#from com.hp.hpl.guess import Edge

def replaceSpecialChars(inStr):
    inStr = inStr.replace(".BSLASH.", "\\")
    inStr = inStr.replace(".SLASH.", "/")
    inStr = inStr.replace(".LT.", "<")
    inStr = inStr.replace(".GT.", ">")
    inStr = inStr.replace(".QUOTE.", "'")
    inStr = inStr.replace(".STAR.", "*")
    inStr = inStr.replace(".AND.", "&")
    return inStr

def pickFrequent2or3(terms, freq, weightedNumVars):
    picked = []
    cutoff = weightedNumVars* 0.025
    ceiling = weightedNumVars* 0.8
    for t in terms:
        if freq[t] > cutoff and freq[t] < ceiling:
            picked.append((t, freq[t]))

    #print "picked frequent:", picked, "terms:", terms
    picked.sort(lambda x,y:y[1] - x[1])
    picked = map(lambda x:x[0], picked)
    if len(picked) >= 3:
        return picked[:3]
    return picked[:2]

from  edu.umd.cs.piccolo.event import PBasicInputEventHandler
class LegendClickHandler(PBasicInputEventHandler):
    def __init__(self, hullLegendNode, hull, anno):
        self.hullLegendNode = hullLegendNode
        self.hull = hull
        self.annotation = anno
        self.mousePressedPosition = None

###    def mousePressed(self, event):
###        PBasicInputEventHandler.mousePressed(self, event)
###        self.mousePressedPosition = event.getPosition()
###
###    def mouseDragged(self, event):
###        PBasicInputEventHandler.mouseDragged(self, event)
###
###        #delta = event.getDeltaRelativeTo(self.annotation)
###        #self.ann.translate(delta.width, delta.height)
###        #event.setHandled(true)

    def mouseClicked(self, event):
        PBasicInputEventHandler.mouseClicked(self, event)

        color = self.hull.getColor()

        visible = self.hull.getVisible()
        if visible:
            self.hull.setVisible(false)
        else:
            self.hull.setVisible(true)

###    def mouseReleased(self, event):
###        PBasicInputEventHandler.mouseReleased(self, event)
###
###        if self.mousePressedPosition:
###            p = event.getPosition()
###            if abs(p.getX()-self.mousePressedPosition.getX()) < 5 and \
###                    abs(p.getY()-self.mousePressedPosition.getY()) < 5:
###                color = self.hull.getColor()
###
###                visible = self.hull.getVisible()
###                if visible:
###                    self.hull.setVisible(false)
###                else:
###                    self.hull.setVisible(true)

def setNodeLabelToField(node):
    fullField = node.classname
    assert fullField.find("Field") >= 0

    idx = fullField.find(",")
    fullField = fullField[:idx]
    node.color = '160,0,0'
    node.label = fullField.split('.').pop()
    node.labelvisible = true

class GythonCallback(GuessCallback):
    
    def __init__(self, sourceViewer, termMiner):
        self.path = []
        self.showPathOnlyFlag = 0
        self.hulls = []
        self.fieldToNode = {}
        self.varToNode = {}
        self.lvToNode = {}
        self.rvToNode = {}
        self.paramRvToNode = {}

        self.termToNodes = {}
        self.termFreq = {}

        self.legend = None
        self.miner = termMiner
        self.viewer = sourceViewer
        #self.initInteractionStatus()

        self._initHullColor()

    def initInteractionStatus(self):
        global sourceViewer

        self.path = []
        sourceViewer.panel.currSelectedUnit = None
        sourceViewer.panel.prevSelectedUnit = None
        for hull in self.hulls:
            removeConvexHull(hull)
        self.hulls = []
        self.fieldToNode = {}
        self.varToNode = {}
        self.lvToNode = {}
        self.paramRvToNode = {}

        self._initHullColor()

        self.showPathOnlyFlag = 0
        if self.legend:
            self.legend.removeAll()

    def resizeNodes(self):
        for n in g.nodes:
            # Constant, Field, Method Param/RVs
            #if n.color not in ["white", "red", "orange", "green"]:
            #    n.style = 1
            if n.color in ["white", "red"]:
                n.size=20
            elif n.color in ["orange"]:
                n.size=16
            elif n.color in ["green"]:
                n.size=14
            else:  # golden units
                n.size=25
                RECT=1
                n.style = RECT

    def graphChanged(self, gmlfilename, repName):
        global sourceViewer
        
        print "graphChanged called!"

        self.initInteractionStatus()

        if len(g.nodes) > 0:
            g.removeComplete(g.nodes)

        g.makeFromGML(gmlfilename)

        print "makeFromGML finished!"
        """
        while 1:
            toremove = []
            for n in g.nodes:
                if n.indegree == 0 and not unit(n).isGolden():
                    toremove.append(n)
                if n.indegree == 1 and not unit(n).isGolden():
                    toremove.append(n)
            g.removeComplete(toremove)
            if len(toremove) == 0:
                break
        """

        print "Removing boring branches(in Python) finished!"
        
        gemLayout(41)  #always use same seed 41

        self.resizeNodes()

        constants = {}
        fields = []
        for n in g.nodes:
            if n.color in ["white"]:
                const = str(n)
                const = replaceSpecialChars(const)
                const = const[len("Constant"):const.find("in class")]
                const = const.strip()
                occurrence = constants.get(const, 0)       
                occurrence += 1
                constants[const] = occurrence
            elif n.color in ["red"]:
                setNodeLabelToField(n)

                field = str(n)
                field = field.strip()
                fields.append(field)
                self.fieldToNode[field] = n
                self.varToNode[field] = n
            elif n.color == "green":
                lv = str(n)
                #XXX(jiwon): treat Length of Field just like local variable.
                #            See also 
                if lv.find("Length of Field") >= 0:
                    n.color = "red"
                    setNodeLabelToField(n)
                    self.fieldToNode[lv] = n
                    self.varToNode[lv] = n
                    continue
                if lv.find("LV ") < 0: continue

                self.lvToNode[lv] = n
                self.varToNode[lv] = n
            elif n.color == "orange":
                paramOrRV = str(n)
                self.paramRvToNode[paramOrRV] = n
                self.varToNode[paramOrRV] = n

        #print "\n\n\n---------------- All Constants --------------------"
        """
        for k, v in constants.items():
            print k,": ", v

        print "\n---------------- All Fields --------------------"
        for f in fields:
            print f
        """

        self.termFreq, self.termToNodes = self.processTerms()

        self.viewer.currentGraphRep = repName

        self.createConvexHullsForGraph()

        sourceViewer.source = gmlfilename

    def processTerms(self):
        termFreq={}
        termToNodes={}
        for f in self.fieldToNode.keys():
            fullField = f.split(',')[0]
            terms = getTermsFromFullFieldName(fullField)

            for t in terms:
                cnt = termFreq.get(t, 0)
                termFreq[t] = cnt + FIELD_WEIGHT
                nodes = termToNodes.get(t, [])
                nodes.append(self.fieldToNode[f])
                termToNodes[t] = nodes

                if t == 'null':
                    print 'Field t null:', fullField, 'terms:', terms

        for lvinfo in self.lvToNode.keys():
            lvName = extractNameFromLocalVarInfo(lvinfo)
            lvName = lvName.strip()
            if not lvName: continue

            terms = getTermsFromVarName(lvName)

            for t in terms:
                cnt = termFreq.get(t, 0)
                termFreq[t] = cnt + LV_WEIGHT
                nodes = termToNodes.get(t, [])
                nodes.append(self.lvToNode[lvinfo])
                termToNodes[t] = nodes

                if t=='null':
                    print 'LV t null:', lvName, 'terms:', terms

        for paramRVinfo in self.paramRvToNode.keys():
            paramOrRV = extractNameFromMethodParamOrRVInfo(paramRVinfo)
            if paramOrRV[:3] == "arg" and len(paramOrRV) <= 4: continue

            terms = getTermsFromVarName(paramOrRV)
            for t in terms:
                cnt = termFreq.get(t, 0)
                termFreq[t] = cnt + PARAM_WEIGHT
                nodes = termToNodes.get(t, [])
                nodes.append(self.paramRvToNode[paramRVinfo])
                termToNodes[t] = nodes

        return termFreq, termToNodes

    def createConvexHullsForGraph(self):
        if self.legend: self.legend.removeAll()
        else: self.legend = Legend()

        rep = self.viewer.currentGraphRep
        terms = self.miner.suspiciousTermsMap.get(rep, ())
        addedTerms = []

        for (t1, t2) in terms:
            print "t1, t2:", t1, t2
            nodes1 = self.termToNodes[t1]
            nodes2 = self.termToNodes[t2]

            if len(nodes1) <= 2:
                print "len(nodes):", len(nodes1), "for term:", t1
            if len(nodes2) <= 2:
                print "len(nodes):", len(nodes2), "for term:", t2

            self.legend.add(nodes1[0], str(t1) + " vs " + str(t2))

            for n in nodes1+nodes2:
                #n.size += 2
                n.style = 1 # RECT

            if t1 not in addedTerms:
                addedTerms.append(t1)
                color = self.nextHullColor()
                hull1 = createConvexHull(nodes1, color)
                self.hulls.append(hull1)
                self.legend.add(hull1, t1)
                self.addListenerToLegend(hull1)

            if t2 not in addedTerms:
                addedTerms.append(t2)
                color = self.nextHullColor()
                hull2 = createConvexHull(nodes2, color)
                self.hulls.append(hull2)
                self.legend.add(hull2, t2)
                self.addListenerToLegend(hull2)

        terms = self.miner.selectedTerms[rep]
        for t in terms:
            if t in addedTerms: continue

            nodes = self.termToNodes[t]
            if len(nodes) <= 2: continue
            for n in nodes:
                #n.size += 2
                n.style = 1 # RECT

            color = self.nextHullColor()
            hull = createConvexHull(nodes, color)
            self.hulls.append(hull)
            self.legend.add(hull, str(t))
            self.addListenerToLegend(hull)
            hull.setVisible(false)

    def addListenerToLegend(self, hull):
        from java.lang.reflect import *
        field = Legend.__class__.getDeclaredField(Legend, "nodes")
        field.setAccessible(true)
        nodes = field.get(self.legend)
        addedHullLegendNode = nodes[len(nodes)-1]

        field = Legend.__class__.getDeclaredField(Legend, "annotations")
        field.setAccessible(true)
        annotations = field.get(self.legend)
        anno = annotations[addedHullLegendNode]

        handler = LegendClickHandler(addedHullLegendNode, hull, anno)
        addedHullLegendNode.addInputEventListener(handler)
        anno.addInputEventListener(handler)

    def _initHullColor(self):
        Blue = '0,0,255'
        Red = '255,0,0'
        ForestGreen = '34,139,34'   
        Sienna = '160,82,45'
        Purple = '160,32,240'
        DeepPink = '255,20,147'
        Peach = '252,186,148'
        GreenYellow = '236,252,151'
        VioletRed = '250,121,253'
        Gray = '49,79,79'

        self.HullColors = [Blue, Red, ForestGreen, Sienna, Purple, DeepPink, Peach, GreenYellow, VioletRed]
        self.HullColors.reverse()

    def nextHullColor(self):
        color = self.HullColors.pop()

        self.HullColors.insert(0, color)

        return color + ",160"

#    def showPathOnly(self, flag):
#        self.showPathOnlyFlag = flag
#
#        self.invisibles = []
#
#        if flag == 0:
#            g.nodes.visible = 1
#            g.edges.visible = 1
#        else:
#            g.nodes.visible = 0
#            g.edges.visible = 0
#            for e in g.edges:
#                eid = e.eid.split(';')[0]
#                if int(eid) % 8 == 0:
#                    e.visible = 1
#                    e.node1.visible = 1
#                    e.node2.visible = 1
#                else:
#                    self.invisibles.append(e)
#                    g.removeComplete([e])
#
#            g.gemLayout(41)

    def showPathOnly(self, flag):
        self.showPathOnlyFlag = flag

        if flag == 0:
            g.nodes.visible = 1
            g.edges.visible = 1
        else:
            g.nodes.visible = 0
            g.edges.visible = 0
            for e in g.edges:
                if e.color in ['orange', 'red']:
                    e.visible = 1
                    e.node1.visible = 1
                    e.node2.visible = 1

    def findPathBetweenHulls(self):
        pass
##        if self.prevHull == None or self.currHull == None:
##            return
##
##        nodelist1 = self.prefHull.getNodes()
##        nodelist2 = self.currHull.getNodes()
##
##        from com.hp.hpl.guess.mascopt import MascoptGraphProxy
##
##        paths = []
##        for n1 in nodelist1:
##            for n2 in nodelist2:
##                proxy = MascoptGraphProxy.createProxy(g)
##                path = proxy.kShortestPaths(2, n1, n2)
##                paths.extend(path)
##                if path.size() < 5:
##                    break
##
##        paths.sort(lambda x, y: len(x) - len(y))
##        for path in paths[:3]:
##            for e in path:
##                if e.color == "orange":
##                    e.color = "brown"
##                elif e.color == "red":
##                    e.color = "gray"

    def pathFound(self, jgraphEdges):
        pass
        """
        for e in self.path:
            if e.color == "orange":
                e.color = "brown"
            elif e.color == "red":
                e.color = "gray"
            else:
                raise Exception("Unexpected Edge color while eraising previous path")

        if self.showPathOnlyFlag == 1:
            g.nodes.visible = 0
            g.edges.visible = 0

        self.path = []
        for e in jgraphEdges:
            gEdgeId = e.guessEdgeId
            gEdge = g.getEdgeByID(gEdgeId)
            self.path.append(gEdge)
            gEdge.visible = 1
            gEdge.node1.visible = 1
            gEdge.node2.visible = 1

            if gEdge.color == "brown": # super edge
                gEdge.color = "orange"
            elif gEdge.color == "gray": # normal edge
                gEdge.color = "red"
            else:
                raise Exception("Unexpected Edge color")
        """


from java.util import LinkedHashSet

def unit(node):
    global sourceViewer
    return sourceViewer.panel.getUnit(int(node.uid))

class SourceViewer(com.hp.hpl.guess.ui.DockableAdapter):

    def __init__(self):
        sourcePath = System.getProperty("unifi.sp")
        uc_file = System.getProperty("unifi.read") 
        sourcePath = sourcePath.split(":")

        print "srcpath:", sourcePath
        print "uc_file:", uc_file

        self.panel = GuiPanel(sourcePath, uc_file)
        self.miner = TermMiner(Unit._current_unit_collection)
        self.panel.guessCallback = GythonCallback(self, self.miner)
        self.guessCallback = self.panel.guessCallback
        self.currentGraphRep = None

        #graphevents.clickNode = self.mouseClickNode
        graphevents.clickEdge = self.mouseClickEdge
        graphevents.mouseEnterEdge = self.mouseEnterEdge
        graphevents.mouseLeaveEdge = self.mouseLeaveEdge
        graphevents.mouseEnterNode = self.mouseEnterNode
        graphevents.mouseLeaveNode = self.mouseLeaveNode
        self.prevClicked = None

        EdgeEditorPopup.addItem("** UniFi Options **")

        disconnectEdgeItem = EdgeEditorPopup.addItem("Disconnect")
        disconnectEdgeItem.menuEvent = self.disconnectEdge

        vf.defaultNodeZooming(false)
        vf.defaultEdgeZooming(false)

        self.source = "None"

        self.miner.run()

        showAllClusters = System.getProperty("showall")
        if showAllClusters is None:
            from java.util import ArrayList
            arrayList = ArrayList()
            for r in self.miner.sortedReps:
                arrayList.add(r)

            self.panel.filterAndSortClusters(arrayList)

        self.panel.display_all()
        self.add(self.panel)
        
        # add the toolbar to the main UI window
        ui.dock(self)

    def disconnectEdge(self, edge):
        print "removing edge:", edge

##    def mouseClickNode(self, node):
##        print "mouseClickNode clicked!!!"
##
##        containingHull = None
##        print "self.hulls.size:", len(self.hulls)
##        for hull in self.hulls:
##            print "iterating self.hulls, hull:", hull
##            nodes = hull.getNodes()
##            if node in nodes:
##                containingHull = hull
##                break
##
##        if containingHull == None: 
##            print "No containing hull for this node:", node
##            return
##
##        print "Found containing hull for this node:", node
##
##        from java.awt import Color
##
##        if self.prevHull != None:
##            colorStr = self.prevHull.getColor()
##            c = colorStr.split(',')
##
##            c = Color(int(c[0]), int(c[1]), int(c[2]), int(c[3]) - 70)
##            self.prevHull.setColor(c)
##
##
##
##        colorStr = containingHull.getColor()
##        c = colorStr.split(',')
##        c = Color(int(c[0]), int(c[1]), int(c[2]), int(c[3]) + 70)
##        containingHull.setColor(c)

##    def mouseClickNode(self, node):
##        if node == self.currSelectedNode:
##            return
##
##        self.panel.updateNodeSelection(unit(node));
##
##        if self.prevSelectedNode != None:
##            self.prevSelectedNode.size = self.prevSelectedNode.size - 10
##            self.prevSelectedNode.color = self.prevSelectedNodeOrigColor
##
##        if self.currSelectedNode != None:
##            self.prevSelectedNode = self.currSelectedNode
##            self.prevSelectedNodeOrigColor = self.currSelectedNodeOrigColor
##
##        self.currSelectedNode = node
##        self.currSelectedNodeOrigColor = node.color
##
##        node.size = node.size + 10
##        node.color = 'gray'

       
    def getFullVarRepr(self, node):
        fullLabel = node.classname
        idx = fullLabel.find(",")
        varName = fullLabel[:idx]
        return varName

    def mouseEnterEdge(self, edge):
        n1 = edge.getNode1()
        n2 = edge.getNode2()

        self.prevN1Label = n1.label
        self.prevN2Label = n2.label

        n1.label = self.getFullVarRepr(n1)
        n2.label = self.getFullVarRepr(n2)

    def mouseLeaveEdge(self, edge):
        n1 = edge.getNode1()
        n2 = edge.getNode2()

        n1.label = self.prevN1Label
        n2.label = self.prevN2Label

    def mouseEnterNode(self, node):
        self.prevNlabel = node.label
        node.label = self.getFullVarRepr(node)

    def mouseLeaveNode(self, node):
        node.label = self.prevNlabel

    def mouseClickEdge(self, edge):
        if self.prevClicked:
            self.prevClicked.color = 'gray'
            self.prevClicked.width -= 2

        edge.color = 'red'
        edge.width += 2
        self.prevClicked = edge

        eidlist = edge.eid.split(';')
        eids = []
        for eid in eidlist:
            try:
                eid = int(eid)
                eids.append(eid)
            except:
                pass
        if len(eids)==1:
            self.panel.display_source(eids[0])
        else:
            self.panel.display_source2(eids)

    def getTitle(self):
        return self.source

Guess.getFrame().setDisplayBackground(white)
Guess.getMainUIWindow().setSize(java.awt.Dimension(1500, 1400))
Guess.getMainUIWindow().setTitle("Unifi Visualization")

sourceViewer = SourceViewer()


"""
print "\n\n!!!!"
print "Work on method param, rv term extracting!"
print "!!!!\n\n"

all pair shortest path
from com.hp.hpl.guess.mascopt import MascoptDiGraphProxy

proxy = MascoptDiGraphProxy.createProxy(g)
proxy.kShortestPaths(2,v1,v4) 
"""
