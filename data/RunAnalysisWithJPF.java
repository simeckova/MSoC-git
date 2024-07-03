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
package cz.cuni.mff.d3s.stdynpor;

import java.lang.reflect.Method;
 
import java.util.Date;
import java.util.Set;
import java.util.List;
import java.util.Map;

import java.io.File;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

import cz.cuni.mff.d3s.stdynpor.data.*;
import cz.cuni.mff.d3s.stdynpor.analysis.*;
import cz.cuni.mff.d3s.stdynpor.analysis.fieldaccess.*;
import cz.cuni.mff.d3s.stdynpor.analysis.synchevent.*;
import cz.cuni.mff.d3s.stdynpor.analysis.pointeralias.*;
import cz.cuni.mff.d3s.stdynpor.analysis.arrayaccess.*;
import cz.cuni.mff.d3s.stdynpor.analysis.localvariable.*;
import cz.cuni.mff.d3s.stdynpor.analysis.methodcall.*;
import cz.cuni.mff.d3s.stdynpor.jpf.*;
import cz.cuni.mff.d3s.stdynpor.wala.*;


// this class is derived from RunJPF
public class RunAnalysisWithJPF
{
	private static boolean printJPFSettings = false;
	
	private static boolean printCallGraph = false;
	private static boolean printMethodIR = false;
	private static boolean printInterProcCFG = false;
	
	private static boolean printFieldAccesses = false;
	private static boolean printImmutableFields = false;
	private static boolean printAllocationSites = false;
	private static boolean printSynchEventsMay = false;
	private static boolean printSynchEventsMust = false;
	private static boolean printSynchTypesMust = false;
	private static boolean printUnblockEvents = false;
	private static boolean printLockPatternThis = false;
	private static boolean printArrayAccesses = false;
	private static boolean printArrayElements = false;
	private static boolean printLocalVarAccesses = false;
	private static boolean printMethodCalls = false;
	

	public static void main(String[] args) 
	{
		Date analysisStartTime = new Date();

		processFlags(args);

		gov.nasa.jpf.Config jpfConfig = new gov.nasa.jpf.Config(args);

		// get main class name from JPF configuration parameters (including command line)
		String mainClassName = jpfConfig.getProperty("target");
		if (mainClassName == null)
		{
			String[] freeArgs = jpfConfig.getFreeArgs();
			if (freeArgs != null) mainClassName = freeArgs[0];
		}
		
		// step 0: init the static analysis library
		
		String targetClassPath = jpfConfig.getString("analysis.target.dir", "");
	
		// exclusion file identifies library class that are ignored by the static analysis
		String walaExclusionFilePath = jpfConfig.getString("analysis.exclusion.file", "");

		try
		{
			WALAUtils.initLibrary(mainClassName, targetClassPath, walaExclusionFilePath);
		}
		catch (Exception ex)
		{
			System.out.println("[ERROR] initialization failed");
			ex.printStackTrace();
		}
		
		// step 1: run configured static analyses 

		try
		{
			runStaticAnalysis(mainClassName, targetClassPath, walaExclusionFilePath);
		}
		catch (Exception ex)
		{
			System.out.println("[ERROR] static analysis failed");
			ex.printStackTrace();
		}

		Date analysisFinishTime = new Date();
		
		long analysisUsedMemoryInMB = (Runtime.getRuntime().totalMemory() >> 20);
  		
		System.out.println("[ANALYSIS] time = " + printTimeDiff(analysisStartTime, analysisFinishTime) + " s, memory = " + analysisUsedMemoryInMB + " MB \n");

		// step 2: run JPF

		Date jpfStartTime = new Date();
		
		try
		{
			if (printJPFSettings) jpfConfig.printEntries();

			ClassLoader cl = jpfConfig.initClassLoader(RunAnalysisWithJPF.class.getClassLoader());
			Class jpfCls = cl.loadClass("gov.nasa.jpf.JPF");
			Method jpfStartMth = jpfCls.getMethod("start", new Class[]{jpfConfig.getClass(), args.getClass()});
			jpfStartMth.invoke(null, new Object[]{jpfConfig, args});
		} 
		catch (Exception ex) 
		{
			System.out.println("[ERROR] cannot start JPF");
			ex.printStackTrace();
			if (ex.getCause() != null) ex.getCause().printStackTrace();
		}
		
		Date jpfFinishTime = new Date();
		
		System.out.println("[JPF] time = " + printTimeDiff(jpfStartTime, jpfFinishTime) + " s \n");
	}
	
