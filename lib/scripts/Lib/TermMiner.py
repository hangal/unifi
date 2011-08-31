from __future__ import nested_scopes
from unifi.units import FieldUnit, LocalVarUnit, ArrayLengthUnit, MethodParamUnit, ReturnValueUnit, PhiUnit

def fieldFromFullField(fullField):
    return fullField.split('.').pop()

def getTermsFromFullFieldName(fullField, existingTerms=[]):
    if fullField.find("Length of Field ") >= 0:
        fullField = fullField[len("Length of Field "):fullField.find(",")]
    fieldName = fieldFromFullField(fullField)
    if fieldName.find("this$") >= 0:
        return ""

    terms = getTermsFromVarName(fieldName, existingTerms)
    if 'm' in terms and len(terms) >= 2: terms.remove('m')
    return terms

def getTermsFromVarName(varName, existingTerms=[]):
    import re
    import stemmer
    import string

    if varName == "this":
        return []

    def splitCamel(s):
        return s.group(0) + " "

    terms = varName.split('_')
    terms2=[]
    for t in terms:
        terms2.extend(re.split("\d+", t))

    finalTerms = []
    for t in terms2:
        if len(t)==0: continue
        p = re.compile('((?<=[A-Z][A-Z][A-Z])(?=[a-z])|(?<=[a-z])(?=[A-Z][a-z])|(?<=[a-z])(?=[A-Z]))')
        tmp1 = p.sub(splitCamel, t).strip().split(' ')
        score1 = len(filter(lambda x: x in existingTerms, tmp1))

        p = re.compile('((?=[A-Z][a-z])|(?<=[a-z])(?=[A-Z]))')
        
        tmp2 = p.sub(splitCamel, t).strip().split(' ')
        score2 = len(filter(lambda x: x in existingTerms, tmp2))
        if score1 >= score2:
            finalTerms.extend(tmp1)
        else:
            finalTerms.extend(tmp2)

    result = map(lambda w: w.strip().lower(), finalTerms)

    stemmed = []
    p = stemmer.PorterStemmer()
    for w in result:
        if not w: continue
        if w[-1:] in string.digits:
            p2 = re.compile("[a-zA-Z]*[^0-9]") 
            wordNum = p2.sub(splitCamel, w).strip().split(' ')
            if len(wordNum) == 2:
                w = wordNum[0]
                n = wordNum[1]
                # only index numbers larger than 10
                if len(n) > 1: stemmed.append(n)
            else:
                pass
            
        s = p.stem(w, 0, len(w)-1)
        stemmed.append(s)

    if "get" in stemmed:
        stemmed.remove("get")
    return stemmed
 
def extractNameFromLocalVarInfo(lvinfo):
    if lvinfo.find("LV") < 0 and lvinfo.find("Length of Field") < 0:
        raise Exception("wrong local variable info:"+lvinfo)
    if lvinfo.find("LV null ") >= 0:
        return ""

    lvName = None
    if lvinfo.find("Length of Field") == 0:
        fullField = lvinfo[len("Length of Field "): lvinfo.find(", ")]
        field = fieldFromFullField(fullField)
        lvName = field
    elif lvinfo.find("Length of") == 0:
        endIdx = lvinfo.find(" in method")
        lvName = lvinfo[len("Length of LV "):endIdx]
    else:
        endIdx = lvinfo.find(" in method")
        lvName = lvinfo[len("LV "):endIdx]

    return lvName

def isRVstr(s):
    if s.find("RV of ") >= 0: return 1
    return 0

def isParamStr(s):
    if s.find("Param ") >= 0 and s.find(" of method") >= 0:
        return 1
    return 0

def isParamOrRVstr(s):
    if isRVstr(s) or isParamStr(s):
        return 1
    return 0

def extractNameFromMethodParamOrRVInfo(paramOrRV):
    # Let's ignore param/rv clones for each call site
    # Except for the very first one

    #if paramOrRV.find("Call#") == 0 and not paramOrRV.find("Call#0") : return ""

    #if paramOrRV.find("RV of ") >= 0:
    if isRVstr(paramOrRV):
        terms = extractNameFromRVInfo(paramOrRV)
        return terms
    else:
        return extractNameFromParamInfo(paramOrRV)

def extractNameFromParamInfo(param):
    #if param.find("Param ") < 0 or param.find(" of method") < 0:
    if not isParamStr(param):
        raise Exception("wrong param info:"+param)
    endIdx = param.find(" of method")
    name = param[param.find("Param ") + len("Param "):endIdx]
    return name

