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
package cz.cuni.mff.d3s.stdynpor.analysis.synchevent;

import java.util.Iterator;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.List;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.intset.BitVector;
import com.ibm.wala.util.intset.OrdinalSetMapping;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.IntSet; 
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.graph.impl.GraphInverter;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.dataflow.graph.BitVectorFramework;
import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.BitVectorKillGen;
import com.ibm.wala.dataflow.graph.BitVectorUnion;
import com.ibm.wala.dataflow.graph.BitVectorIdentity;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;

import cz.cuni.mff.d3s.stdynpor.data.ProgramPoint;
import cz.cuni.mff.d3s.stdynpor.data.AllocationSite;
import cz.cuni.mff.d3s.stdynpor.data.SynchEventID;
import cz.cuni.mff.d3s.stdynpor.data.SynchEventType;
import cz.cuni.mff.d3s.stdynpor.bytecode.ByteCodeUtils;
import cz.cuni.mff.d3s.stdynpor.wala.WALAUtils;
import cz.cuni.mff.d3s.stdynpor.wala.PointerAnalysisData;
import cz.cuni.mff.d3s.stdynpor.wala.SynchEventCodeInfo;
import cz.cuni.mff.d3s.stdynpor.wala.dataflow.BitVectorKillAll;
import cz.cuni.mff.d3s.stdynpor.Debug;


public class UnblockingEventAnalysis
{
	// map from program point (method signature and instruction position) to the set of unblocking events (notify, monitor exit)
	protected static Map<ProgramPoint, Set<SynchEventID>> pp2FutureUnblockEvents;

	// mapping between unblocking events and integer numbers (used in bitvectors)
	protected static OrdinalSetMapping<SynchEventID> eventsNumbering;
	
	static
	{
		pp2FutureUnblockEvents = new LinkedHashMap<ProgramPoint, Set<SynchEventID>>();
		
		eventsNumbering = null;
	}
	

	public static void analyzeProgram(CallGraph clGraph, ExplodedInterproceduralCFG icfg) throws Exception
	{
		// create the backwards-oriented control-flow graph of the program
		Graph<BasicBlockInContext<IExplodedBasicBlock>> bwICFG = GraphInverter.invert(icfg);

		createEventsNumbering(bwICFG);
		
		UnblockEvents ue = new UnblockEvents(bwICFG);
		BitVectorSolver<BasicBlockInContext<IExplodedBasicBlock>> solverUE = ue.analyze();

		for (BasicBlockInContext<IExplodedBasicBlock> bb : bwICFG) 
		{
			IExplodedBasicBlock ebb = bb.getDelegate();

			int insnPos = WALAUtils.getInsnBytecodePos(bb.getNode(), ebb.getFirstInstructionIndex());
		
			String fullMethodSig = WALAUtils.getMethodSignature(bb.getNode().getMethod());
		
			ProgramPoint pp = new ProgramPoint(fullMethodSig, insnPos);

			Set<SynchEventID> events = new HashSet<SynchEventID>();
		
			IntSet out = solverUE.getOut(bb).getValue();
			if (out != null)
			{
				for (IntIterator outIt = out.intIterator(); outIt.hasNext(); )
				{
					int eventNum = outIt.next();
				
					SynchEventID sev = eventsNumbering.getMappedObject(eventNum);
				
					events.add(sev);
				}	
			}
			
			pp2FutureUnblockEvents.put(pp, events);
		}
	}
	
	public static Set<SynchEventID> getUnblockingEventsForProgramPoint(ProgramPoint pp)
	{
		return pp2FutureUnblockEvents.get(pp);
	}

	public static void printUnblockEvents()
	{
		System.out.println("UNBLOCKING EVENTS");
		System.out.println("=================");
		
		Set<ProgramPoint> progPoints = new TreeSet<ProgramPoint>();
		progPoints.addAll(pp2FutureUnblockEvents.keySet());
		
		for (ProgramPoint pp : progPoints)
		{
			if ( ! Debug.isWatchedEntity(pp.methodSig) ) continue;
			
			Set<SynchEventID> events = pp2FutureUnblockEvents.get(pp);

			System.out.println(pp.methodSig + ":" + pp.insnPos);
			
			for (SynchEventID ev : events)
			{
				if ( ! Debug.isWatchedEntity(ev.tgtObjectID.toString()) ) continue;
				
				System.out.println("\t " + ev.toString());
			}
		}
		
		System.out.println("");
	}
		
