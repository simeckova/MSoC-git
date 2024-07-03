/*
 * Copyright (C) 2015, Charles University in Prague.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.cuni.mff.d3s.stdynpor.jpf;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;

import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.StaticElementInfo;
import gov.nasa.jpf.vm.ClassLoaderInfo;
import gov.nasa.jpf.vm.ThreadList;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.jvm.bytecode.INVOKEINTERFACE;
import gov.nasa.jpf.jvm.bytecode.ASTORE;
import gov.nasa.jpf.jvm.bytecode.DUP;
import gov.nasa.jpf.jvm.bytecode.MONITORENTER;
import gov.nasa.jpf.jvm.bytecode.InstanceFieldInstruction;
import gov.nasa.jpf.jvm.bytecode.GETFIELD;
import gov.nasa.jpf.jvm.bytecode.PUTFIELD;

import cz.cuni.mff.d3s.stdynpor.Configuration;
import cz.cuni.mff.d3s.stdynpor.data.ProgramPoint;
import cz.cuni.mff.d3s.stdynpor.data.AllocationSite;
import cz.cuni.mff.d3s.stdynpor.data.ObjectStringContext;
import cz.cuni.mff.d3s.stdynpor.data.FieldID;
import cz.cuni.mff.d3s.stdynpor.data.FieldName;
import cz.cuni.mff.d3s.stdynpor.data.FieldAccessPath;
import cz.cuni.mff.d3s.stdynpor.data.ClassName;
import cz.cuni.mff.d3s.stdynpor.data.LocalVarName;
import cz.cuni.mff.d3s.stdynpor.bytecode.ByteCodeUtils;


public class JPFUtils
{
	public static String getMethodSignature(MethodInfo mi)
	{
		return mi.getFullName();
	}

	public static List<ProgramPoint> getProgramPointsForThreadStack(ThreadInfo th, boolean excludeChildThreads)
	{
		List<ProgramPoint> progpoints = new ArrayList<ProgramPoint>();
		
		// top stack frame is the first
		boolean isTop = true;

		for (StackFrame sf : th)
		{
			if (sf != th.getTopFrame()) isTop = false;
			
			Instruction curInsn = sf.getPC();
			
			String methodSig = getMethodSignature(curInsn.getMethodInfo());

			// for stack frames other than top we need the next instruction after the currently processed method call
			if ( ! isTop )
			{
				// returns "null" if we are at the end (last instruction) of the given method
				curInsn = curInsn.getNext();
			}
		
			int insnPos = -1;		
			if (curInsn != null) insnPos = curInsn.getPosition();
			
			// ignore calls of methods without body
			if (curInsn instanceof INVOKEINTERFACE) continue;
			
			// ignore synthetic methods
			if (sf.isSynthetic())
			{
				if (sf.getMethodInfo().getName().equals("[main]")) continue;
				
				// we assume that a given thread is at the beginning of its method "run()"
				if (sf.getMethodInfo().getName().equals("[run]"))
				{
					methodSig = getRunMethodSignatureForThread(th);
					insnPos = 0;
				}
			}
			
			// static analysis provides results only for the position -1
			if (sf.isNative()) insnPos = -1;

			// there is a call to "Thread.start" at the top stack frame
			if (isTop && ByteCodeUtils.isThreadStartMethod(methodSig))
			{
				// just skip to ignore data (results) for child threads
				if (excludeChildThreads) continue;
				
				// this returns "null" if the current thread is still executing the call of Thread.start() but the dynamic receiving thread object already runs (it has the method "run()" on its call stack)
					// in that case we can safely ignore this program point (inside the call of "Thread.start()")
				// otherwise we can replace the program point inside the call to "Thread.start" with the beginning of "run()" (i.e., position equal to 0) in the dynamic receiver object (its class)
				methodSig = getMethodSignatureForNewlyStartedThread(th, sf, true);
				if (methodSig == null) continue;
	
				insnPos = 0;
			}
			
			ProgramPoint pp = new ProgramPoint(methodSig, insnPos);
			
			progpoints.add(pp);
			
			// we have to ignore all future field accesses that may happen after the end of a thread whose "run" method is in the call stack
				// program points are not collected for stack frames that are below the current one (which corresponds to "run")
				// there may be some other frames below especially when the main thread just started the new thread (that is why the execution of "run" appears in the list of program points)
			if (isActiveThreadRunMethod(th, sf, methodSig)) break;
		}
		
		return progpoints;
	}
	
	public static ProgramPoint getProgramPointForThreadPC(ThreadInfo th, boolean excludeChildThreads)
	{
		if (th.getTopFrame() == null) return ProgramPoint.INVALID;

		for (StackFrame sf : th)
		{								
			Instruction curInsn = sf.getPC();
			
			// thread not yet started or already terminated
			if (curInsn == null) return ProgramPoint.INVALID;
			
			String methodSig = getMethodSignature(curInsn.getMethodInfo());
				
			int insnPos = curInsn.getPosition();
			
			// ignore calls of methods without body
			if (curInsn instanceof INVOKEINTERFACE) continue;
			
			if (sf.isSynthetic()) 
			{
				// we assume that a given thread is at the beginning of its method "run()"
				if (sf.getMethodInfo().getName().equals("[run]"))
				{
					methodSig = getRunMethodSignatureForThread(th);
					insnPos = 0;
				}
				else if ( ! sf.isNative() )
				{
					// ignore other synthetic methods
					return ProgramPoint.INVALID;
				}
			}
			
			// static analysis provides results only for the position -1
			if (sf.isNative()) insnPos = -1;

			// there is a call to "Thread.start" at the top stack frame
			if (ByteCodeUtils.isThreadStartMethod(methodSig))
			{
				// just skip to ignore data (results) for child threads
				if (excludeChildThreads) continue;
				
				// this returns "null" if the current thread is still executing the call of Thread.start() but the dynamic receiving thread object already runs (it has the method "run()" on its call stack)
					// in that case we can safely ignore this program point (inside the call of "Thread.start()")
				// otherwise we can replace the program point inside the call to "Thread.start" with the beginning of "run()" (i.e., position equal to 0) in the dynamic receiver object (its class)
				methodSig = getMethodSignatureForNewlyStartedThread(th, sf, true);
				if (methodSig == null) continue;
	
				insnPos = 0;
			}
			
			ProgramPoint pp = new ProgramPoint(methodSig, insnPos);
			
			return pp;
		}
		
		// we have not found any usable program point (not in library methods, etc)
		return ProgramPoint.INVALID;
	}
	
	private static boolean isAlreadyRunningThreadObject(VM vm, ElementInfo objEI)
	{
		ThreadInfo th = vm.getThreadList().getThreadInfoForObjRef(objEI.getObjectRef());
		
		// no such thread
		if (th == null) return false; 
		
		// look for the method "run()" on its call stack
		// it must have a proper dynamic receiver object
		for (StackFrame sf : th)
		{
			if (sf.getMethodInfo().getName().equals("run") && ( ! sf.isSynthetic() ) && (sf.getThis() == objEI.getObjectRef())) return true;
		}
		
		return false;		
	}
	
	public static List<AllocationSite> getCurrentlyHeldLocksForThread(ThreadInfo th)
	{
		List<AllocationSite> lockObjAllocSites = new ArrayList<AllocationSite>();
		
		List<ElementInfo> lockObjEIs = th.getLockedObjects();
		
		for (ElementInfo lockEI : lockObjEIs)
		{
			// this should not happen
			if (lockEI instanceof StaticElementInfo) continue;
			
			AllocationSite lockAS = ObjectAllocationTracker.getAllocSiteForObject(lockEI.getObjectRef());
			
			if (lockAS == null)
			{
				if (lockEI.getClassInfo().getName().equals("java.lang.Class"))
				{
					// get name of the class represented by this class object
					
					String lvarClassName = ObjectAllocationTracker.getClassNameForClassObject(lockEI.getObjectRef());
					
					lockAS = JPFUtils.getAllocSiteForClass(lvarClassName);
				}
			}
			
			lockObjAllocSites.add(lockAS);
		}
			
		return lockObjAllocSites;
	}
	
	public static List<Integer> getThisObjectRefsForConstructors(ThreadInfo th)
	{
		List<Integer> thInitObjRefs = new ArrayList<Integer>();
		
		for (StackFrame sf : th)
		{
			if (sf.getMethodInfo().isInit())
			{
				thInitObjRefs.add(sf.getThis());
			}
		}
		
		return thInitObjRefs;
	}
	
	public static AllocationSite getAllocSiteFromInsn(Instruction insn)
	{
		String methodSig = getMethodSignature(insn.getMethodInfo());

		return new AllocationSite(methodSig, insn.getPosition());
	}
	
	public static AllocationSite getAllocSiteForClass(String className)
	{
		return new AllocationSite(className+".<clinit>", -1);
	}

	/**
	 * Creates an object string that is ordered from the outermost receiver object (bottom of the stack frame, the procedure &quot;main&quot;) to the receiver object for the current method (top of the stack frame).
	 */	
	public static ObjectStringContext getReceiverObjectStringForThreadStack(ThreadInfo th)
	{
		ObjectStringContext ctx = new ObjectStringContext();

		for (StackFrame sf : th)
		{
			// ignore synthetic methods like "[run]"
			if (sf.isSynthetic())
			{
				if (sf.getMethodInfo().getName().equals("[run]")) continue;
				if (sf.getMethodInfo().getName().equals("[main]")) continue;				
			}
			
			// plain alloc site = without context
			AllocationSite receiverObjectPlainAS = null;

			MethodInfo mth = sf.getMethodInfo();
			
			if (mth.isStatic()) 
			{
				receiverObjectPlainAS = getAllocSiteForClass(mth.getClassInfo().getName());
			}
			else
			{
				AllocationSite recObjFullAS = ObjectAllocationTracker.getAllocSiteForObject(sf.getThis());

				// we do not know so we skip (this happens for "main" and some JVM-internal calls)
				if (recObjFullAS == null) continue;

				// we must strip the context information from the full allocation site
				receiverObjectPlainAS = AllocationSite.makeCopyWithoutContext(recObjFullAS);
			}
			
			ctx.addFirstElement(receiverObjectPlainAS);
		}

		return ctx;
	}
	
	public static boolean isSchedulingPointAtFieldAccess(FieldInfo fi, ThreadInfo ti, MethodInfo mi, int insnIndex)
	{
		// thread scheduling point (i.e., choice generator) is always created at field accesses inside the application classes
			// exceptions: class init, monitor enter prologue
			
		if (fi.neverBreak()) return false;
		
		if (mi.isClinit() && (fi.getClassInfo().equals(mi.getClassInfo()))) return false;
		
		// we also check for the monitor enter prologue
		boolean existsPrologue = false;
		
		Instruction[] code = mi.getInstructions();
		
		insnIndex++;
		
		if (insnIndex < (code.length - 3))
		{ 
			if (code[insnIndex] instanceof DUP) 
			{ 
				insnIndex++;
				
				if (code[insnIndex] instanceof ASTORE) insnIndex++;
					
				if (code[insnIndex] instanceof MONITORENTER) existsPrologue = true;
			}
		}
		
		if (existsPrologue) return false;
		
		return true;
	}

	private static String getRunMethodSignatureForThread(ThreadInfo curTh)
	{
		ElementInfo thObjEI = curTh.getThreadObject();
				
		// create signature for the "run()" method of this thread (its class)
		String methodSig = thObjEI.getClassInfo().getName() + ".run()V";

		return methodSig;
	}

	private static String getMethodSignatureForNewlyStartedThread(ThreadInfo curTh, StackFrame curSF, boolean checkRunning)
	{
		ElementInfo rcvObjEI = curTh.getHeap().get(curSF.getThis());
				
		// if the current thread is still executing the call of Thread.start() but the dynamic receiving thread object already runs (it has the method "run()" on its call stack), then we can safely ignore the call of "Thread.start()"
		if (checkRunning)
		{
			if (isAlreadyRunningThreadObject(curTh.getVM(), rcvObjEI)) return null;
		}
				
		// create signature for the "run()" method of the dynamic receiver object (its class)
		String methodSig = rcvObjEI.getClassInfo().getName() + ".run()V";

		return methodSig;
	}
	
	private static boolean isActiveThreadRunMethod(ThreadInfo curTh, StackFrame curSF, String curMethodSig)
	{
		if ( ! curMethodSig.endsWith(".run()V") ) return false;
		
		// curSF.this == -1 in this case
		if (curSF.getMethodInfo().getName().equals("[run]")) return true;

		ElementInfo rcvObjEI = curTh.getHeap().get(curSF.getThis());
		
		return isThreadClass(rcvObjEI.getClassInfo());
	}
	
	public static boolean isThreadClass(ClassInfo ci)
	{
		if (ci.isThreadClassInfo()) return true;
		
		ClassInfo ciSuper = ci.getSuperClass();
		while (ciSuper != null)
		{
			if (ciSuper.isThreadClassInfo()) return true;
			ciSuper = ciSuper.getSuperClass();
		}
		
		return false;
	}

	public static String getArrayClassName(ElementInfo arrayObjEI)
	{
		ClassInfo ci = arrayObjEI.getClassInfo();

		int dim = 0;
		
		while (ci.isArray())
		{
			ci = ci.getComponentClassInfo();
			dim++;
		}
		
		StringBuffer arrayTypeStrBuf = new StringBuffer();
		
		arrayTypeStrBuf.append(ci.getName());
		
		for (int i = 0; i < dim; i++) arrayTypeStrBuf.append("[]");
		
		return arrayTypeStrBuf.toString();
	}
	
	public static int getArrayDimensionsCount(ElementInfo arrayObjEI)
	{
		ClassInfo ci = arrayObjEI.getClassInfo();

		int dim = 0;
		
		while (ci.isArray())
		{
			ci = ci.getComponentClassInfo();
			dim++;
		}
		
		return dim;
	}
	
	public static Integer getCurrentValueFieldAccessPath(ThreadInfo th, String sfMethodSig, FieldAccessPath fap)
	{
		ElementInfo rootObjEI = null;
		
		if (fap.rootObjectID instanceof LocalVarName)
		{
			// instance fields			
			// we consider only the local variable "this" ("local0")
			
			LocalVarName lvn = (LocalVarName) fap.rootObjectID;
			
			if ( ! lvn.getAsString().equals("local0") ) return null;
			
			StackFrame sf = getStackFrameForMethod(th, sfMethodSig);
			
			rootObjEI = th.getVM().getHeap().get(sf.getThis()); 
		}
		
		if (fap.rootObjectID instanceof ClassName)
		{
			// static fields
			
			ClassName cn = (ClassName) fap.rootObjectID;
			
			ClassInfo ci = ObjectAllocationTracker.getClassInformationForName(cn.getAsString());
			
			rootObjEI = ci.getStaticElementInfo();
		}
		
		// traverse field access path
		
		ElementInfo curObjEI = rootObjEI;
		
		for (int i = 0; i < fap.fieldNameList.size() - 1; i++)
		{
			FieldName fn = fap.fieldNameList.get(i);
			
			int nextObjRef = curObjEI.getReferenceField(fn.fieldNameStr);
			
			curObjEI = th.getVM().getHeap().get(nextObjRef);
		}
		
		FieldName lastFN = fap.fieldNameList.get(fap.fieldNameList.size() - 1);
		
		int fapValue = 0;
		
		if (curObjEI.isArray())
		{
			// the only possible field name is "length" (see the class bytecode/interpreter/ExecutionSimulator)
			if (lastFN.fieldNameStr.equals("length")) fapValue = curObjEI.arrayLength();
			else return null;
		}
		else
		{
			fapValue = curObjEI.getIntField(lastFN.fieldNameStr);
		}
		
		return new Integer(fapValue);
	}

	public static Integer getCurrentValueLocalVariable(ThreadInfo th, String sfMethodSig, LocalVarName lv)
	{
		StackFrame sf = getStackFrameForMethod(th, sfMethodSig);
		
		int lvarValue = sf.getLocalVariable(lv.getIndex());
		
		return new Integer(lvarValue);
	}
	
	private static StackFrame getStackFrameForMethod(ThreadInfo th, String tgtMethodSig)
	{
		for (StackFrame sf : th)
		{
			String curMthSig = getMethodSignature(sf.getMethodInfo());

			if (curMthSig.equals(tgtMethodSig)) return sf;
		}
		
		// this really should not happen
		return null;
	}
	
	public static int getTargetObjRefForFieldAccess(InstanceFieldInstruction fieldInsn, ThreadInfo curTh)
	{
		int targetObjRef = -1;
		
		if (fieldInsn instanceof GETFIELD)
		{
			GETFIELD gfInsn = (GETFIELD) fieldInsn;

			targetObjRef = curTh.getTopFrame().peek();
		}
		
		if (fieldInsn instanceof PUTFIELD)
		{
			PUTFIELD pfInsn = (PUTFIELD) fieldInsn;

			targetObjRef = (pfInsn.getFieldInfo().getStorageSize() == 1) ? curTh.getTopFrame().peek(1) : curTh.getTopFrame().peek(2);
		}
		
		return targetObjRef;
	}
}