def extractNameFromRVInfo(rv):
    #if rv.find("RV of") < 0:
    if not isRVstr(rv):
        raise Exception("wrong rv info:"+rv)
    #print "RV:", rv
    idx = rv.find(",")
    rv = rv[len("RV of "):idx]
    if rv.find("()") >= 0:
        # rv is like: java.awt.MouseInfo.getPointerInfo()
        rv = rv[:rv.find("()")]
        # now rv is like java.awt.MouseInfo.getPointerInfo
        # so, it's like a full field
        return fieldFromFullField(rv)

    return ""
    

FIELD_WEIGHT = 6
ARRAY_LENGTH_OF_FIELD_WEIGHT = 6
ARRAY_LENGTH_OF_REST = 1
LV_WEIGHT = 1
PARAM_WEIGHT = 1
RV_WEIGHT = 1

def getWeightFromUnit(u):
    if isinstance(u, FieldUnit):
        return FIELD_WEIGHT
    elif isinstance(u, ArrayLengthUnit):
        if isinstance(u.lengthOf, FieldUnit):
            return ARRAY_LENGTH_OF_FIELD_WEIGHT
        else:
            return ARRAY_LENGTH_OF_REST
    elif isinstance(u, LocalVarUnit):
        return LV_WEIGHT
    elif isinstance(u, MethodParamUnit):
        return PARAM_WEIGHT
    elif isinstance(u, ReturnValueUnit):
        return RV_WEIGHT
    return 1;
 
class TermCollection:
    def __init__(self):
        self.termMap = {} # term string to term instance
        self.selfBigrams = []
        self.processedParamOrRVs = []

    def printWeights(self, termIDF):
        terms = self.termMap.values()
        terms.sort(lambda x,y:x.weight < y.weight)
        for t in terms:
            print t.term, ":", t.weight * termIDF[t.term]

    def toStr(self):
        terms = self.termMap.values()
        terms.sort(lambda x,y:x.weight < y.weight)
        s = "["
        newLineIdx = 0
        for t in terms:
            s += t.term + ":"
            s += str(t.weight) + ", ("
            s += str(t.varIDs) + ")"
            newLineIdx += 1
            if newLineIdx == 4:
                newLineIdx = 0
                s += "\n"
        s += "]"
        return s

    def topNterms(self, n=7):
        terms = self.termMap.values()
        terms.sort(lambda x, y:y.weight > x.weight)
        return map(lambda x:x.term, terms)[:n]

    def totalWeight(self):
        weightSum = 0
        for t in self.termMap.values():
            weightSum += t.weight
        return weightSum

    def extractVarAndMethodForParam(self, param):
        idx = param.find(',')
        param= param[:idx]
        if param.find("Call#") >= 0:
            idx = param.find("Param ")
            param= param[idx:]
        return param

    def extractVarAndMethodForRV(self, rv):
        idx = rv.find(",")
        rv = rv[:idx]
        if rv.find("Call#") >= 0:
            idx = rv.find("Call#")
            idx = rv.find(" ", idx)
            rv = "RV of" + rv[idx:]
        return rv

    def isProcessedParamOrRV(self, unitStr):
        if isParamStr(unitStr):
            paramStr = self.extractVarAndMethodForParam(unitStr)
            if paramStr in self.processedParamOrRVs:
                return 1
            self.processedParamOrRVs.append(paramStr)
            return 0
        else:
            RVstr = self.extractVarAndMethodForRV(unitStr)
            if RVstr in self.processedParamOrRVs:
                return 1
            self.processedParamOrRVs.append(RVstr)
            return 0
 
    def addTerms(self, unitStr, terms, varID, weight):
        if isParamOrRVstr(unitStr) and self.isProcessedParamOrRV(unitStr):
            return

        for t in terms:
            self.addTerm(t, varID, weight)

        self.registerSelfBigram(terms)

    def registerSelfBigram(self, terms):
        for i in range(len(terms)):
            t1 = terms[i]
            for j in range(i+1, len(terms)):
                t2 = terms[j]
                if t1<t2:
                    if (t1,t2) not in self.selfBigrams:
                        self.selfBigrams.append((t1, t2))
                elif t2<t1:
                    if (t2,t1) not in self.selfBigrams:
                        self.selfBigrams.append((t2, t1))

    def addTerm(self, term, varID, weight):
        t = None
        if term in self.termMap.keys():
            t = self.termMap[term]
            t.addWeight(weight)
            t.addVarID(varID)
        else:
            t = Term(term, varID, weight)
            self.termMap[term] = t

        if weight == FIELD_WEIGHT:
            t.appearedInField = 1

    def getBigrams(self, findAnomaly=0):
        bigrams = []
        if findAnomaly == 0:
            cutOff = LV_WEIGHT * 2
        else:
            cutOff = FIELD_WEIGHT + LV_WEIGHT * 2

        terms = self.termMap.keys()
        for i in range(len(terms)):
            t1 = terms[i]
            w1 = self.termMap[t1].weight
            idlist1 = self.termMap[t1].varIDs
            if w1 < cutOff: continue

            for j in range(i+1, len(terms)):
                t2 = terms[j]
                w2 = self.termMap[t2].weight
 
                idlist2 = self.termMap[t2].varIDs
                if w2 < cutOff: continue

                #if not findAnomaly or self.notFromSameVars(idlist1, idlist2 ):
                if not self.fromSameVars(idlist1, idlist2):
                    termInst1 = self.termMap[t1]
                    termInst2 = self.termMap[t2]
                    if t1 < t2:
                        bigrams.append((termInst1, termInst2))
                    elif t1 > t2:
                        bigrams.append((termInst2, termInst1))

        return bigrams

    def fromSameVars(self, varIDs1, varIDs2):
        idlist1 = varIDs1
        idlist2 = varIDs2
        if len(varIDs1) > len(varIDs2):
            idlist1 = varIDs2
            idlist2 = varIDs1
        
        for id1 in idlist1:
            if id1 in idlist2:
                return 1
        
        return 0

    def notFromSameVars(self, varIDs1, varIDs2):
        idlist1 = varIDs1
        idlist2 = varIDs2
        if len(varIDs1) > len(varIDs2):
            idlist1 = varIDs2
            idlist2 = varIDs1
        
        origIdlist1Len = len(idlist1)
        origIdlist2Len = len(idlist2)

        common = []
        for id1 in idlist1:
            if id1 in idlist2:
                common.append(id1)

        for e in common:
            idlist1.remove(e)
            idlist2.remove(e)

        # XXX(jiwon)
        if len(idlist1) >= 1 and len(idlist2) >= 1:
            if len(idlist1) + len(idlist2) > len(common) * 2:
                return 1
        return 0
       
    def getTermWeight(self, term):
        t = self.termMap[term]
        return t.weight

