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

package unifi.contextsensitive;

import java.io.Serializable;

import unifi.BCP;
import unifi.MethodUnits;

/** class to represent a method invocation */
public class MethodInvoke implements Serializable {

private MethodUnits callerMU, calleeMU; // method units of caller
private BCP bcp; // bcp at callsite

public MethodInvoke (BCP bcp, MethodUnits callerMethodUnits, MethodUnits calleeMethodUnits)
{
	this.bcp = bcp;
	this.callerMU = callerMethodUnits;
	this.calleeMU = calleeMethodUnits;
}

public MethodSummary getMethodSummary()
{
	return callerMU.getMethodSummary();
}

public MethodUnits getCallerMethodUnits() {
	return callerMU;
}

public BCP getBcp() {
	return bcp;
}

}