	private static void processFlags(String[] args)
	{
		if (args == null) return;
		
		for (int i = 0; i < args.length; i++) 
		{
			if (args[i].equals("-debugincludelibs"))
			{
				args[i] = null;
				Configuration.debugIncludeLibs = true;
			}
			else if (args[i].startsWith("-usepointeralias:"))
			{
				String[] options = getFlagOptions(args[i]);
				
				args[i] = null;
				
				Configuration.usingPointerAlias = true;
				
				// we consider only the first value
				if (options.length > 0)
				{
					if (options[0].equals("exhaustive")) Configuration.enabledPAExhaustive = true;
					if (options[0].equals("exhaustobjctx")) Configuration.enabledPAExhaustObjCtx = true;
					if (options[0].equals("demanddrv")) Configuration.enabledPADemandDrv = true;
					if (options[0].equals("demanddrvctx")) Configuration.enabledPADemandDrvCtx = true;						
				}
			}
			else if (args[i].startsWith("-usefieldaccess:"))
			{
				String[] options = getFlagOptions(args[i]);
				
				args[i] = null;
				
				Configuration.usingFieldAccess = true;
				
				// the first option must be the mode of field access analysis
				if (options.length > 0)
				{
					if (options[0].equals("insensitive")) Configuration.enabledFAInsensitive = true;
					if (options[0].equals("calleectx")) Configuration.enabledFACalleeCtx = true;
					if (options[0].equals("ctxsensitive")) Configuration.enabledFACtxSensitive = true;				
				}
				
				if (options.length > 1)
				{
					if (options[1].equals("savelocvaracc")) Configuration.saveFALocalVarAccess = true;
				}
			}
			else if (args[i].startsWith("-useimmutablefields"))
			{
				args[i] = null;				
				Configuration.usingImmutableFields = true;				
			}
			else if (args[i].startsWith("-usehappensbefore:"))
			{
				String[] options = getFlagOptions(args[i]);
				
				args[i] = null;
				
				// pointer analysis is required for the may-happen-before analysis
				Configuration.usingPointerAlias = true;
				
				// context-sensitive field access analysis is required for the may-happen-before analysis
				Configuration.usingFieldAccess = true;
				Configuration.enabledFACtxSensitive = true;
				Configuration.enabledFACalleeCtx = false;
				Configuration.enabledFAInsensitive = false;
				
				// allocation sites analysis is required for the may-happen-before analysis
				Configuration.usingAllocationSites = true;
				
				Configuration.usingHappensBefore = true;
				
				for (int j = 0; j < options.length; j++)
				{
					if (options[j].equals("waitnotify")) Configuration.enabledHBWaitNotify = true;
					if (options[j].equals("lockacqrel")) Configuration.enabledHBLockAcqRel = true;
					if (options[j].equals("lockpattern")) Configuration.enabledHBLockPattern = true;
					if (options[j].equals("threadjoin")) Configuration.enabledHBThreadJoin = true;
				}
			}
			else if (args[i].equals("-usearrayaccess"))
			{
				args[i] = null;
				Configuration.usingArrayAccess = true;
			}
			else if (args[i].equals("-usearrayelements"))
			{
				args[i] = null;
				
				// context-sensitive field access analysis is required for the array elements analysis
				Configuration.usingFieldAccess = true;
				Configuration.enabledFACtxSensitive = true;
				Configuration.enabledFACalleeCtx = false;
				Configuration.enabledFAInsensitive = false;
				
				// allocation sites analysis is required for the array elements analysis
				Configuration.usingAllocationSites = true;
				
				Configuration.usingArrayElements = true;
			}
			else if (args[i].equals("-uselocalvaraccess"))
			{
				args[i] = null;
				Configuration.usingLocalVarAccess = true;
			}
			else if (args[i].equals("-usemethodcalls"))
			{
				args[i] = null;
				Configuration.usingMethodCalls = true;
			}
			else if (args[i].equals("-usedynamicpor"))
			{
				args[i] = null;
				Configuration.usingDynamicPOR = true;
			}
			else if (args[i].equals("-usevaldeterm"))
			{
				args[i] = null;
				Configuration.usingValueDeterminacy = true;
			}
			else if (args[i].equals("-excludechildthreads"))
			{
				args[i] = null;
				Configuration.allowExcludingChildThreads = true;
			}
			else if (args[i].equals("-printjpfsettings")) 
			{
				args[i] = null;
				printJPFSettings = true;
			}
			else if (args[i].equals("-printcallgraph"))
			{
				args[i] = null;
				printCallGraph = true;
			}
			else if (args[i].equals("-printmethodir"))
			{
				args[i] = null;
				printMethodIR = true;
			}
			else if (args[i].equals("-printinterproccfg"))
			{
				args[i] = null;
				printInterProcCFG = true;
			}
			else if (args[i].equals("-printfieldaccess"))
			{
				args[i] = null;
				printFieldAccesses = true;
			}
			else if (args[i].equals("-printimmutablefields"))
			{
				args[i] = null;
				printImmutableFields = true;
			}
			else if (args[i].equals("-printallocsites"))
			{
				args[i] = null;
				printAllocationSites = true;
			}
			else if (args[i].equals("-printsyncheventsmay"))
			{
				args[i] = null;
				printSynchEventsMay = true;
			}
			else if (args[i].equals("-printsyncheventsmust"))
			{
				args[i] = null;
				printSynchEventsMust = true;
			}
			else if (args[i].equals("-printsynchtypes"))
			{
				args[i] = null;
				printSynchTypesMust = true;
			}
			else if (args[i].equals("-printunblockevents"))
			{
				args[i] = null;
				printUnblockEvents = true;
			}
			else if (args[i].equals("-printlockpatternthis"))
			{
				args[i] = null;
				printLockPatternThis = true;
			}			
			else if (args[i].equals("-printarrayaccess"))
			{
				args[i] = null;
				printArrayAccesses = true;
			}
			else if (args[i].equals("-printarrayelements"))
			{
				args[i] = null;
				printArrayElements = true;
			}
			else if (args[i].equals("-printlocalvaraccess"))
			{
				args[i] = null;
				printLocalVarAccesses = true;
			}
			else if (args[i].equals("-printmethodcalls"))
			{
				args[i] = null;
				printMethodCalls = true;
			}
			else if (args[i].equals("-printresultsfull"))
			{
				args[i] = null;
				AnalysisResultProcessor.printResultsFull = true;
				TransitionSchedulerFactory.printResultsFull = true;
			}
			else if (args[i].equals("-printchoicedecision"))
			{
				args[i] = null;
				AnalysisResultProcessor.printChoiceDecision = true;
				cz.cuni.mff.d3s.stdynpor.jpf.dpor.SearchDriver.printChoiceDecision = true;
				cz.cuni.mff.d3s.stdynpor.jpf.valdet.SearchDriver.printChoiceDecision = true;
			}
			else if (args[i].equals("-printchoicefilter"))
			{
				args[i] = null;
				TransitionSchedulerFactory.printChoiceFilter = true;
			}
		}
	}
	
