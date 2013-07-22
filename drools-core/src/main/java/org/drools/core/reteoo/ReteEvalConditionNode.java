package org.drools.core.reteoo;

import org.drools.core.base.DroolsQuery;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalRuleBase;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.LeftTupleIterator;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.rule.EvalCondition;
import org.drools.core.spi.PropagationContext;
import org.drools.core.spi.RuleComponent;
import org.drools.core.util.Iterator;
import org.kie.api.definition.rule.Rule;

import java.util.Map.Entry;

public class ReteEvalConditionNode extends EvalConditionNode {

    public ReteEvalConditionNode() {
    }

    public ReteEvalConditionNode(int id, LeftTupleSource tupleSource, EvalCondition eval, BuildContext context) {
        super(id, tupleSource, eval, context);
    }

    public void modifyLeftTuple(InternalFactHandle factHandle,
                                ModifyPreviousTuples modifyPreviousTuples,
                                PropagationContext context,
                                InternalWorkingMemory workingMemory) {
        LeftTupleSourceUtils.doModifyLeftTuple(factHandle, modifyPreviousTuples, context, workingMemory,
                                               (LeftTupleSink) this, getLeftInputOtnId(), getLeftInferredMask());
    }

    public void assertLeftTuple(final LeftTuple leftTuple,
                                final PropagationContext context,
                                final InternalWorkingMemory workingMemory) {
        final EvalMemory memory = (EvalMemory) workingMemory.getNodeMemory( this );

        final boolean allowed = this.condition.isAllowed( leftTuple,
                                                          workingMemory,
                                                          memory.context );

        if ( allowed ) {
            boolean useLeftMemory = true;
            if ( !this.tupleMemoryEnabled ) {
                // This is a hack, to not add closed DroolsQuery objects
                Object object = leftTuple.get( 0 ).getObject();
                if ( !(object instanceof DroolsQuery) || !((DroolsQuery) object).isOpen() ) {
                    useLeftMemory = false;
                }
            }

            this.sink.propagateAssertLeftTuple( leftTuple,
                                                context,
                                                workingMemory,
                                                useLeftMemory );
        }
    }

    public void retractLeftTuple(final LeftTuple leftTuple,
                                 final PropagationContext context,
                                 final InternalWorkingMemory workingMemory) {
        if ( leftTuple.getFirstChild() != null ) {
            this.sink.propagateRetractLeftTuple( leftTuple,
                                                 context,
                                                 workingMemory );
        }
    }

    public void modifyLeftTuple(LeftTuple leftTuple,
                                PropagationContext context,
                                InternalWorkingMemory workingMemory) {
        final EvalMemory memory = (EvalMemory) workingMemory.getNodeMemory( this );
        boolean wasPropagated = leftTuple.getFirstChild() != null;

        final boolean allowed = this.condition.isAllowed( leftTuple,
                                                          workingMemory,
                                                          memory.context );

        if ( allowed ) {
            if ( wasPropagated ) {
                // modify
                this.sink.propagateModifyChildLeftTuple( leftTuple,
                                                         context,
                                                         workingMemory,
                                                         this.tupleMemoryEnabled );
            } else {
                // assert
                this.sink.propagateAssertLeftTuple( leftTuple,
                                                    context,
                                                    workingMemory,
                                                    this.tupleMemoryEnabled );
            }
        } else {
            if ( wasPropagated ) {
                // retract
                this.sink.propagateRetractLeftTuple( leftTuple,
                                                     context,
                                                     workingMemory );
            }
            // else do nothing
        }
    }

    public void attach( BuildContext context ) {
        super.attach(context);

        for ( InternalWorkingMemory workingMemory : context.getWorkingMemories() ) {
            PropagationContextFactory pctxFactory =((InternalRuleBase)workingMemory.getRuleBase()).getConfiguration().getComponentFactory().getPropagationContextFactory();

            final PropagationContext propagationContext = pctxFactory.createPropagationContext(workingMemory.getNextPropagationIdCounter(), PropagationContext.RULE_ADDITION,
                                                                                               null, null, null);
            this.leftInput.updateSink( this,
                                       propagationContext,
                                       workingMemory );
        }
    }

    public void updateSink(final LeftTupleSink sink,
                           final PropagationContext context,
                           final InternalWorkingMemory workingMemory) {
        Iterator<LeftTuple> it = LeftTupleIterator.iterator( workingMemory, this );

        for ( LeftTuple leftTuple =  it.next(); leftTuple != null; leftTuple = it.next() ) {
            LeftTuple childLeftTuple = leftTuple.getFirstChild();
            if ( childLeftTuple != null ) {
                while ( childLeftTuple != null ) {
                    RightTuple rightParent = childLeftTuple.getRightParent();
                    sink.assertLeftTuple( sink.createLeftTuple( leftTuple, sink, context, true ),
                                          context,
                                          workingMemory );

                    while ( childLeftTuple != null && childLeftTuple.getRightParent() == rightParent ) {
                        // skip to the next child that has a different right parent
                        childLeftTuple = childLeftTuple.getLeftParentNext();
                    }
                }
            } else {
                childLeftTuple = sink.createLeftTuple( leftTuple, sink, context, true );
                sink.assertLeftTuple( childLeftTuple,
                                      context,
                                      workingMemory );
            }
        }
    }

    protected void doRemove(final RuleRemovalContext context,
                            final ReteooBuilder builder,
                            final InternalWorkingMemory[] workingMemories) {
        if ( !this.isInUse() ) {
            for( InternalWorkingMemory workingMemory : workingMemories ) {
                workingMemory.clearNodeMemory( this );
            }
            getLeftTupleSource().removeTupleSink( this );
        } else {
            // need to re-wire eval expression to the same one from another rule
            // that is sharing this node
            Entry<Rule, RuleComponent> next = this.getAssociations().entrySet().iterator().next();
            this.condition = (EvalCondition) next.getValue();
        }
    }
}