class Term:
    def __init__(self, termStr, varID, weight):
        #assert isinstance(termStr, str)
        #assert isinstance(varID, int)
        #assert isinstance(weight, float)

        self.term = termStr
        self.varIDs = []
        self.varIDs.append(varID)
        self.weight = weight
        self.appearedInField = 0

    def addWeight(self, weight):
        self.weight += weight

    def addVarID(self, varID):
        if varID not in self.varIDs:
            self.varIDs.append(varID)

    def __str__(self):
        return "["+self.term+"] weight:"+self.weight

CLUSTER_SIZE_CUTOFF = 2
class TermMiner:
    def __init__(self, unitCollection):
        self.uc = unitCollection
        self.termDocCount = {}
        self.bigramDocCount = {}
        self.termIDF = {}
        self.usedGraphNum = 0
        self.cutoffIDF = None
        self.sortedReps = []
        self.selectedTerms = {} # rep to list of terms
        self.suspiciousTermsMap = {}

    def run(self):
        self.calcIDF()
        self.calcBigramFreq()
        self.examineBigrams()
        self.findAnomalBigrams()

    def updateTermDocCount(self, terms, dimIdx, clusterSize):
        for t in terms:
            cnt = self.termDocCount.get(t, 0)
            cnt += 1
            self.termDocCount[t] = cnt

            if t in ["class", "tree", "trees"]:
                print t, "appeared in dimension:", dimIdx, "clusterSize:", clusterSize

    def getTermsFromUnit(self, u, existingTerms = []):
        unitStr = str(u)

        terms = []
        if isinstance(u, FieldUnit):
            fullField = unitStr.split(',')[0]
            terms = getTermsFromFullFieldName(fullField, existingTerms)
        elif isinstance(u, ArrayLengthUnit) and unitStr.find("Length of Field") >= 0:
            terms = getTermsFromFullFieldName(unitStr, existingTerms)
        elif isinstance(u, LocalVarUnit) or (isinstance(u, ArrayLengthUnit) and unitStr.find("Length of LV") >= 0):
            lvName = extractNameFromLocalVarInfo(unitStr)
            lvName = lvName.strip()
            # "maybe_varname?" is a guess
            if lvName.find("?") >= 0:
                return []
            terms = getTermsFromVarName(lvName, existingTerms)
            #print "local var:", lvName, "terms:", terms
        elif isinstance(u, MethodParamUnit) or \
               isinstance(u, ReturnValueUnit):
            param = extractNameFromMethodParamOrRVInfo(unitStr)
            if param[:3] == "arg" and len(param) <= 4:
                return []
            terms = getTermsFromVarName(param, existingTerms)

        terms2 = []
        for t in terms:
            if t.find("$") >= 0:
                #print "$ included, unitStr:", unitStr, "t:", t
                pass
            else:
                terms2.append(t)

        return terms2

    def extendCurrentTerms(self, currentTerms, terms):
        for t in terms:
            if t not in currentTerms:
                currentTerms.append(t)

    def updateTermWeights(self, termWeights, terms, weight):
        for t in terms:
            count = termWeights.get(t, 0)
            count += weight
            termWeights[t] = count

    def calcIDF(self):
        reps = self.uc.get_reps()
        # for each graph
        usedGraphNum = 0
        for rep in reps.keySet():
            clusterSize = reps.get(rep).size()
            if clusterSize <= CLUSTER_SIZE_CUTOFF: continue

            usedGraphNum += 1

            currentTerms = []
            # for each unit in the graph
            for u in reps.get(rep):
                terms = self.getTermsFromUnit(u, self.termDocCount.keys() + currentTerms)

                self.extendCurrentTerms(currentTerms, terms)

            self.updateTermDocCount(currentTerms, usedGraphNum+1, clusterSize) 

        print "\ntotal used dimensions #:", usedGraphNum 

        import math
        for k,v in self.termDocCount.items():
            #idf = math.log10(float(usedGraphNum)/ v)
            idf = math.pow(float(usedGraphNum)/ v, 0.50)
            self.termIDF[k] = idf

        IDFs = self.termIDF.values()
        IDFs.sort()
        bottom3Percent = IDFs[len(IDFs)/34]
        bottom1Percent = IDFs[len(IDFs)/99]
        bottom02Percent = IDFs[len(IDFs)/499]

        print "bottom 0.2%:", bottom02Percent
        print "bottom 1%:", bottom1Percent
        print "bottom 3%:", bottom3Percent

        commonWordIDFs = []
        tmpSum = 1.0
        minIdf = 1000.0
        maxIdf = 0.0
        #for e in ['i', 'j', 'num', 'size', 'count', 'name', 'index', 'value', 'data', 'id', 'length']:
        for e in ['i', 'j', 'num', 'size', 'count', 'name', 'index', 'id', 'length']:
            idf = self.termIDF.get(e, None)
            if idf != None and idf <= bottom3Percent and idf > bottom02Percent:
                print "IDF[",e,"]:", idf
                if idf < minIdf: minIdf=idf
                if idf > maxIdf: maxIdf=idf
                commonWordIDFs.append(idf)
                tmpSum += idf

        #if minIdf != 1000.0: 
        #    tmpSum -= minIdf
        #    commonWordIDFs.remove(minIdf)
        #if maxIdf != 0.0 and maxIdf != minIdf: 
        #    tmpSum -= maxIdf
        #    commonWordIDFs.remove(maxIdf)

        if len(commonWordIDFs) <= 1:
            self.cutoffIDF = bottom1Percent
        else:
            #self.cutoffIDF = math.pow(tmpSum, 1.0 / len(commonWordIDFs))
            self.cutoffIDF = tmpSum/ len(commonWordIDFs)

        print "cutoff IDF:", self.cutoffIDF

        #self.cutoffIDF = bottom1Percent

        print "cutoff IDF:", self.cutoffIDF


        self.usedGraphNum = usedGraphNum

        termIDFpair = self.termIDF.items()
        termIDFpair.sort(lambda x, y: x[1] > y[1])

        idx=0
        cutoffPrinted = 0
        for k, v in termIDFpair:
            if v >= self.cutoffIDF and cutoffPrinted==0:
                print "----------------> cutoff <-------------------"
                cutoffPrinted = 1

            print idx, ":", k, "has IDF:", v, "termDocCount:", self.termDocCount[k]
            idx += 1
            if idx % 100 == 0:
                break
        print "total morphemes #:", len(termIDFpair)

    def calcScore(self, bigram, tfidf1, tfidf2):
        t1, t2 = bigram
        t1docCount = self.termDocCount[t1]
        t2docCount = self.termDocCount[t2]

        jaccardSimilarity = float(self.bigramDocCount[bigram]) / ((t1docCount + t2docCount) - self.bigramDocCount[bigram])

        import math
        score = math.pow(1 / jaccardSimilarity, 0.50)

        tfIdfSum = tfidf1 + tfidf2
        #score *= math.log10(1 + 10.0 * float(tfIdfSum) / self.cutoffIDF)
        score *= math.pow(10.0 * float(tfIdfSum) / self.cutoffIDF, 0.90)

        if self.bigramDocCount[bigram] >= 2:
            score *= 0.7

        if len(t1) == 3: score *= 0.8
        elif len(t1) == 2: score *= 0.7
        elif len(t1) == 1: score *= 0.5

        if len(t2) == 3: score *= 0.8
        elif len(t2) == 2: score *= 0.7
        elif len(t2) == 1: score *= 0.5

        if len(t2) >= 3 and t1.find(t2) >= 0: 
            closeness = len(t2) / float(len(t1))
            if closeness >= 0.7: score *= 0.3
            elif closeness >= 0.6: score *= 0.45
            elif closeness >= 0.5: score *= 0.6
            else: score *= 0.8
        elif len(t1) >= 3 and t2.find(t1) >= 0:
            closeness = len(t1) / float(len(t2))
            if closeness >= 0.7: score *= 0.3
            elif closeness >= 0.6: score *= 0.45
            elif closeness >= 0.5: score *= 0.6
            else: score *= 0.8

        return score

    def findAnomalBigrams(self):
        reps = self.uc.get_reps()
        anomalClusters = []
        anomalClusterCount = 0

        varID = 0
        for rep in reps.keySet():
            clusterSize = reps.get(rep).size()
            if clusterSize <= CLUSTER_SIZE_CUTOFF: continue

            currentTermWeights = {}
            tc = TermCollection()
            for u in reps.get(rep):
                terms = self.getTermsFromUnit(u, self.termDocCount.keys())
                terms = filter(lambda x: self.termIDF[x] > self.cutoffIDF, terms)
                weight = getWeightFromUnit(u)

                tc.addTerms(str(u), terms, varID, weight)

                varID += 1

            totalTermWeights = tc.totalWeight()
            anomalyCandidates = []
            for instBigram in tc.getBigrams(findAnomaly=1):
                termInst1, termInst2 = instBigram
                bigram = (instBigram[0].term, instBigram[1].term)
                t1, t2 = termInst1.term, termInst2.term

                tf1 = tc.getTermWeight(t1)
                tf2 = tc.getTermWeight(t2)

                if len(t1) <= 2 or len(t2) <= 2: continue

                if len(t1) == 1: tf1 *= 0.5
                elif len(t1) == 2: tf1 *= 0.7
                elif len(t1) == 3: tf1 *= 0.9

                if len(t2) == 1: tf2 *= 0.5
                elif len(t2) == 2: tf2 *= 0.7
                elif len(t2) == 3: tf2 *= 0.9


                tfIdf1 = tf1/float(totalTermWeights) * self.termIDF[t1]
                tfIdf2 = tf2/float(totalTermWeights) * self.termIDF[t2]

                if clusterSize == 223 and (t1, t1) == ('description', 'label'):
                    print "tfIdf[description]:", tfIdf1
                    print "tfIdf[label]:", tfIdf2

                    t1docCount = float(self.termDocCount[t1])
                    t2docCount = float(self.termDocCount[t2])
                    sumDocCount = (t1docCount + t2docCount) - self.bigramDocCount[bigram]
                    print "JaccardSimilarity:", self.bigramDocCount[bigram] / sumDocCount 


                tfIdfCutoff = self.cutoffIDF * 0.09
                if tfIdf1 <= tfIdfCutoff or tfIdf2 <= tfIdfCutoff: 
                    continue
                
                t1docCount = float(self.termDocCount[t1])
                t2docCount = float(self.termDocCount[t2])
                sumDocCount = (t1docCount + t2docCount) - self.bigramDocCount[bigram]

                if self.bigramDocCount[bigram] / sumDocCount <= 0.21 and \
                        self.bigramDocCount[bigram] <= 2:
                    if reps.get(rep).size() <= 5: continue
                    if self.termDocCount[t1] == self.bigramDocCount[bigram] or \
                          self.termDocCount[t2] == self.bigramDocCount[bigram]:continue
                    if (not termInst1.appearedInField) or (not termInst2.appearedInField): continue

                    score = self.calcScore(bigram, tfIdf1, tfIdf2)
                    anomalyCandidates.append((rep, bigram, (tf1, tf2), totalTermWeights, self.bigramDocCount[bigram], score))

            for candidate in anomalyCandidates:
                suspiciousTerms = self.suspiciousTermsMap.get(str(rep), [])

                suspiciousTerms.append((candidate[1][0],candidate[1][1]))
                self.suspiciousTermsMap[str(rep)] = suspiciousTerms

            anomalClusters.extend(anomalyCandidates)
            if anomalyCandidates: anomalClusterCount += 1

        #anomalClusters.sort(lambda x, y:reps.get(x[0]).size() - reps.get(y[0]).size())
        anomalClusters.sort(lambda x, y: x[5] < y[5])

        for cluster in anomalClusters:
            if cluster[0] not in self.sortedReps:
                self.sortedReps.append(cluster[0])

        for i in range(len(anomalClusters)):
            print "["+str(i)+"]: rep=", anomalClusters[i]
            #t1 = anomalClusters[i][1][0]
            #t2 = anomalClusters[i][1][1]
            #tf1 = anomalClusters[i][2][0]
            #tf2 = anomalClusters[i][2][1]
            #totalWeight = anomalClusters[i][3]
            #print "  term doc count(",t1,"):", self.termDocCount[t1], "idf:",self.termIDF[t1] , "termWeight:", tf1, "tf*idf:", float(tf1)/totalWeight * self.termIDF[t1]
            #print "  term doc count(",t2,"):", self.termDocCount[t2], "idf:",self.termIDF[t2] ,"termWeight:", tf2, "tf*idf:", float(tf2)/totalWeight * self.termIDF[t2]
            #score = anomalClusters[i][4]
            #print "  score:", score, "totalTermWeight:", totalWeight


            #print
        print "anomal cluster size:", anomalClusterCount

        return anomalClusters

    def examineBigrams(self):
        print "\n\n#########"*4
        for (bigram, count)  in self.bigramDocCount.items():
            if count >= 2:
                print bigram, "has count:", count, bigram[0], " docCount:", self.termDocCount[bigram[0]],\
                                           bigram[1], " docCount:", self.termDocCount[bigram[1]]
            if 'code' in bigram or 'char' in bigram:
                print "@@@@   ", bigram, "has count:", count
        print "#########"*4, "\n"

    def calcBigramFreq(self):
        reps = self.uc.get_reps()

        varID = 0
        for rep in reps.keySet():
            clusterSize = reps.get(rep).size()
            if clusterSize <= CLUSTER_SIZE_CUTOFF: continue

            currentTermWeights = {}
            tc = TermCollection()

            for u in reps.get(rep):
                terms = self.getTermsFromUnit(u, self.termDocCount.keys())

                terms = filter(lambda x: self.termIDF[x] > self.cutoffIDF, terms)
                weight = getWeightFromUnit(u)

                tc.addTerms(str(u), terms, varID, weight)
                varID += 1

            self.selectedTerms[str(rep)] = tc.topNterms(7)
            #if clusterSize >= 40:
            #    print "+---- Top 7 terms from Rep:", rep
            #    for t in tc.topNterms(7):
            #        print t, "TF:", float(tc.getTermWeight(t)) / tc.totalWeight()

            for instBigram in tc.getBigrams():
                bigram = (instBigram[0].term, instBigram[1].term)
                count = self.bigramDocCount.get(bigram, 0)
                count += 1
                self.bigramDocCount[bigram] = count

    def getSelfBigrams(self, terms):
        bigrams = []
        for i in range(len(terms)):
            for j in range(i+1, len(terms)):
                t1 = terms[i]
                t2 = terms[j]
                if t1 < t2:
                    bigrams.append((t1, t2))
                elif t1 > t2:
                    bigrams.append((t2, t1))
        return bigrams