	private static String[] getFlagOptions(String flagStr)
	{
		int k = flagStr.indexOf(':');
		if (k == -1) return new String[0];
		
		String optionsStr = flagStr.substring(k+1);
		
		return optionsStr.split(",");
	}

	private static void runStaticAnalysis(String mainClassName, String targetClassPath, String walaExclusionFilePath) throws Exception
	{
		// just to keep the argument lists compact (avoiding prefix "WALAUtils")
		IClassHierarchy classHierarchy = WALAUtils.classHierarchy;
		AnalysisScope scope = WALAUtils.scope;
		AnalysisOptions options = WALAUtils.options;
		AnalysisCache cache = WALAUtils.cache;
		
		// build call graph and optionally compute pointer analysis to identify objects (for aliasing)
				
		SSAPropagationCallGraphBuilder cgBuilder = null;
		
		if (Configuration.usingPointerAlias) 
		{
			if (Configuration.enabledPAExhaustObjCtx)
			{
				// object-context-sensitive pointer analysis
				cgBuilder = Util.makeVanillaZeroOneContainerCFABuilder(options, cache, classHierarchy, scope);				
			}			
			else
			{
				// standard context-insensitive exhaustive pointer analysis (andersen)
				cgBuilder = Util.makeVanillaZeroOneCFABuilder(options, cache, classHierarchy, scope);
			}			
		}
		else
		{
			// this uses a single common allocation site for every type/class (for all objects)
			cgBuilder = Util.makeZeroCFABuilder(options, cache, classHierarchy, scope);
		}
		
		CallGraph clGraph = cgBuilder.makeCallGraph(options, null);
	
		if (printCallGraph) WALAUtils.printCallGraph(clGraph, 5);
		
		WALAUtils.loadMethodNodesCache(clGraph);
		
		if (Configuration.usingPointerAlias)
		{
			PointerAnalysis pa = cgBuilder.getPointerAnalysis();
			
			if (Configuration.enabledPAExhaustive || Configuration.enabledPAExhaustObjCtx)
			{
				PointerAnalysisData.collectAllocationSites(pa);
				PointerAnalysisData.computeAliasingInformation();
			}
			else if (Configuration.enabledPADemandDrv || Configuration.enabledPADemandDrvCtx)
			{
				PointerAnalysisData.initDemandDrivenAnalysis(cgBuilder, clGraph, pa);
			}			
		}
		
		if (printMethodIR) WALAUtils.printAllMethodsIR(clGraph);
		
		ExplodedInterproceduralCFG icfg = ExplodedInterproceduralCFG.make(clGraph);
		
		if (printInterProcCFG) WALAUtils.printInterProcCFG(icfg);
		
		if (Configuration.usingFieldAccess) 
		{		
			// compute field access analysis 
				// for each code location in each thread find all future field accesses
				
			Date startFieldAccess = new Date();
			
			// compute full results (default option)
			if (Configuration.enabledFACtxSensitive) ContextSensitiveFieldAccessAnalysis.analyzeProgram(clGraph, icfg, false);
			if (Configuration.enabledFACalleeCtx) CalleeContextFieldAccessAnalysis.analyzeProgram(clGraph, icfg, false);
			if (Configuration.enabledFAInsensitive) InsensitiveFieldAccessAnalysis.analyzeProgram(clGraph, icfg, false);
			
			// compute also results for when child threads are excluded (secondary)
			if (Configuration.allowExcludingChildThreads)
			{
				if (Configuration.enabledFACtxSensitive) ContextSensitiveFieldAccessAnalysis.analyzeProgram(clGraph, icfg, true);
				if (Configuration.enabledFACalleeCtx) CalleeContextFieldAccessAnalysis.analyzeProgram(clGraph, icfg, true);
				if (Configuration.enabledFAInsensitive) InsensitiveFieldAccessAnalysis.analyzeProgram(clGraph, icfg, true);
			}
			
			if (Configuration.enabledPADemandDrv || Configuration.enabledPADemandDrvCtx)
			{
				FieldAccessCodeInfo.saveTargetRefsForFieldAccesses(clGraph);
			}
			
			Date finishFieldAccess = new Date();
			
			if (printFieldAccesses) FieldAccessAnalysisBase.printFieldAccesses(false);
			
			System.out.println("[ANALYSIS] field access analysis time = " + printTimeDiff(startFieldAccess, finishFieldAccess) + " s \n");
		
			if (Configuration.usingImmutableFields)
			{
				Date startImmutableFields = new Date();
				
				StagedSummaryEscapeAnalysis.analyzeProgram(clGraph);
				Map<ProgramPoint, Set<LocalVarID>> pp2EscapedVars = StagedSummaryEscapeAnalysis.getEscapedVariables();
				
				ImmutableFieldsDetector.analyzeProgram(clGraph, pp2EscapedVars);
	
				Date finishImmutableFields = new Date();
				
				if (printImmutableFields) ImmutableFieldsDetector.printImmutableFields();
				
				System.out.println("[ANALYSIS] immutable fields analysis time = " + printTimeDiff(startImmutableFields, finishImmutableFields) + " s \n");
			}
		}

		if (Configuration.usingAllocationSites)
		{
			// find all future allocation sites that can be visited after a given program point

			Date startFutureAlloc = new Date();
				
			AllocationSitesAnalysis.analyzeProgram(clGraph, icfg);

			Date finishFutureAlloc = new Date();
				
			if (printAllocationSites) AllocationSitesAnalysis.printAllocationSites();
				
			System.out.println("[ANALYSIS] future allocation analysis time = " + printTimeDiff(startFutureAlloc, finishFutureAlloc) + " s \n");
		}
		
		if (Configuration.usingHappensBefore)
		{
			if (Configuration.enabledHBWaitNotify)
			{
				// find all synchronization events that may appear between a program point P and the nearest future access to some field F
			
				Date startMayWait = new Date();

				SynchronizationEventMayAnalysis.analyzeProgram(clGraph, icfg);
				
				Date finishMayWait = new Date();
		
				if (printSynchEventsMay)
				{
					SynchronizationEventMayAnalysis.printSynchEventsBeforeNextFieldAccess();
					SynchronizationEventMayAnalysis.printNextFieldAccesses();
				}
				
				System.out.println("[ANALYSIS] may wait analysis time = " + printTimeDiff(startMayWait, finishMayWait) + " s \n");
				
				// find if there is a call to "wait" on every path between a program point P and the nearest future access to some field F

				Date startMustWait = new Date();
				
				SynchronizationTypeMustAnalysis.analyzeProgram(clGraph, icfg, SynchEventType.WAIT);
				
				Date finishMustWait = new Date();
		
				if (printSynchTypesMust)
				{
					Map<ProgramPoint, Set<FieldID>> pp2ffr = ContextSensitiveFieldAccessAnalysis.getFutureFieldReadsData(false);
					Map<ProgramPoint, Set<FieldID>> pp2ffw = ContextSensitiveFieldAccessAnalysis.getFutureFieldWritesData(false);
					SynchronizationTypeMustAnalysis.printFieldsAfterSynchEvent(pp2ffr, pp2ffw);
				}
				
				System.out.println("[ANALYSIS] must wait analysis time = " + printTimeDiff(startMustWait, finishMustWait) + " s \n");
				
				// find all future unblocking events (notify, unlock) that can happen after a given program point
				
				Date startMayNotify = new Date();

				UnblockingEventAnalysis.analyzeProgram(clGraph, icfg);

				Date finishMayNotify = new Date();
				
				if (printUnblockEvents) UnblockingEventAnalysis.printUnblockEvents();
				
				System.out.println("[ANALYSIS] may notify analysis time = " + printTimeDiff(startMayNotify, finishMayNotify) + " s \n");
			}
			
			if (Configuration.enabledHBLockAcqRel || Configuration.enabledHBThreadJoin)
			{
				// find all synchronization events that must appear between a program point P and the nearest future access to some field F

				Date startMustLockJoin = new Date();
				
				SynchronizationEventMustAnalysis.analyzeProgram(clGraph, icfg);
				
				Date finishMustLockJoin = new Date();
		
				if (printSynchEventsMust)
				{
					SynchronizationEventMustAnalysis.printSynchEventsBeforeNextFieldAccess();
					SynchronizationEventMustAnalysis.printNextFieldAccesses();
				}
				
				System.out.println("[ANALYSIS] must lock/join analysis time = " + printTimeDiff(startMustLockJoin, finishMustLockJoin) + " s \n");
			}
			
			if (Configuration.enabledHBLockPattern)
			{
				// find all fields that are accessed through the local variable "this" and inside synchronized blocks over "this"
				
				Date startLockPattern = new Date();

				LockPatternThisAnalysis.analyzeProgram(clGraph, icfg);
				
				Date finishLockPattern = new Date();
				
				if (printLockPatternThis)
				{
					LockPatternThisAnalysis.printLockedProgramPointsInEachMethod();
					Map<ProgramPoint, Set<FieldID>> pp2ffr = ContextSensitiveFieldAccessAnalysis.getFutureFieldReadsData(false);
					Map<ProgramPoint, Set<FieldID>> pp2ffw = ContextSensitiveFieldAccessAnalysis.getFutureFieldWritesData(false);
					LockPatternThisAnalysis.printFieldAccessesOnThisLocked(pp2ffr, pp2ffw);
				}
				
				System.out.println("[ANALYSIS] lock patterns analysis time = " + printTimeDiff(startLockPattern, finishLockPattern) + " s \n");
			}
		}
		
		if (Configuration.usingArrayAccess)
		{
			// compute array object access analysis 
				// for each code location in each thread find all future accesses to array objects				

			Date startArrayObjectAccess = new Date();
			
			/// compute full results (default option)
			ArrayObjectAccessAnalysis.analyzeProgram(clGraph, icfg, false);
			
			// compute also results for when child threads are excluded (secondary)
			if (Configuration.allowExcludingChildThreads)
			{
				ArrayObjectAccessAnalysis.analyzeProgram(clGraph, icfg, true);
			}				
			
			Date finishArrayObjectAccess = new Date();
			
			if (printArrayAccesses) ArrayObjectAccessAnalysis.printArrayAccesses();
			
			System.out.println("[ANALYSIS] array object access analysis time = " + printTimeDiff(startArrayObjectAccess, finishArrayObjectAccess) + " s \n");
		
			if (Configuration.usingArrayElements)
			{			
				// compute symbolic expressions that represent array element indexes used at respective array access instructions
				
				Date startSymbolElementIndex = new Date();
					
				SymbolicArrayElementIndexAnalysis.analyzeProgram(clGraph);
				
				Date finishSymbolElementIndex = new Date();
				
				if (printArrayElements) SymbolicArrayElementIndexAnalysis.printSymbolicElementIndexes();
				
				System.out.println("[ANALYSIS] symbolic array element indexes analysis time = " + printTimeDiff(startSymbolElementIndex, finishSymbolElementIndex) + " s \n");
				
				// compute array element access analysis
					// for each code location (program point) find all future accesses to array elements
					
				Date startArrayElementAccess = new Date();
					
				ArrayElementAccessAnalysis.analyzeProgram(clGraph, icfg);
				
				Date finishArrayElementAccess = new Date();
				
				if (printArrayElements) ArrayElementAccessAnalysis.printArrayElementAccesses();
				
				System.out.println("[ANALYSIS] array element access analysis time = " + printTimeDiff(startArrayElementAccess, finishArrayElementAccess) + " s \n");
			}
		}
		
		if (Configuration.usingLocalVarAccess) 
		{		
			// compute local variable access analysis 
				// for each code location in each thread find all future accesses to local variables
				
			Date startLocalVarAccess = new Date();

			LocalVariableAccessAnalysis.analyzeProgram(clGraph);
			
			Date finishLocalVarAccess = new Date();
			
			if (printLocalVarAccesses) LocalVariableAccessAnalysis.printLocalVariableAccesses();
			
			System.out.println("[ANALYSIS] local variable access analysis time = " + printTimeDiff(startLocalVarAccess, finishLocalVarAccess) + " s \n");
		}

		if (Configuration.usingMethodCalls) 
		{		
			// compute method calls analysis 
				// for each code location in each thread find all future method calls
				
			Date startMethodCalls = new Date();

			MethodCallsAnalysis.analyzeProgram(clGraph, icfg);
			
			Date finishMethodCalls = new Date();
			
			if (printMethodCalls) MethodCallsAnalysis.printMethodCalls();
			
			System.out.println("[ANALYSIS] method calls analysis time = " + printTimeDiff(startMethodCalls, finishMethodCalls) + " s \n");
		}
	}

	private static String printTimeDiff(Date start, Date finish)
	{
		long startMS = start.getTime();
		long finishMS = finish.getTime();
	
		long diffMS = finishMS - startMS;
    	
		long diffSeconds = (diffMS / 1000);
    	
		return String.valueOf(diffSeconds);
	}
}