	protected static void createEventsNumbering(Graph<BasicBlockInContext<IExplodedBasicBlock>> icfg)
	{
		eventsNumbering = new MutableMapping<SynchEventID>(new SynchEventID[1]);
		
		for (BasicBlockInContext<IExplodedBasicBlock> bb : icfg)
		{
			CGNode mthNode = bb.getNode();
				
			IR methodIR = mthNode.getIR();
				
			if (methodIR == null) continue;
			
			String methodSig = WALAUtils.getMethodSignature(mthNode.getMethod());
					
			int firstInsnIdx = bb.getFirstInstructionIndex();
			int lastInsnIdx = bb.getLastInstructionIndex();
   
			// basic block without instructions
			if ((firstInsnIdx < 0) || (lastInsnIdx < 0)) continue;

			SSAInstruction[] instructions = methodIR.getInstructions();

			for (int i = firstInsnIdx; i <= lastInsnIdx; i++) 
			{
				SSAInstruction insn = instructions[i];
				
				try
				{
					// get unblocking events represented by the current instruction
					Set<SynchEventID> eventIDs = SynchEventCodeInfo.getUnblockEventsForInsn(mthNode, methodIR, insn);
					
					for (SynchEventID sev : eventIDs) eventsNumbering.add(sev);
				}
				catch (Exception ex) { ex.printStackTrace(); }
			}
		}
	}

	
	// collecting unblocking events before return from the method
	static class UnblockEvents
	{
		private Graph<BasicBlockInContext<IExplodedBasicBlock>> bwICFG;
		
		public UnblockEvents(Graph<BasicBlockInContext<IExplodedBasicBlock>> bwICFG)
		{
			this.bwICFG = bwICFG;
		}
			
		class TransferFunctionsUE implements ITransferFunctionProvider<BasicBlockInContext<IExplodedBasicBlock>, BitVectorVariable> 
		{
			public AbstractMeetOperator<BitVectorVariable> getMeetOperator() 
			{
				return BitVectorUnion.instance();
			}

			public UnaryOperator<BitVectorVariable> getEdgeTransferFunction(BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dst) 
			{
				// we do not propagate through return-to-exit edges 
				
				if (dst.getDelegate().isExitBlock()) 
				{
					return BitVectorKillAll.getInstance();
				}
				else
				{
					return BitVectorIdentity.instance();
				}
			}
	
			public UnaryOperator<BitVectorVariable> getNodeTransferFunction(BasicBlockInContext<IExplodedBasicBlock> bb) 
			{
				IExplodedBasicBlock ebb = bb.getDelegate();
				
				SSAInstruction insn = ebb.getInstruction();

				if ((insn instanceof SSAInvokeInstruction) || (insn instanceof SSAReturnInstruction) || (insn instanceof SSAMonitorInstruction))
				{
					// this must be empty -> we do not need to kill anything
					BitVector kill = new BitVector();

					BitVector gen = new BitVector();
					
					try
					{
						Set<SynchEventID> events = SynchEventCodeInfo.getUnblockEventsForInsn(bb.getNode(), bb.getNode().getIR(), insn);
						
						for (SynchEventID ev : events)
						{
							int eventNum = eventsNumbering.getMappedIndex(ev);							
							gen.set(eventNum);
						}							
					}
					catch (Exception ex) { ex.printStackTrace(); }
					
					return new BitVectorKillGen(kill, gen);
				}
				else
				{
					// identity function for all other instructions
					return BitVectorIdentity.instance();
				}
			}
			
			public boolean hasEdgeTransferFunctions() 
			{
				return true;
			}
			
			public boolean hasNodeTransferFunctions() 
			{
				return true;
			}
		}
	
		public BitVectorSolver<BasicBlockInContext<IExplodedBasicBlock>> analyze() 
		{
			BitVectorFramework<BasicBlockInContext<IExplodedBasicBlock>, SynchEventID> framework = new BitVectorFramework<BasicBlockInContext<IExplodedBasicBlock>, SynchEventID>(bwICFG, new TransferFunctionsUE(), eventsNumbering);
			
			BitVectorSolver<BasicBlockInContext<IExplodedBasicBlock>> solver = new BitVectorSolver<BasicBlockInContext<IExplodedBasicBlock>>(framework);

			try
			{
				solver.solve(null);
			}
			catch (Exception ex) { ex.printStackTrace(); }
			
			return solver;
		}
	}

}

