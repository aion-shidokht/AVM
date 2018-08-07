package org.aion.avm.core;

import org.aion.kernel.TransactionContext;
import org.aion.kernel.KernelInterface;
import org.aion.kernel.Transaction;
import org.aion.kernel.TransactionResult;


public class AvmImpl implements Avm {
    private final KernelInterface kernel;

    public AvmImpl(KernelInterface kernel) {
        this.kernel = kernel;
    }

    @Override
    public TransactionResult run(TransactionContext ctx) {

        Transaction tx = ctx.getTransaction();

        // only one result (mutable) shall be created per transaction execution
        TransactionResult result = new TransactionResult();

        // TODO: charge basic transaction cost

        switch (tx.getType()) {
            case CREATE:
                DAppCreator.create(this.kernel, this, tx, ctx, result);
                break;
            case CALL:
                DAppExecutor.call(this.kernel, this, tx, ctx, result);
                break;
            default:
                result.setStatusCode(TransactionResult.Code.INVALID_TX);
                result.setEnergyUsed(tx.getEnergyLimit());
                break;
        }

        return result;
    }
}
